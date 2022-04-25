CREATE USER moonlight_dev_user WITH PASSWORD 'dev';
DROP DATABASE IF EXISTS moonlight_dev;
CREATE DATABASE moonlight_dev;
GRANT ALL PRIVILEGES ON DATABASE moonlight_dev to moonlight_dev_user;
ALTER ROLE moonlight_dev_user superuser;
