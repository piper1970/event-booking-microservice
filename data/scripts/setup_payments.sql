-- Create Database payments
DROP DATABASE IF EXISTS payments;
CREATE DATABASE payments
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- Create admin role payments_admin_user
DROP ROLE IF EXISTS payments_admin_user;
CREATE ROLE payments_admin_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'payments_admin_password';
-- make sure .env file matches

-- Grant permissions for payments_admin_user
GRANT ALL ON DATABASE payments TO payments_admin_user;

-- move to payments db
\c payments

-- setup schema for payments
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to payments_admin_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to payments_admin_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to payments_admin_user;

-- Create regular user role payments_user
DROP ROLE IF EXISTS payments_user;
CREATE ROLE payments_user WITH
    LOGIN
    NOSUPERUSER
    INHERIT
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'payments_password';
-- make sure .env file matches

-- Grant permissions for payments_user
GRANT CONNECT ON DATABASE payments TO payments_user;
GRANT USAGE ON SCHEMA event_service to payments_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA event_service to payments_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA event_service to payments_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE payments_admin_user
    GRANT SELECT, UPDATE, DELETE, INSERT ON TABLES TO payments_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE payments_admin_user
    GRANT USAGE, SELECT ON SEQUENCES TO payments_user;