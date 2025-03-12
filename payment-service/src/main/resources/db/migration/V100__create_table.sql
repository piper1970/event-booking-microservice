CREATE TABLE IF NOT EXISTS payments
(
    id                int GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    booking_id        int NOT NULL,
    payment_status    varchar(30) NOT NULL,
    payment_token     uuid  NOT NULL,
    created_date_time timestamp,
    updated_date_time timestamp
);