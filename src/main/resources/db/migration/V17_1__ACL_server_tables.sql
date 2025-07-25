-- we create the following extension to use gen_random_uuid
-- it is created on the default public schema so that all
-- schemas in the database may use it (if required).
create EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

alter SCHEMA ${flyway:defaultSchema} OWNER TO ${flyway:user};

-- request status type
create type _access_request_status_type as ENUM
(
   'GRANTED',
   'PENDING',
   'REJECTED'
);

-- request type
create type _request_type as ENUM
(
   'DOWNLOAD'
);

-- asset type
-- Update asset_type enum to include new values according to
-- catalogue server response and rename existing ones
create type _asset_type as ENUM
(
   'adex:DataBank',
   'adex:AiModel'
);

-- asset permission
--create type _asset_permission as ENUM
--(
--   'RESTRICTED'
--);

-- TODO: add first name, last name and email ID as it is shown in the UI.
---
-- Request table
---
CREATE TABLE IF NOT EXISTS request
(
   request_id uuid DEFAULT uuid_generate_v4 () NOT NULL,
   status _access_request_status_type NOT NULL default 'PENDING',
   request_type _request_type NOT NULL,
   additional_info JSONB NOT NULL DEFAULT '{}',
   provider_id uuid NOT NULL,
   consumer_id uuid NOT NULL,
   consumer_first_name varchar NOT NULL,
   consumer_last_name varchar NOT NULL,
   consumer_email_id varchar NOT NULL,
   consumer_organization varchar,
   consumer_organization_id uuid,
   item_organization_id uuid,
   item_id uuid NOT NULL,
   asset_name varchar NOT NULL,
   asset_type _asset_type NOT NULL,
   short_description varchar(2000),
   expiry_at timestamp without time zone,
   created_at timestamp without time zone NOT NULL,
   updated_at timestamp without time zone NOT NULL,
   CONSTRAINT request_id_pk PRIMARY KEY (request_id)
);

ALTER TABLE request OWNER TO ${flyway:user};


---
-- Functions for audit[created,updated] on table/column
---

-- updated_at column function
create
or replace
   function update_modified () RETURNS trigger AS $$
begin NEW.updated_at = now ();
RETURN NEW;
END;
$$ language 'plpgsql';

-- created_at column function
create
or replace
   function update_created () RETURNS trigger AS $$
begin NEW.created_at = now ();
RETURN NEW;
END;
$$ language 'plpgsql';

---
-- Triggers
---

-- request table
create trigger update_ua_created before insert on request for each row EXECUTE procedure update_created ();
create trigger update_ua_modified before insert or update on request for each row EXECUTE procedure update_modified ();


 ---
 -- grants
 ---

 GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${authUser};

 GRANT SELECT,INSERT,UPDATE,DELETE ON TABLE request TO ${authUser};
