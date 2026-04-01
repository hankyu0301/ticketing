CREATE TABLE members (
    member_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 ID (PK)',
    email VARCHAR(100) NOT NULL COMMENT '이메일 (로그인 ID)',
    password VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
    nickname VARCHAR(50) NOT NULL COMMENT '닉네임',
    point BIGINT NOT NULL DEFAULT 0 COMMENT '보유 포인트',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT uk_members_email UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회원 정보';

CREATE TABLE concerts (
    concert_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '공연 ID (PK)',
    title VARCHAR(200) NOT NULL COMMENT '공연 제목',
    description TEXT COMMENT '공연 설명',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공연 메타 정보';

CREATE TABLE concert_schedules (
    schedule_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회차 ID (PK)',
    concert_id BIGINT NOT NULL COMMENT '공연 ID (FK)',
    concert_date DATETIME NOT NULL COMMENT '실제 공연 일시',
    reservation_open_at DATETIME NOT NULL COMMENT '예매 오픈 일시',
    total_seats INT NOT NULL DEFAULT 0 COMMENT '총 좌석 수',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT fk_concert_schedules_concert
        FOREIGN KEY (concert_id) REFERENCES concerts(concert_id),
    INDEX idx_concert_schedules_concert_date (concert_id, concert_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공연 회차 정보';

CREATE TABLE seats (
    seat_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '좌석 ID (PK)',
    schedule_id BIGINT NOT NULL COMMENT '회차 ID (FK)',
    seat_no INT NOT NULL COMMENT '좌석 번호',
    grade VARCHAR(20) NOT NULL DEFAULT 'STANDARD' COMMENT '좌석 등급',
    price DECIMAL(10, 2) NOT NULL COMMENT '좌석 가격',
    member_id BIGINT DEFAULT NULL COMMENT '현재 점유 회원 ID',
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' COMMENT '좌석 상태',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT fk_seats_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_seats_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT uk_seats_schedule_seat_no UNIQUE (schedule_id, seat_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='좌석 현재 상태';

CREATE TABLE reservations (
    reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '예약 ID (PK)',
    member_id BIGINT NOT NULL COMMENT '회원 ID (FK)',
    schedule_id BIGINT NOT NULL COMMENT '회차 ID (FK)',
    seat_id BIGINT NOT NULL COMMENT '좌석 ID (FK)',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '예약 상태',
    reserved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '예약 생성 시각',
    expired_at DATETIME DEFAULT NULL COMMENT '결제 만료 시각',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '생성 일시',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정 일시',
    CONSTRAINT fk_reservations_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT fk_reservations_schedule
        FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
    CONSTRAINT fk_reservations_seat
        FOREIGN KEY (seat_id) REFERENCES seats(seat_id),
    INDEX idx_reservations_member_reserved_at (member_id, reserved_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='예약 이력';

CREATE TABLE payments (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 ID (PK)',
    reservation_id BIGINT NOT NULL COMMENT '예약 ID (FK)',
    member_id BIGINT NOT NULL COMMENT '회원 ID (FK)',
    amount DECIMAL(10, 2) NOT NULL COMMENT '결제 금액',
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' COMMENT '결제 상태',
    paid_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '결제 시각',
    CONSTRAINT fk_payments_reservation
        FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
    CONSTRAINT fk_payments_member
        FOREIGN KEY (member_id) REFERENCES members(member_id),
    CONSTRAINT uk_payments_reservation UNIQUE (reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='결제 이력';
