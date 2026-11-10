#!/bin/bash
set -e

# Create the 'aaa' schema and set search_path for the application
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE SCHEMA IF NOT EXISTS aaa;
    ALTER DATABASE $POSTGRES_DB SET search_path TO aaa, public;
EOSQL
