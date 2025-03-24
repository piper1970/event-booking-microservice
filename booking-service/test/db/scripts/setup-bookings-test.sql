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
DROP ROLE IF EXISTS bookings_user_test;
CREATE ROLE bookings_user_test WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD 'bookings_password_test';

-- Grant permissions for bookings_admin_user
GRANT ALL ON DATABASE bookings TO bookings_user_test;

-- move to bookings db
\c bookings

-- setup schema
CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to bookings_user_test;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to bookings_user_test;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to bookings_user_test;




