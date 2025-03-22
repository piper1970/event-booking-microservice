-- Create Database
DROP DATABASE IF EXISTS bookings;
CREATE DATABASE bookings
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- Create role test_bookings_user
DROP ROLE IF EXISTS test_bookings_user;
CREATE ROLE test_bookings_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'test_bookings_password';

-- Grant permissions for bookings_admin_user
GRANT ALL ON DATABASE bookings TO test_bookings_user;

-- move to bookings db
\c bookings

-- setup schema
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to test_bookings_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to test_bookings_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to test_bookings_user;




