# Add priority

# --- !Ups

ALTER TABLE background_jobs ADD COLUMN priority int NOT NULL default '0';

# --- !Downs

ALTER TABLE background_jobs DROP COLUMN priority;
