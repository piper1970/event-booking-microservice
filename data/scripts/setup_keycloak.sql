-- create database keycloak
DROP DATABASE IF EXISTS keycloak;
CREATE DATABASE keycloak
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- create role keycloak_admin_user
DROP ROLE IF EXISTS keycloak_admin_user;
CREATE ROLE keycloak_admin_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'keycloak_admin_password';
-- make sure .env file matches for KC_ADMIN_PASSWORD

-- grant all privileges to keycloak_admin_user role
GRANT ALL ON DATABASE keycloak TO keycloak_admin_user;

-- move to keycloak db
\c keycloak

-- setup grant permissions for keycloak_admin_user
GRANT ALL ON SCHEMA public TO keycloak_admin_user;
GRANT ALL ON ALL TABLES IN SCHEMA public to keycloak_admin_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public to keycloak_admin_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES to keycloak_admin_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO keycloak_admin_user;
