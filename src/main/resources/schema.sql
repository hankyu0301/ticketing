DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS seats;
DROP TABLE IF EXISTS concert_schedules;
DROP TABLE IF EXISTS concerts;
DROP TABLE IF EXISTS members;

CREATE TABLE members (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50) NOT NULL,
    point BIGINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE concerts (
    concert_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE concert_schedules (
    schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    concert_id BIGINT NOT NULL,
    concert_date DATETIME NOT NULL,
    reservation_open_at DATETIME NOT NULL,
    total_seats INT NOT NULL DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_concert_schedules_concert
        FOREIGN KEY (concert_id) REFERENCES concerts(concert_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE seats (
    seat_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    schedule_id BIGINT NOT NULL,
    seat_no INT NOT NULL,
    grade VARCHAR(20) DEFAULT 'STANDARD',
    price DECIMAL(10, 2) NOT NULL,
    member_id BIGINT,
    status VARCHAR(20) DEFAULT 'AVAILABLE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_seats_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_seats_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT uk_schedule_seat UNIQUE (schedule_id, seat_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reservations (
    reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING',
    reserved_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    expired_at DATETIME,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservations_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT fk_reservations_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_reservations_seat
        FOREIGN KEY (seat_id) REFERENCES seats(seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reservation_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'COMPLETED',
    paid_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payments_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT fk_payments_member
        FOREIGN KEY (member_id) REFERENCES members(member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
