# Add initiated_at_opt

# --- !Ups

ALTER TABLE background_jobs ADD COLUMN initiated_at_opt bigint;

# --- !Downs

ALTER TABLE background_jobs DROP COLUMN initiated_at_opt;
