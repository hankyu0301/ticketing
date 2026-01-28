DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS reservations;
DROP TABLE IF EXISTS seats;
DROP TABLE IF EXISTS concert_schedules;
DROP TABLE IF EXISTS concerts;
DROP TABLE IF EXISTS members;

/* =======================================================
 * 1. 회원 (Members)
 * ======================================================= */
CREATE TABLE members (
                         member_id   BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '회원 ID (PK)',
                         email       VARCHAR(100) NOT NULL UNIQUE COMMENT '이메일 (로그인 ID)',
                         password    VARCHAR(255) NOT NULL COMMENT '암호화된 비밀번호',
                         nickname    VARCHAR(50) NOT NULL COMMENT '닉네임',
                         point       BIGINT DEFAULT 0 COMMENT '보유 포인트 (결제 수단)',
                         created_at  DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '가입일시',
                         updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '수정일시'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='회원 정보';

/* =======================================================
 * 2. 콘서트 (Concerts)
 * - 공연의 메타 정보 (예: 아이유 콘서트)
 * ======================================================= */
CREATE TABLE concerts (
                          concert_id  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '공연 ID (PK)',
                          title       VARCHAR(200) NOT NULL COMMENT '공연 제목',
                          description TEXT COMMENT '공연 설명',
                          created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                          updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공연 메타 정보';

/* =======================================================
 * 3. 공연 일정 (Concert Schedules)
 * - 실제 예매 단위 (예: 아이유 콘서트 - 2024년 10월 25일 회차)
 * ======================================================= */
CREATE TABLE concert_schedules (
                                   schedule_id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '일정 ID (PK)',
                                   concert_id          BIGINT NOT NULL COMMENT '공연 ID (FK)',
                                   concert_date        DATETIME NOT NULL COMMENT '실제 공연 일시',
                                   reservation_open_at DATETIME NOT NULL COMMENT '티켓 예매 오픈 일시',
                                   total_seats         INT NOT NULL DEFAULT 0 COMMENT '총 좌석 수',
                                   created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
                                   updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                                   FOREIGN KEY (concert_id) REFERENCES concerts(concert_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='공연 일정(회차) 정보';

/* =======================================================
 * 4. 좌석 (Seats) -
 * - 미리 생성되는 데이터
 * - member_id가 NULL이면 빈 좌석, 값이 있으면 점유된 좌석
 * ======================================================= */
CREATE TABLE seats (
                       seat_id     BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '좌석 ID (PK)',
                       schedule_id BIGINT NOT NULL COMMENT '공연 일정 ID (FK)',
                       seat_no     INT NOT NULL COMMENT '좌석 번호 (1 ~ N)',
                       grade       VARCHAR(20) DEFAULT 'STANDARD' COMMENT '좌석 등급 (VIP, R, S)',
                       price       DECIMAL(10, 2) NOT NULL COMMENT '가격',

    -- 동시성 제어 및 예약 상태 관리
                       member_id   BIGINT COMMENT '예약자 ID (FK, NULL 허용)',
                       status      VARCHAR(20) DEFAULT 'AVAILABLE' COMMENT '상태 (AVAILABLE, TEMPORARY, SOLD_OUT)',

                       created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
                       updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                       FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
                       FOREIGN KEY (member_id) REFERENCES members(member_id),

    -- 한 회차 내에서 좌석 번호는 유일해야 함
                       UNIQUE KEY uk_schedule_seat (schedule_id, seat_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='좌석 정보';

/* =======================================================
 * 5. 예약 (Reservations)
 * - 좌석 선점 후 생성되는 예약 내역
 * ======================================================= */
CREATE TABLE reservations (
                              reservation_id  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '예약 ID (PK)',
                              member_id       BIGINT NOT NULL COMMENT '회원 ID (FK)',
                              schedule_id     BIGINT NOT NULL COMMENT '공연 일정 ID (FK)',
                              seat_id         BIGINT NOT NULL COMMENT '좌석 ID (FK)',

                              status          VARCHAR(20) DEFAULT 'PENDING' COMMENT '상태 (PENDING:결제대기, CONFIRMED:완료, CANCELLED:취소)',
                              reserved_at     DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '예약 생성 일시',
                              expired_at      DATETIME COMMENT '결제 마감 기한 (생성 후 5~10분)',

                              created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
                              updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

                              FOREIGN KEY (member_id) REFERENCES members(member_id),
                              FOREIGN KEY (schedule_id) REFERENCES concert_schedules(schedule_id),
                              FOREIGN KEY (seat_id) REFERENCES seats(seat_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='예약 내역';

/* =======================================================
 * 6. 결제 (Payments)
 * - 예약에 대한 실제 결제 이력
 * ======================================================= */
CREATE TABLE payments (
                          payment_id      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '결제 ID (PK)',
                          reservation_id  BIGINT NOT NULL COMMENT '예약 ID (FK)',
                          member_id       BIGINT NOT NULL COMMENT '회원 ID (FK)',

                          amount          DECIMAL(10, 2) NOT NULL COMMENT '결제 금액',
                          status          VARCHAR(20) DEFAULT 'COMPLETED' COMMENT '상태 (COMPLETED, FAILED, REFUNDED)',
                          paid_at         DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '결제 완료 일시',

                          FOREIGN KEY (reservation_id) REFERENCES reservations(reservation_id),
                          FOREIGN KEY (member_id) REFERENCES members(member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='결제 내역';