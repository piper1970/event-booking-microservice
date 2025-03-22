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
DROP ROLE IF EXISTS test_events_user;
CREATE ROLE test_events_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'test_events_password';

-- grant permissions for events_admin_user
GRANT ALL ON DATABASE events TO test_events_user;

-- move to events database
\c events

-- setup schema for events db
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to test_events_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to test_events_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to test_events_user;