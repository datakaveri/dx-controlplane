SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

-- we create the pgcrypto extension to use gen_random_uuid
-- it is created on the default public schema so that all
-- schemas in the database may use it (if required).
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


CREATE TABLE IF NOT EXISTS organizations (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    name VARCHAR NOT NULL UNIQUE,
    logo_path VARCHAR,
    entity_type VARCHAR NOT NULL,
    org_sector VARCHAR NOT NULL,
    website_link VARCHAR NOT NULL,
    address VARCHAR NOT NULL,
    certificate_path VARCHAR NOT NULL,
    pancard_path VARCHAR NOT NULL,
    relevant_doc_path VARCHAR,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS organization_join_requests (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL UNIQUE,
    user_name VARCHAR NOT NULL,
    job_title VARCHAR NOT NULL,
    emp_id VARCHAR NOT NULL,
    status VARCHAR NOT NULL CHECK (status IN ('pending', 'granted', 'rejected')),
    requested_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS organization_users (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    user_id UUID NOT NULL UNIQUE,
    user_name VARCHAR NOT NULL,
    job_title VARCHAR NOT NULL,
    emp_id VARCHAR NOT NULL,
    phone_no VARCHAR,
    role VARCHAR NOT NULL CHECK (role IN ('admin','member')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS organization_create_requests (
    id UUID DEFAULT public.gen_random_uuid() PRIMARY KEY,
    requested_by UUID NOT NULL,
    name VARCHAR NOT NULL UNIQUE,
    logo_path VARCHAR,
    entity_type VARCHAR NOT NULL,
    org_sector VARCHAR NOT NULL,
    website_link VARCHAR NOT NULL,
    address VARCHAR NOT NULL,
    certificate_path VARCHAR NOT NULL,
    pancard_path VARCHAR NOT NULL,
    relevant_doc_path VARCHAR,
    user_name VARCHAR NOT NULL,
    emp_id VARCHAR NOT NULL,
    job_title VARCHAR NOT NULL,
    phone_no VARCHAR NOT NULL,
    status VARCHAR NOT NULL CHECK (status IN ('pending', 'granted', 'rejected')),
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

