-- Item types are "<prefix>:<name>" (adex:DataBank, iudx:DataBank, tgdex:DataBank, ...)
-- and the prefix is set per deployment via commonOptions.itemTypePrefix, so stop
-- restricting the columns that store them to a fixed enum of prefixed values.
ALTER TABLE request
    ALTER COLUMN asset_type TYPE VARCHAR USING asset_type::TEXT;

DROP TYPE IF EXISTS _asset_type;

ALTER TABLE app_constraints
    ALTER COLUMN entity_type TYPE VARCHAR USING entity_type::TEXT;

DROP TYPE IF EXISTS entity_type_enum;

ALTER TABLE delegation_scope_constraints
    ALTER COLUMN entity_type TYPE VARCHAR USING entity_type::TEXT;

DROP TYPE IF EXISTS delegation_entity_type;
