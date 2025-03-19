CREATE SCHEMA IF NOT EXISTS event_service;

CREATE TABLE IF NOT EXISTS event_service.bookings
(
    id                int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id          integer      NOT NULL,
    username          varchar(60)  NOT NULL,
    email             varchar(255) NOT NULL,
    event_date_time   timestamp    NOT NULL,
    booking_status    varchar(30)  NOT NULL,
    created_date_time timestamp,
    updated_date_time timestamp
);