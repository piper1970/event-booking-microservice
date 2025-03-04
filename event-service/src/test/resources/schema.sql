CREATE SCHEMA IF NOT EXISTS event_service;

CREATE TABLE IF NOT EXISTS event_service.events
(
    id                 int GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    facilitator        varchar(60)   NOT NULL,
    title              varchar(255)  NOT NULL,
    description        varchar(255),
    location           varchar(255)  NOT NULL,
    event_date_time    timestamp     NOT NULL,
    created_date_time  timestamp,
    updated_date_time  timestamp,
    cost               numeric(6, 2) NOT NULL,
    available_bookings smallint      NOT NULL,
    event_status       varchar(30)   NOT NULL
);