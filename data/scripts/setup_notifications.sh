#!/bin/bash

# Relies on NOTIFICATIONS_ADMIN_DB_USER, NOTIFICATIONS_ADMIN_DB_PASSWORD,
# NOTIFICATIONS_DB_USER, and NOTIFICATIONS_DB_PASSWORD environment variables

psql <<-END
DROP DATABASE IF EXISTS notifications;
CREATE DATABASE notifications
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- Create admin role
DROP ROLE IF EXISTS $NOTIFICATIONS_ADMIN_DB_USER;
CREATE ROLE $NOTIFICATIONS_ADMIN_DB_USER WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD '$NOTIFICATIONS_ADMIN_DB_PASSWORD';

-- Grant permissions for admin user
GRANT ALL ON DATABASE notifications TO $NOTIFICATIONS_ADMIN_DB_USER;

-- move to notifications db
\c notifications

-- setup schema
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to $NOTIFICATIONS_ADMIN_DB_USER;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to $NOTIFICATIONS_ADMIN_DB_USER;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to $NOTIFICATIONS_ADMIN_DB_USER;

-- Create regular user
DROP ROLE IF EXISTS $NOTIFICATIONS_DB_USER;
CREATE ROLE $NOTIFICATIONS_DB_USER WITH
    LOGIN
    NOSUPERUSER
    INHERIT
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD '$NOTIFICATIONS_DB_PASSWORD';

-- Grant permissions for user
GRANT CONNECT ON DATABASE notifications TO $NOTIFICATIONS_DB_USER;
GRANT USAGE ON SCHEMA event_service to $NOTIFICATIONS_DB_USER;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA event_service to $NOTIFICATIONS_DB_USER;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA event_service to $NOTIFICATIONS_DB_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE $NOTIFICATIONS_ADMIN_DB_USER
    GRANT SELECT, UPDATE, DELETE, INSERT ON TABLES TO $NOTIFICATIONS_DB_USER;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE $NOTIFICATIONS_ADMIN_DB_USER
    GRANT USAGE, SELECT ON SEQUENCES TO $NOTIFICATIONS_DB_USER;

END