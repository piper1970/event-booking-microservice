CREATE TABLE IF NOT EXISTS event(
    id INT NOT NULL AUTO_INCREMENT,
    title VARCHAR(255) UNIQUE NOT NULL,
    description VARCHAR(255),
    location VARCHAR(255) NOT NULL,
    event_date_time DATETIME NOT NULL,
    cost NUMERIC(10, 2) NOT NULL,
    available_bookings INT NOT NULL);