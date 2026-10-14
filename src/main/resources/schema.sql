CREATE TABLE members (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'member primary key',
    email VARCHAR(100) NOT NULL COMMENT 'login email',
    password VARCHAR(255) NOT NULL COMMENT 'encoded password',
    nickname VARCHAR(50) NOT NULL COMMENT 'member nickname',
    role VARCHAR(20) NOT NULL DEFAULT 'USER' COMMENT 'member role',
    point BIGINT NOT NULL DEFAULT 0 COMMENT 'member point',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created timestamp',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated timestamp',
    CONSTRAINT uk_members_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='member information';

CREATE TABLE concerts (
    concert_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'concert primary key',
    title VARCHAR(200) NOT NULL COMMENT 'concert title',
    description TEXT COMMENT 'concert description',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created timestamp',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated timestamp'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='concert metadata';

CREATE TABLE concert_schedules (
    schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'schedule primary key',
    concert_id BIGINT NOT NULL COMMENT 'concert foreign key',
    concert_date DATETIME NOT NULL COMMENT 'concert datetime',
    reservation_open_at DATETIME NOT NULL COMMENT 'reservation open datetime',
    total_seats INT NOT NULL DEFAULT 0 COMMENT 'total seat count',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created timestamp',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated timestamp',
    CONSTRAINT fk_concert_schedules_concert
        FOREIGN KEY (concert_id) REFERENCES concerts(concert_id),
    INDEX idx_concert_schedules_concert_date (concert_id, concert_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='concert schedule';

CREATE TABLE seats (
    seat_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'seat primary key',
    schedule_id BIGINT NOT NULL COMMENT 'schedule foreign key',
    seat_no INT NOT NULL COMMENT 'seat number',
    grade VARCHAR(20) NOT NULL DEFAULT 'STANDARD' COMMENT 'seat grade',
    price DECIMAL(10, 2) NOT NULL COMMENT 'seat price',
    member_id BIGINT DEFAULT NULL COMMENT 'current holder member id',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT 'seat status',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created timestamp',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated timestamp',
    CONSTRAINT fk_seats_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_seats_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT uk_seats_schedule_seat_no UNIQUE (schedule_id, seat_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='seat current status';

CREATE TABLE reservations (
    reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'reservation primary key',
    member_id BIGINT NOT NULL COMMENT 'member foreign key',
    schedule_id BIGINT NOT NULL COMMENT 'schedule foreign key',
    seat_id BIGINT NOT NULL COMMENT 'seat foreign key',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'reservation status',
    reserved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'reserved timestamp',
    expired_at DATETIME DEFAULT NULL COMMENT 'reservation expiration timestamp',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created timestamp',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated timestamp',
    CONSTRAINT fk_reservations_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT fk_reservations_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_reservations_seat
        FOREIGN KEY (seat_id) REFERENCES seats(seat_id),
    INDEX idx_reservations_member_reserved_at (member_id, reserved_at DESC),
    INDEX idx_reservations_status_expired_at_reservation_id (status, expired_at, reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='reservation history';

CREATE TABLE payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT 'payment primary key',
    attempt_id CHAR(36) NOT NULL COMMENT 'current payment attempt',
    reservation_id BIGINT NOT NULL COMMENT 'reservation foreign key',
    member_id BIGINT NOT NULL COMMENT 'member foreign key',
    amount DECIMAL(10, 2) NOT NULL COMMENT 'payment amount',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'payment status',
    paid_at DATETIME DEFAULT NULL COMMENT 'payment timestamp',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'created timestamp',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated timestamp',
    CONSTRAINT fk_payments_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT fk_payments_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT uk_payments_reservation UNIQUE (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='payment history';

CREATE TABLE payment_outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id CHAR(36) NOT NULL,
    payment_id BIGINT NOT NULL,
    attempt_id CHAR(36) NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    schema_version INT NOT NULL,
    payload JSON NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'READY',
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    claim_token CHAR(36) DEFAULT NULL,
    locked_until DATETIME(6) DEFAULT NULL,
    last_error VARCHAR(2000) DEFAULT NULL,
    published_at DATETIME(6) DEFAULT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_payment_outbox_payment FOREIGN KEY (payment_id) REFERENCES payments(payment_id),
    CONSTRAINT uk_payment_outbox_event UNIQUE (event_id),
    CONSTRAINT uk_payment_outbox_attempt UNIQUE (payment_id, attempt_id, event_type),
    INDEX idx_payment_outbox_ready (status, available_at, id),
    INDEX idx_payment_outbox_expired (status, locked_until, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='payment publication outbox';
