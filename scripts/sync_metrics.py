#!/usr/bin/env python3
"""
Sync dataset metrics (views, downloads, likes, dislikes) from PostgreSQL
to the Elasticsearch index.

Flow:
  1. Pull active+published asset IDs from asset_visibility_snapshot (PG).
  2. LEFT JOIN with user_activity_audit_log (views/downloads) and
     user_interactions (likes/dislikes) — single query.
  3. Bulk-update ES: _id == asset_id (UUID), so no ES lookup needed.

Configuration is read from config.ini (path configurable via --config).
"""

import argparse
import configparser
import logging
import sys
from typing import Dict, Any

import psycopg2
import psycopg2.extras
from elasticsearch import Elasticsearch, helpers

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
)
log = logging.getLogger("sync_metrics")


# ---------- Config ----------

def load_config(path: str) -> configparser.ConfigParser:
    cp = configparser.ConfigParser()
    if not cp.read(path):
        raise FileNotFoundError(f"Config file not found: {path}")
    for section in ("postgres", "elasticsearch"):
        if section not in cp:
            raise ValueError(f"Missing [{section}] section in {path}")
    return cp


# ---------- Postgres ----------

def fetch_metrics(cp: configparser.ConfigParser) -> Dict[str, Dict[str, float]]:
    """
    Returns { asset_id: {views, downloads, likes, dislikes} }
    Scoped to assets present in asset_visibility_snapshot (active + published).
    """
    pg = cp["postgres"]
    schema = pg.get("schema", "public")

    snapshot_table   = pg.get("snapshot_table",    "asset_visibility_snapshot")
    audit_table      = pg.get("audit_table",        "user_activity_audit_log")
    inter_table      = pg.get("interactions_table", "user_interactions")
    audit_asset_col  = pg.get("audit_asset_col",    "asset_id")
    audit_action_col = pg.get("audit_action_col",   "action")

    s  = f'"{schema}"'
    sql = f"""
        SELECT
            snap.asset_id::text                                            AS asset_id,
            COALESCE(a.views,     0)                                       AS views,
            COALESCE(a.downloads, 0)                                       AS downloads,
            COALESCE(i.likes,     0)                                       AS likes,
            COALESCE(i.dislikes,  0)                                       AS dislikes
        FROM {s}."{snapshot_table}" snap
        LEFT JOIN (
            SELECT {audit_asset_col}::text                                 AS asset_id,
                   COUNT(*) FILTER (WHERE {audit_action_col} = 'View')     AS views,
                   COUNT(*) FILTER (WHERE {audit_action_col} = 'Download') AS downloads
            FROM   {s}."{audit_table}"
            WHERE  {audit_action_col} IN ('View', 'Download')
              AND  {audit_asset_col} IS NOT NULL
            GROUP  BY {audit_asset_col}
        ) a ON a.asset_id = snap.asset_id::text
        LEFT JOIN (
            SELECT asset_id::text                                          AS asset_id,
                   COUNT(*) FILTER (WHERE is_liked)                        AS likes,
                   COUNT(*) FILTER (WHERE is_disliked)                     AS dislikes
            FROM   {s}."{inter_table}"
            WHERE  asset_id IS NOT NULL
            GROUP  BY asset_id
        ) i ON i.asset_id = snap.asset_id::text;
    """

    conn = psycopg2.connect(
        host=pg.get("host"),
        port=pg.getint("port"),
        dbname=pg.get("database"),
        user=pg.get("user"),
        password=pg.get("password", ""),
    )
    metrics: Dict[str, Dict[str, float]] = {}
    try:
        with conn.cursor(cursor_factory=psycopg2.extras.DictCursor) as cur:
            log.info("Fetching metrics via snapshot JOIN …")
            cur.execute(sql)
            for row in cur.fetchall():
                metrics[row["asset_id"]] = {
                    "views":     float(row["views"]),
                    "downloads": float(row["downloads"]),
                    "likes":     float(row["likes"]),
                    "dislikes":  float(row["dislikes"]),
                }
    finally:
        conn.close()

    log.info("Fetched metrics for %d active assets", len(metrics))
    return metrics


# ---------- Elasticsearch ----------

def build_es_client(cp: configparser.ConfigParser) -> tuple:
    es_cfg = cp["elasticsearch"]
    hosts = [h.strip() for h in es_cfg.get("hosts").split(",")]
    kwargs: Dict[str, Any] = {
        "hosts": hosts,


    }
    username = es_cfg.get("username", fallback=None)
    password = es_cfg.get("password", fallback=None)
    if username and password:
        kwargs["basic_auth"] = (username, password)
    return Elasticsearch(**kwargs), es_cfg.get("index")


def bulk_update_es(
    es: Elasticsearch,
    index: str,
    metrics: Dict[str, Dict[str, float]],
    dry_run: bool = False,
) -> None:
    if not metrics:
        log.warning("No metrics to sync — exiting.")
        return

    # asset_id == ES _id, so no lookup needed
    actions = [
        {
            "_op_type": "update",
            "_index":   index,
            "_id":      asset_id,
            "doc": {
                "metrics": {
                    "views":     m["views"],
                    "downloads": m["downloads"],
                    "likes":     m["likes"],
                    "dislikes":  m["dislikes"],
                }
            },
        }
        for asset_id, m in metrics.items()
    ]

    log.info("Prepared %d ES update actions", len(actions))

    if dry_run:
        for a in actions[:5]:
            log.info("DRY-RUN: %s", a)
        log.info("Dry run — no changes written.")
        return

    success, errors = helpers.bulk(es, actions, raise_on_error=False, stats_only=False)
    log.info("ES bulk update: %d succeeded", success)
    if errors:
        log.error("ES bulk update: %d error(s). First 3: %s", len(errors), errors[:3])


# ---------- Main ----------

def main() -> int:
    parser = argparse.ArgumentParser(description="Sync PG metrics → ES")
    parser.add_argument("--config",  default="config.ini", help="Path to config.ini")
    parser.add_argument("--dry-run", action="store_true",  help="Don't write to ES")
    args = parser.parse_args()

    try:
        cp = load_config(args.config)
    except Exception as e:
        log.error("Config error: %s", e)
        return 1

    try:
        metrics = fetch_metrics(cp)
    except Exception as e:
        log.exception("Failed to fetch metrics from Postgres: %s", e)
        return 1

    try:
        es, index = build_es_client(cp)
        try:
            es.info()
        except Exception as e:
            log.error("Cannot reach Elasticsearch: %s", e)
            return 1
        bulk_update_es(es, index, metrics, dry_run=args.dry_run)
    except Exception as e:
        log.exception("Failed to update Elasticsearch: %s", e)
        return 1

    log.info("Done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
