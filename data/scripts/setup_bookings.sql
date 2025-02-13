-- Create Database
DROP DATABASE IF EXISTS bookings;
CREATE DATABASE bookings
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LOCALE_PROVIDER = 'libc'
    CONNECTION LIMIT = -1
    IS_TEMPLATE = False;

-- Create admin role bookings_admin_user
DROP ROLE IF EXISTS bookings_admin_user;
CREATE ROLE bookings_admin_user WITH
    LOGIN
    NOSUPERUSER
    NOCREATEDB
    NOCREATEROLE
    INHERIT
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD '<see .env file>';

-- Grant permissions for bookings_admin_user
GRANT ALL ON DATABASE bookings TO bookings_admin_user;

-- make sure to be in bookings db
-- \c bookings

CREATE SCHEMA event_service;
GRANT ALL ON SCHEMA event_service to bookings_admin_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_service to bookings_admin_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA event_service to bookings_admin_user;

-- Create regular user role bookings_user
DROP ROLE IF EXISTS bookings_user;
CREATE ROLE bookings_user WITH
    LOGIN
    NOSUPERUSER
    INHERIT
    NOCREATEDB
    NOCREATEROLE
    NOREPLICATION
    NOBYPASSRLS
    CONNECTION LIMIT -1
    ENCRYPTED PASSWORD '<see .env file>';

-- Grant permissions for bookings_user
GRANT CONNECT ON DATABASE bookings TO bookings_user;
GRANT USAGE ON SCHEMA event_service to bookings_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA event_service to bookings_user;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA event_service to bookings_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE bookings_admin_user
    GRANT SELECT, UPDATE, DELETE, INSERT ON TABLES TO bookings_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA event_service FOR ROLE bookings_admin_user
    GRANT USAGE, SELECT ON SEQUENCES TO bookings_user;



