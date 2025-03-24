#!/bin/bash

# Relies on KC_ADMIN and KC_ADMIN_PASSWORD environment variables

psql <<-END
-- create database keycloak
DROP DATABASE IF EXISTS keycloak;
CREATE DATABASE keycloak
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- create role for admin user
DROP ROLE IF EXISTS $KC_ADMIN;
CREATE ROLE $KC_ADMIN WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD '$KC_ADMIN_PASSWORD';

-- grant all privileges to admin role
GRANT ALL ON DATABASE keycloak TO $KC_ADMIN;

-- move to keycloak db
\c keycloak

-- setup grant permissions for admin role
GRANT ALL ON SCHEMA public TO $KC_ADMIN;
GRANT ALL ON ALL TABLES IN SCHEMA public to $KC_ADMIN;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public to $KC_ADMIN;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES to $KC_ADMIN;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO $KC_ADMIN;

END