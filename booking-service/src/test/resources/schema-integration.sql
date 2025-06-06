CREATE TABLE IF NOT EXISTS event_service.bookings
(
    id                int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    version           integer,
    event_id          integer      NOT NULL,
    username          varchar(60)  NOT NULL,
    email             varchar(255) NOT NULL,
    booking_status    varchar(30)  NOT NULL
);