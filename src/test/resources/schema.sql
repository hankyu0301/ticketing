DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS seats;
DROP TABLE IF EXISTS concert_schedules;
DROP TABLE IF EXISTS concerts;
DROP TABLE IF EXISTS members;

CREATE TABLE members (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    point BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE members
    ADD CONSTRAINT uk_members_email UNIQUE (email);

CREATE TABLE concerts (
    concert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE concert_schedules (
    schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    concert_id BIGINT NOT NULL,
    concert_date DATETIME NOT NULL,
    reservation_open_at DATETIME NOT NULL,
    total_seats INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_concert_schedules_concert
        FOREIGN KEY (concert_id) REFERENCES concerts(concert_id)
);

CREATE INDEX idx_concert_schedules_concert_date
    ON concert_schedules (concert_id, concert_date);

CREATE TABLE seats (
    seat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    seat_no INT NOT NULL,
    grade VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    price DECIMAL(10, 2) NOT NULL,
    member_id BIGINT DEFAULT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_seats_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_seats_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT uk_seats_schedule_seat_no UNIQUE (schedule_id, seat_no)
);

CREATE TABLE reservations (
    reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reserved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expired_at DATETIME DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT fk_reservations_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_reservations_seat
        FOREIGN KEY (seat_id) REFERENCES seats(seat_id)
);

CREATE INDEX idx_reservations_member_reserved_at
    ON reservations (member_id, reserved_at);

CREATE TABLE payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    paid_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT fk_payments_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT uk_payments_reservation UNIQUE (reservation_id)
);
