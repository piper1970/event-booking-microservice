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
DROP ROLE IF EXISTS events_user_test;
CREATE ROLE events_user_test WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'events_password_test';

-- grant permissions for events_admin_user
GRANT ALL ON DATABASE events TO events_user_test;

-- move to events database
\c events

-- setup schema for events db
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to events_user_test;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to events_user_test;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to events_user_test;