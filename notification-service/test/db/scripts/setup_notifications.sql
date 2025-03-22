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
DROP ROLE IF EXISTS test_notifications_user;
CREATE ROLE test_notifications_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'test_notifications_password';

-- Grant permissions for notifications_admin_user
GRANT ALL ON DATABASE notifications TO test_notifications_user;

-- move to notifications db
\c notifications

-- setup schema
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to test_notifications_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to test_notifications_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to test_notifications_user;




