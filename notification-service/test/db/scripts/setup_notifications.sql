-- Create Database
DROP DATABASE IF EXISTS notifications;
CREATE DATABASE notifications
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- Create  role notifications_admin_user
DROP ROLE IF EXISTS notifications_user_test;
CREATE ROLE notifications_user_test WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'notifications_password_test';

-- Grant permissions for notifications_admin_user
GRANT ALL ON DATABASE notifications TO notifications_user_test;

-- move to notifications db
\c notifications

-- setup schema
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to notifications_user_test;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to notifications_user_test;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to notifications_user_test;




