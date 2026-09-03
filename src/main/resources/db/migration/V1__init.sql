CREATE TABLE flight (
                        id                  BIGSERIAL PRIMARY KEY,
                        flight_number       VARCHAR(255) NOT NULL,
                        origin              VARCHAR(255) NOT NULL,
                        destination         VARCHAR(255) NOT NULL,
                        departure_city      VARCHAR(255),
                        scheduled_departure TIMESTAMP    NOT NULL,
                        departure_zone      VARCHAR(255) NOT NULL,
                        total_seats         INT          NOT NULL
);

CREATE TABLE booking (
                         id               BIGSERIAL PRIMARY KEY,
                         flight_id        BIGINT       NOT NULL REFERENCES flight(id),
                         passenger_name   VARCHAR(255) NOT NULL,
                         passenger_email  VARCHAR(255) NOT NULL,
                         status           VARCHAR(32)  NOT NULL,
                         created_at       TIMESTAMP    NOT NULL,
                         hold_expires_at  TIMESTAMP
);

CREATE INDEX idx_booking_flight_status ON booking(flight_id, status);
CREATE INDEX idx_booking_status_expiry ON booking(status, hold_expires_at);