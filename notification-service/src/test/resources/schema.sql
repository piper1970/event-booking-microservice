CREATE SCHEMA IF NOT EXISTS event_service;

CREATE TABLE IF NOT EXISTS event_service.booking_confirmations
(
    id                     int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    booking_id             int          NOT NULL,
    event_id               int          NOT NULL,
    confirmation_string    UUID         NOT NULL,
    booking_user           varchar(255) NOT NULL,
    booking_email          varchar(255) NOT NULL,
    confirmation_date_time timestamp    NOT NULL,
    duration_in_minutes    int          NOT NULL,
    confirmation_status    varchar(30)  NOT NULL
);