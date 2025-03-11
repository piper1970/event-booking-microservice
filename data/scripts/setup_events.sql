-- create database
DROP DATABASE IF EXISTS events;
CREATE DATABASE events
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- create admin role events_admin_user
DROP ROLE IF EXISTS events_admin_user;
CREATE ROLE events_admin_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'events_admin_password';
-- make sure .env file matches

-- grant permissions for events_admin_user
GRANT ALL ON DATABASE events TO events_admin_user;

-- move to events database
\c events

-- setup schema for events db
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to events_admin_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to events_admin_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to events_admin_user;

-- create regular user role events_user
DROP ROLE IF EXISTS events_user;
CREATE ROLE events_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'events_password';
-- make sure .env file matches

-- grant permissions for events_user
GRANT CONNECT ON DATABASE events TO events_user;
GRANT USAGE ON SCHEMA event_service to events_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA event_service to events_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA event_service to events_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE events_admin_user
    GRANT SELECT, UPDATE, INSERT, DELETE ON TABLES TO events_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE events_admin_user
    GRANT USAGE, SELECT ON SEQUENCES TO events_user;


