CREATE TABLE IF NOT EXISTS bookings
(
    id                int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    event_id          integer      NOT NULL,
    username          varchar(60)  NOT NULL,
    email             varchar(255) NOT NULL,
    booking_status    varchar(30)  NOT NULL,
    created_date_time timestamp,
    updated_date_time timestamp
);