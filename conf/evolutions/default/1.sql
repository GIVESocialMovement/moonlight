# Create background_jobs

# --- !Ups

CREATE TABLE background_jobs (
    id bigserial NOT NULL,
    created_at bigint NOT NULL,
    started_at_opt bigint,
    finished_at_opt bigint,
    status character varying(255),
    error text NOT NULL,
    try_count integer NOT NULL,
    job_type character varying(255) NOT NULL,
    params_in_json_string text NOT NULL,
    should_run_at bigint NOT NULL
);

# --- !Downs

DROP TABLE background_jobs CASCADE;
