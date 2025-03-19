-- Create Database
DROP DATABASE IF EXISTS notifications;
CREATE DATABASE notifications
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- Create admin role notifications_admin_user
DROP ROLE IF EXISTS notifications_admin_user;
CREATE ROLE notifications_admin_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'notifications_admin_password';
-- make sure .env file matches

-- Grant permissions for notifications_admin_user
GRANT ALL ON DATABASE notifications TO notifications_admin_user;

-- move to notifications db
\c notifications

-- setup schema
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to notifications_admin_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to notifications_admin_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to notifications_admin_user;

-- Create regular user notifications_user
DROP ROLE IF EXISTS notifications_user;
CREATE ROLE notifications_user WITH
    LOGIN
    NOSUPERUSER
    INHERIT
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'notifications_password';
-- make sure .env file matches

-- Grant permissions for notifications_user
GRANT CONNECT ON DATABASE notifications TO notifications_user;
GRANT USAGE ON SCHEMA event_service to notifications_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA event_service to notifications_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA event_service to notifications_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE notifications_admin_user
    GRANT SELECT, UPDATE, DELETE, INSERT ON TABLES TO notifications_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE notifications_admin_user
    GRANT USAGE, SELECT ON SEQUENCES TO notifications_user;



