CREATE SCHEMA IF NOT EXISTS event_service;

CREATE TABLE IF NOT EXISTS event_service.bookings
(
    id                int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id          int     NOT NULL,
    username          varchar(60) NOT NULL,
    event_date_time   timestamp   NOT NULL,
    created_date_time timestamp,
    updated_date_time timestamp,
    booking_status    varchar(30) NOT NULL
);