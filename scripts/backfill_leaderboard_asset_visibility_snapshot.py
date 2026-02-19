#!/usr/bin/env python3

# Utility script for backfill, rebuild, and recovery of leaderboard asset
# visibility and engagement metrics using Elasticsearch and PostgreSQL.


import logging
import requests
import psycopg2
import json
import os
import configparser
from psycopg2.extras import execute_batch
from requests.auth import HTTPBasicAuth
from datetime import datetime
from collections import defaultdict

# -------------------------------------------------
# CONFIG LOADING (ROBUST)
# -------------------------------------------------

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "../secrets/leaderboard.ini")

config = configparser.RawConfigParser()

if not config.read(CONFIG_PATH):
    raise RuntimeError(f"Failed to load config file: {CONFIG_PATH}")

print("Using config file:", CONFIG_PATH)
print("Loaded sections:", config.sections())

#init local variables
ES_URL = config.get("elastic", "url")
ES_INDEX = config.get("elastic", "index")
ES_USER = config.get("elastic", "user")
ES_PASSWORD = config.get("elastic", "password")
SCROLL_TIME = config.get("elastic", "scroll_time")
BATCH_SIZE = config.getint("elastic", "batch_size")


PG_HOST = config.get("postgres", "host")
PG_PORT = config.getint("postgres", "port")
PG_DB = config.get("postgres", "db")
PG_USER = config.get("postgres", "user")
PG_PASSWORD = config.get("postgres", "password")
PG_SCHEMA = config.get("postgres", "schema")


SKIPPED_JSON_FILE = config.get("output", "skipped_json_file")
LEADERBOARD_SQL_FILE = config.get("sql", "leaderboard_rebuild_file")


# =================================================
# SKIP REASONS
# =================================================

class SkipReason:
    INVALID_ASSET_TYPE = "INVALID_ASSET_TYPE"
    DATA_UPLOAD_STATUS_FALSE = "DATA_UPLOAD_STATUS_FALSE"
    PUBLISH_STATUS_NOT_ACTIVE = "PUBLISH_STATUS_NOT_ACTIVE"
    MISSING_ACCESS_POLICY = "MISSING_ACCESS_POLICY"


SKIP_COUNTS = defaultdict(int)
SKIPPED_ASSETS = []

# =================================================
# LOGGING
# =================================================

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
LOGGER = logging.getLogger("asset-visibility-backfill")

# =================================================
# HELPERS
# =================================================

def normalize_asset_type(type_field):
    if not type_field:
        return None

    raw = type_field[0] if isinstance(type_field, list) else type_field

    return {
        "adex:DataBank": "DATABANK",
        "adex:AiModel": "AI_MODEL",
        "adex:Apps": "USECASE",
    }.get(raw)


def record_skip(doc, reason, value):
    SKIPPED_ASSETS.append({
        "reason": reason,
        "value": value,
        "skippedAt": datetime.utcnow().isoformat() + "Z",
        "asset": doc   # 👈 FULL Elastic _source
    })
    SKIP_COUNTS[reason] += 1


def map_asset(doc):
    raw_type = doc.get("type")

    asset_type = normalize_asset_type(raw_type)
    if not asset_type:
        record_skip(doc, SkipReason.INVALID_ASSET_TYPE, raw_type)
        return None

    if asset_type!= "USECASE" and doc.get("dataUploadStatus") is not True:
        record_skip(doc, SkipReason.DATA_UPLOAD_STATUS_FALSE, doc.get("dataUploadStatus"))
        return None

    if doc.get("publishStatus") != "ACTIVE":
        record_skip(doc, SkipReason.PUBLISH_STATUS_NOT_ACTIVE, doc.get("publishStatus"))
        return None

    access_policy = doc.get("accessPolicy")
    if not access_policy:
        record_skip(doc, SkipReason.MISSING_ACCESS_POLICY, None)
        return None

    return (
        doc.get("id"),
        doc.get("name"),
        asset_type,
        access_policy,
        doc.get("ownerUserId"),
        doc.get("organizationId"),
        doc.get("organization"),
        doc.get("organizationType"),
    )


def get_pg_conn():
    return psycopg2.connect(
        host=PG_HOST,
        port=PG_PORT,
        dbname=PG_DB,
        user=PG_USER,
        password=PG_PASSWORD,
    )


def with_schema(sql: str) -> str:
    return sql.format(schema=PG_SCHEMA)

# =================================================
# DB
# =================================================

INSERT_SNAPSHOT_SQL = """
INSERT INTO {schema}.asset_visibility_snapshot (
    asset_id,
    asset_name,
    asset_type,
    access_policy,
    provider_id,
    organization_id,
    organization_name,
    organization_type,
    snapshot_at
)
VALUES (%s, %s, %s, %s, %s, %s, %s, %s, now())
ON CONFLICT (asset_id)
DO UPDATE SET
    asset_name        = EXCLUDED.asset_name,
    asset_type        = EXCLUDED.asset_type,
    access_policy     = EXCLUDED.access_policy,
    provider_id       = EXCLUDED.provider_id,
    organization_id   = EXCLUDED.organization_id,
    organization_name = EXCLUDED.organization_name,
    organization_type = EXCLUDED.organization_type,
    snapshot_at       = now();
"""

def clear_snapshot(conn):
    with conn.cursor() as cur:
        cur.execute(f"TRUNCATE TABLE {PG_SCHEMA}.asset_visibility_snapshot")
    conn.commit()


def insert_snapshot_rows(conn, rows):
    if not rows:
        return 0

    sql = with_schema(INSERT_SNAPSHOT_SQL)

    # 🔍 Print SQL query
    #print("Postgres SQL:")
    #print(sql)

    # 🔍 Optional: print how many rows + one sample
    #print(f"Rows to insert: {len(rows)}")
    #print("Sample row:", rows[0])

    with conn.cursor() as cur:
        execute_batch(cur, sql, rows)

    conn.commit()
    return len(rows)


# =================================================
# ELASTIC
# =================================================

def scroll_elastic():
    LOGGER.info("Starting initial Elastic search")

    url = f"{ES_URL}/{ES_INDEX}/_search?scroll={SCROLL_TIME}"

    query = {
        "size": BATCH_SIZE,
        "_source": [
            "id",
            "name",
            "type",
            "accessPolicy",
            "ownerUserId",
            "organizationId",
            "organization",
            "organizationType",
            "dataUploadStatus",
            "publishStatus"
        ],
        "query": {
            "bool": {
              "must_not": [
                {
                  "match": {
                    "accessPolicy": {
                      "query": "PRIVATE"
                    }
                  }
                },
                {
                  "bool": {
                    "must": [
                      { "term": { "type.keyword": { "value": "adex:DataBank" } } },
                      { "term": { "dataUploadStatus": { "value": "false" } } }
                    ]
                  }
                },
                {
                  "bool": {
                    "must": [
                      { "term": { "type.keyword": { "value": "adex:AiModel" } } },
                      { "term": { "dataUploadStatus": { "value": "false" } } }
                    ]
                  }
                },
                {
                  "bool": {
                    "must": [
                      {
                        "terms": {
                          "type.keyword": [
                            "adex:DataBank",
                            "adex:AiModel",
                            "adex:Apps"
                          ]
                        }
                      },
                      {
                        "term": {
                          "publishStatus.keyword": {
                            "value": "PENDING"
                          }
                        }
                      }
                    ]
                  }
                }
              ]
            }
          }
    }

    res = requests.post(
        url,
        json=query,
        auth=HTTPBasicAuth(ES_USER, ES_PASSWORD),
        timeout=60,
    )
    res.raise_for_status()
    return res.json()


def continue_scroll(scroll_id):
    url = f"{ES_URL}/_search/scroll"
    res = requests.post(
        url,
        json={"scroll": SCROLL_TIME, "scroll_id": scroll_id},
        auth=HTTPBasicAuth(ES_USER, ES_PASSWORD),
        timeout=60,
    )
    res.raise_for_status()
    return res.json()

def run_sql_file(conn, file_path):
    LOGGER.info("Running SQL file: %s", file_path)

    # 🔑 Finish previous transaction first
    conn.commit()

    # Now safe to change autocommit
    conn.autocommit = True

    with conn.cursor() as cur:
        cur.execute(f"SET search_path TO {PG_SCHEMA}")

        with open(file_path, "r") as f:
            sql = f.read()

        cur.execute(sql)

    LOGGER.info("SQL file executed successfully: %s", file_path)


# =================================================
# MAIN
# =================================================

def main():
    LOGGER.info("=== Asset Visibility Snapshot Backfill STARTED ===")
    LOGGER.info("Skipped JSON will be written to %s", SKIPPED_JSON_FILE)

    SKIP_COUNTS.clear()
    SKIPPED_ASSETS.clear()

    conn = get_pg_conn()
    LOGGER.info("Clearing asset_visibility_snapshot before backfill")
    clear_snapshot(conn)


    response = scroll_elastic()
    scroll_id = response["_scroll_id"]

    total_scanned = 0
    total_inserted = 0
    skipped = 0
    batch_no = 0

    while True:
        hits = response["hits"]["hits"]
        if not hits:
            break

        batch_no += 1
        batch_rows = []

        LOGGER.info("Batch %d | Elastic hits fetched = %d", batch_no, len(hits))

        for hit in hits:
            total_scanned += 1
            row = map_asset(hit["_source"])
            if row:
                batch_rows.append(row)
            else:
                skipped += 1

        inserted = insert_snapshot_rows(conn, batch_rows)
        total_inserted += inserted

        LOGGER.info(
            "Batch %d | Inserted=%d | Skipped=%d | TotalInserted=%d",
            batch_no,
            inserted,
            skipped,
            total_inserted,
        )

        response = continue_scroll(scroll_id)
        scroll_id = response["_scroll_id"]

    #conn.close()

     # -----------------------------
        # PHASE 2: Leaderboard rebuild
        # -----------------------------
        LOGGER.info("=== Leaderboard Rebuild STARTED ===")
        run_sql_file(conn, LEADERBOARD_SQL_FILE)
        LOGGER.info("=== Leaderboard Rebuild COMPLETE ===")

        conn.close()


    # 🔹 Write skipped assets JSON
    with open(SKIPPED_JSON_FILE, "w") as f:
        json.dump(SKIPPED_ASSETS, f, indent=2)

    LOGGER.info("=== Skip Summary ===")
    for reason, count in SKIP_COUNTS.items():
        LOGGER.info("Skipped %-35s : %d", reason, count)

    LOGGER.info("=== Backfill COMPLETE ===")
    LOGGER.info("Total Elastic docs scanned : %d", total_scanned)
    LOGGER.info("Total rows written         : %d", total_inserted)
    LOGGER.info("Total skipped              : %d", skipped)
    LOGGER.info("Skipped asset details JSON : %s", SKIPPED_JSON_FILE)


if __name__ == "__main__":
    main()
