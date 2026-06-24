#!/usr/bin/env python3
"""
Verification script: find asset IDs that exist in aaa.user_interactions
but have no corresponding document in Elasticsearch.

Output:
  - Console summary
  - missing_from_elastic.json  (list of orphaned asset IDs with their interaction counts)

Config: ../secrets/leaderboard.ini
"""

import configparser
import json
import logging
import os

import psycopg2
import requests
from requests.auth import HTTPBasicAuth

# -------------------------------------------------
# CONFIG
# -------------------------------------------------

BASE_DIR    = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "../secrets/leaderboard.ini")

config = configparser.RawConfigParser()
if not config.read(CONFIG_PATH):
    raise RuntimeError(f"Failed to load config: {CONFIG_PATH}")

ES_URL      = config.get("elastic", "url")
ES_INDEX    = config.get("elastic", "index")
ES_USER     = config.get("elastic", "user")
ES_PASSWORD = config.get("elastic", "password")
BATCH_SIZE  = config.getint("elastic", "batch_size")

PG_HOST     = config.get("postgres", "host")
PG_PORT     = config.getint("postgres", "port")
PG_DB       = config.get("postgres", "db")
PG_USER     = config.get("postgres", "user")
PG_PASSWORD = config.get("postgres", "password")
PG_SCHEMA   = config.get("postgres", "schema")

OUTPUT_FILE = os.path.join(BASE_DIR, "missing_from_elastic.json")

# -------------------------------------------------
# LOGGING
# -------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
LOGGER = logging.getLogger("verify-interactions-vs-elastic")

# -------------------------------------------------
# POSTGRES: fetch all distinct asset_ids + interaction counts
# -------------------------------------------------

def fetch_interactions_from_pg(conn):
    sql = f"""
        SELECT DISTINCT asset_id::text
        FROM {PG_SCHEMA}.user_interactions
        WHERE asset_id IS NOT NULL
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()

    result = {row[0] for row in rows}
    LOGGER.info("Distinct asset IDs in user_interactions: %d", len(result))
    return result

# -------------------------------------------------
# ELASTICSEARCH: terms query on id.keyword (source field, not _id)
# -------------------------------------------------

def check_ids_in_elastic(asset_ids):
    """
    Returns a set of IDs that were NOT found in ES.

    Uses a terms query on id.keyword (the _source field) rather than _mget
    on _id, because some documents may have been indexed with an auto-generated
    or IRI-based _id that doesn't match the UUID stored in user_interactions.
    """
    found     = set()
    ids_list  = list(asset_ids)
    total     = len(ids_list)

    for offset in range(0, total, BATCH_SIZE):
        batch = ids_list[offset : offset + BATCH_SIZE]

        body = {
            "_source": ["id"],
            "size": len(batch),
            "query": {
                "terms": {
                    "id.keyword": batch
                }
            }
        }

        resp = requests.post(
            f"{ES_URL}/{ES_INDEX}/_search",
            json=body,
            auth=HTTPBasicAuth(ES_USER, ES_PASSWORD),
            timeout=60,
        )
        resp.raise_for_status()

        hits = resp.json().get("hits", {}).get("hits", [])
        for hit in hits:
            found.add(hit["_source"]["id"])

        LOGGER.info(
            "Checked %d/%d IDs | found so far: %d",
            min(offset + BATCH_SIZE, total), total, len(found),
        )

    not_found = asset_ids - found
    return not_found

# -------------------------------------------------
# MAIN
# -------------------------------------------------

def main():
    LOGGER.info("=== Verify user_interactions vs Elasticsearch STARTED ===")
    LOGGER.info("ES index  : %s", ES_INDEX)
    LOGGER.info("PG schema : %s", PG_SCHEMA)

    conn = psycopg2.connect(
        host=PG_HOST, port=PG_PORT, dbname=PG_DB,
        user=PG_USER, password=PG_PASSWORD,
    )
    try:
        pg_assets = fetch_interactions_from_pg(conn)
    finally:
        conn.close()

    if not pg_assets:
        LOGGER.info("user_interactions table is empty — nothing to verify.")
        return

    missing_ids = check_ids_in_elastic(pg_assets)

    orphans = sorted(missing_ids)

    with open(OUTPUT_FILE, "w") as f:
        json.dump(orphans, f, indent=2)

    LOGGER.info("=== Results ===")
    LOGGER.info("Total asset IDs in user_interactions : %d", len(pg_assets))
    LOGGER.info("Found in Elasticsearch               : %d", len(pg_assets) - len(missing_ids))
    LOGGER.info("Missing from Elasticsearch           : %d", len(missing_ids))
    LOGGER.info("Details written to                   : %s", OUTPUT_FILE)

    if orphans:
        LOGGER.warning("Orphaned asset IDs (in PG, not in ES):")
        for asset_id in orphans:
            LOGGER.warning("  %s", asset_id)


if __name__ == "__main__":
    main()