-- we create the following extension to use gen_random_uuid
-- it is created on the default public schema so that all
-- schemas in the database may use it (if required).
create EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

alter SCHEMA ${flyway:defaultSchema} OWNER TO ${flyway:user};

-- constants enum
-- item types
create type _item_type as ENUM
(
   'DATABANK',
   'AIMODEL'
);

-- status type
create type status_type as ENUM
(
   'ACTIVE',
   'DELETED'
);

---
-- User Table
---
CREATE TABLE IF NOT EXISTS user_table
(
   _id uuid NOT NULL,
   email_id varchar NOT NULL,
   first_name varchar NOT NULL,
   last_name varchar NOT NULL,
   created_at timestamp without time zone NOT NULL,
   updated_at timestamp without time zone NOT NULL,
   CONSTRAINT user_pk PRIMARY KEY (_id)
);

ALTER TABLE user_table OWNER TO ${flyway:user};

---item name like surat
---
-- Resource Entity Table
---
CREATE TABLE IF NOT EXISTS resource_entity
(
   _id uuid NOT NULL,
   provider_id uuid NOT NULL,
   item_type _item_type NOT NULL,
   resource_server_urls text[] NOT NULL,
   created_at timestamp without time zone NOT NULL,
   updated_at timestamp without time zone NOT NULL,
   CONSTRAINT resource_pk PRIMARY KEY (_id),
   CONSTRAINT provider_id_fk FOREIGN KEY(provider_id) REFERENCES user_table(_id)
);

ALTER TABLE resource_entity OWNER TO ${flyway:user};

---
-- Policy Table
---
CREATE TABLE IF NOT EXISTS policy
(
   _id uuid DEFAULT uuid_generate_v4 () NOT NULL,
   user_emailid varchar NOT NULL,
   item_id uuid NOT NULL,
   owner_id uuid NOT NULL,
   status status_type NOT NULL,
   expiry_at timestamp without time zone NOT NULL,
   created_at timestamp without time zone NOT NULL,
   updated_at timestamp without time zone NOT NULL,
   constraints json NOT NULL,
   CONSTRAINT policy_pk PRIMARY KEY (_id),
   CONSTRAINT owner_id_fk FOREIGN KEY(owner_id) REFERENCES user_table(_id),
   CONSTRAINT item_id_fk FOREIGN KEY(item_id) REFERENCES resource_entity(_id)
);

ALTER TABLE policy OWNER TO ${flyway:user};

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

-- resource table
create trigger update_ua_created before insert on resource_entity for each row EXECUTE procedure update_created ();
create trigger update_ua_modified before insert or update on resource_entity for each row EXECUTE procedure update_modified ();

-- policy table
create trigger update_ua_created before insert on policy for each row EXECUTE procedure update_created ();
create trigger update_ua_modified before insert or update on policy for each row EXECUTE procedure update_modified ();

-- user_table
create trigger update_ua_created before insert on user_table for each row EXECUTE procedure update_created ();
create trigger update_ua_modified before insert or update on user_table for each row EXECUTE procedure update_modified ();

 ---
 -- grants
 ---

 GRANT USAGE ON SCHEMA ${flyway:defaultSchema} TO ${aclApdUser};

 GRANT SELECT,INSERT ON TABLE resource_entity TO ${aclApdUser};
 GRANT SELECT,INSERT,UPDATE ON TABLE policy TO ${aclApdUser};
 GRANT SELECT,INSERT ON TABLE user_table TO ${aclApdUser};