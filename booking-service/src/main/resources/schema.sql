CREATE TABLE IF NOT EXISTS booking(
    id INT NOT NULL AUTO_INCREMENT,
    event VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL,
    event_date_time DATETIME NOT NULL);