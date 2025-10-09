ALTER TABLE policy ADD COLUMN additional_info JSONB;
ALTER TABLE policy ADD COLUMN provider_comment varchar(4000);
ALTER TABLE policy ADD COLUMN feedback_to_consumer varchar(4000);