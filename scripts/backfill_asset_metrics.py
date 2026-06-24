#!/usr/bin/env python3
"""
Backfill script: sync views, downloads, and likes from Postgres into
Elasticsearch asset metrics (metrics.views, metrics.downloads, metrics.likes).

Sources:
  views / downloads  →  aaa.user_activity_audit_log
  likes              →  aaa.user_interactions

dislikes are left untouched in ES.

Config: ../secrets/leaderboard.ini  (same file used by the leaderboard backfill)
"""

import configparser
import json
import logging
import os
from collections import defaultdict

import psycopg2
import requests
from requests.auth import HTTPBasicAuth

# -------------------------------------------------
# CONFIG
# -------------------------------------------------

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(BASE_DIR, "../secrets/leaderboard.ini")

config = configparser.RawConfigParser()
if not config.read(CONFIG_PATH):
    raise RuntimeError(f"Failed to load config: {CONFIG_PATH}")

ES_URL        = config.get("elastic", "url")
ES_INDEX      = config.get("elastic", "index")
ES_USER       = config.get("elastic", "user")
ES_PASSWORD   = config.get("elastic", "password")
BATCH_SIZE    = config.getint("elastic", "batch_size")

PG_HOST       = config.get("postgres", "host")
PG_PORT       = config.getint("postgres", "port")
PG_DB         = config.get("postgres", "db")
PG_USER       = config.get("postgres", "user")
PG_PASSWORD   = config.get("postgres", "password")
PG_SCHEMA     = config.get("postgres", "schema")

# -------------------------------------------------
# LOGGING
# -------------------------------------------------

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
LOGGER = logging.getLogger("asset-metrics-backfill")

# -------------------------------------------------
# POSTGRES
# -------------------------------------------------

def get_pg_conn():
    return psycopg2.connect(
        host=PG_HOST,
        port=PG_PORT,
        dbname=PG_DB,
        user=PG_USER,
        password=PG_PASSWORD,
    )


def fetch_view_download_counts(conn):
    sql = f"""
        SELECT
            asset_id::text,
            COUNT(*) FILTER (WHERE UPPER(action) = 'DOWNLOAD') AS downloads,
            COUNT(*) FILTER (WHERE UPPER(action) = 'VIEW')     AS views
        FROM {PG_SCHEMA}.user_activity_audit_log
        WHERE asset_id IS NOT NULL
        GROUP BY asset_id
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()

    result = {}
    for asset_id, downloads, views in rows:
        result[asset_id] = {"downloads": int(downloads), "views": int(views)}

    LOGGER.info("Fetched view/download counts for %d assets", len(result))
    return result


def fetch_like_counts(conn):
    sql = f"""
        SELECT
            asset_id::text,
            COUNT(*) FILTER (WHERE is_liked = TRUE) AS likes
        FROM {PG_SCHEMA}.user_interactions
        WHERE asset_id IS NOT NULL
        GROUP BY asset_id
    """
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()

    result = {}
    for asset_id, likes in rows:
        result[asset_id] = {"likes": int(likes)}

    LOGGER.info("Fetched like counts for %d assets", len(result))
    return result

# -------------------------------------------------
# MERGE
# -------------------------------------------------

def merge_metrics(view_download, likes):
    merged = defaultdict(lambda: {"views": 0, "downloads": 0, "likes": 0})

    for asset_id, counts in view_download.items():
        merged[asset_id]["views"]     = counts["views"]
        merged[asset_id]["downloads"] = counts["downloads"]

    for asset_id, counts in likes.items():
        merged[asset_id]["likes"] = counts["likes"]

    LOGGER.info("Total unique assets with any metric: %d", len(merged))
    return merged

# -------------------------------------------------
# ELASTICSEARCH BULK UPDATE
# -------------------------------------------------

# Preserves dislikes and bookmarks already in ES; overwrites the other three.
PAINLESS_SCRIPT = (
    "if (ctx._source.metrics == null) {"
    "  ctx._source.metrics = ['views': 0, 'downloads': 0, 'likes': 0, 'dislikes': 0];"
    "}"
    "ctx._source.metrics.views     = params.views;"
    "ctx._source.metrics.downloads = params.downloads;"
    "ctx._source.metrics.likes     = params.likes;"
)


def build_bulk_body(batch):
    lines = []
    for asset_id, metrics in batch:
        action_line = json.dumps({"update": {"_index": ES_INDEX, "_id": asset_id}})
        doc_line    = json.dumps({
            "script": {
                "lang":   "painless",
                "source": PAINLESS_SCRIPT,
                "params": {
                    "views":     metrics["views"],
                    "downloads": metrics["downloads"],
                    "likes":     metrics["likes"],
                },
            }
        })
        lines.append(action_line)
        lines.append(doc_line)
    return "\n".join(lines) + "\n"


def send_bulk(body):
    resp = requests.post(
        f"{ES_URL}/_bulk",
        data=body,
        headers={"Content-Type": "application/x-ndjson"},
        auth=HTTPBasicAuth(ES_USER, ES_PASSWORD),
        timeout=60,
    )
    resp.raise_for_status()
    return resp.json()


def parse_bulk_response(response):
    succeeded = 0
    failed    = 0
    for item in response.get("items", []):
        result = item.get("update", {})
        status = result.get("status", 0)
        if status in (200, 201) or result.get("result") in ("updated", "noop"):
            succeeded += 1
        else:
            failed += 1
            LOGGER.warning(
                "ES update failed  id=%-40s  error=%s",
                result.get("_id", "?"),
                result.get("error", {}).get("reason", result.get("error")),
            )
    return succeeded, failed


def bulk_update_es(metrics):
    items  = list(metrics.items())
    total  = len(items)
    total_succeeded = 0
    total_failed    = 0

    for batch_no, offset in enumerate(range(0, total, BATCH_SIZE), start=1):
        batch    = items[offset : offset + BATCH_SIZE]
        body     = build_bulk_body(batch)
        response = send_bulk(body)

        if response.get("errors"):
            succeeded, failed = parse_bulk_response(response)
        else:
            succeeded, failed = len(batch), 0

        total_succeeded += succeeded
        total_failed    += failed

        LOGGER.info(
            "Batch %-4d | size=%-4d | succeeded=%-4d | failed=%-4d | progress=%d/%d",
            batch_no, len(batch), succeeded, failed,
            min(offset + BATCH_SIZE, total), total,
        )

    return total_succeeded, total_failed

# -------------------------------------------------
# MAIN
# -------------------------------------------------

def main():
    LOGGER.info("=== Asset Metrics Backfill STARTED ===")
    LOGGER.info("ES index  : %s", ES_INDEX)
    LOGGER.info("PG schema : %s", PG_SCHEMA)
    LOGGER.info("Batch size: %d", BATCH_SIZE)

    conn = get_pg_conn()
    try:
        view_download = fetch_view_download_counts(conn)
        likes         = fetch_like_counts(conn)
    finally:
        conn.close()

    metrics = merge_metrics(view_download, likes)

    if not metrics:
        LOGGER.info("No metrics found — nothing to sync.")
        return

    succeeded, failed = bulk_update_es(metrics)

    LOGGER.info("=== Asset Metrics Backfill COMPLETE ===")
    LOGGER.info("Total assets processed  : %d", len(metrics))
    LOGGER.info("ES updates succeeded    : %d", succeeded)
    LOGGER.info("ES updates failed       : %d", failed)

    if failed:
        LOGGER.warning("%d assets failed to update — check logs above for details.", failed)


if __name__ == "__main__":
    main()