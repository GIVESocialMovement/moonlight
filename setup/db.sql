CREATE USER moonlight_test_user WITH PASSWORD 'test';
DROP DATABASE IF EXISTS moonlight_test;
CREATE DATABASE moonlight_test;
GRANT ALL PRIVILEGES ON DATABASE moonlight_test to moonlight_test_user;
ALTER ROLE moonlight_test_user superuser;
