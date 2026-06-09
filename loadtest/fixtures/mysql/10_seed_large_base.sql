SET @member_count = 100000;
SET @hot_seat_count = 50000;
SET @history_seat_count = 50000;
SET @reservation_count = 300000;
SET @hot_concert_id = 1;
SET @history_concert_id = 2;
SET @hot_schedule_id = 1;
SET @history_schedule_id = 2;
SET @now = NOW();
SET @password_hash = '$2a$10$K0YYNpidjxzo2NEKcKyQXe7KzA0pSxU.S2.eNdsQOkMl.Own.yLmy';
SET @max_sequence = GREATEST(@member_count, @hot_seat_count, @history_seat_count, @reservation_count);

CREATE TEMPORARY TABLE digits (
    n INT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO digits (n)
VALUES (0), (1), (2), (3), (4), (5), (6), (7), (8), (9);

CREATE TEMPORARY TABLE seq (
    n INT NOT NULL PRIMARY KEY
) ENGINE=MEMORY;

INSERT INTO seq (n)
SELECT generated.n + 1
FROM (
    SELECT ones.n
         + tens.n * 10
         + hundreds.n * 100
         + thousands.n * 1000
         + ten_thousands.n * 10000
         + hundred_thousands.n * 100000 AS n
    FROM digits ones
    CROSS JOIN digits tens
    CROSS JOIN digits hundreds
    CROSS JOIN digits thousands
    CROSS JOIN digits ten_thousands
    CROSS JOIN digits hundred_thousands
) generated
WHERE generated.n < @max_sequence;

INSERT INTO members (
    member_id,
    email,
    password,
    nickname,
    role,
    point,
    created_at,
    updated_at
)
SELECT n,
       CONCAT('loadtest-user-', LPAD(n, 6, '0'), '@test.com'),
       @password_hash,
       CONCAT('loadtest-user-', LPAD(n, 6, '0')),
       'USER',
       100000,
       @now,
       @now
FROM seq
WHERE n <= @member_count;

INSERT INTO concerts (
    concert_id,
    title,
    description,
    created_at,
    updated_at
)
VALUES
    (@hot_concert_id, 'Loadtest Hot Concert', 'Schedule used by mutable queue and reservation load scenarios.', @now, @now),
    (@history_concert_id, 'Loadtest History Concert', 'Schedule used only for large historical reservation data.', @now, @now);

INSERT INTO concert_schedules (
    schedule_id,
    concert_id,
    concert_date,
    reservation_open_at,
    total_seats,
    created_at,
    updated_at
)
VALUES
    (@hot_schedule_id, @hot_concert_id, DATE_ADD(@now, INTERVAL 30 DAY), DATE_SUB(@now, INTERVAL 1 DAY), @hot_seat_count, @now, @now),
    (@history_schedule_id, @history_concert_id, DATE_SUB(@now, INTERVAL 30 DAY), DATE_SUB(@now, INTERVAL 60 DAY), @history_seat_count, @now, @now);

INSERT INTO seats (
    seat_id,
    schedule_id,
    seat_no,
    grade,
    price,
    member_id,
    status,
    created_at,
    updated_at
)
SELECT n,
       @hot_schedule_id,
       n,
       CASE
           WHEN n % 10 = 0 THEN 'VIP'
           WHEN n % 3 = 0 THEN 'R'
           ELSE 'STANDARD'
       END,
       CASE
           WHEN n % 10 = 0 THEN 150000.00
           WHEN n % 3 = 0 THEN 120000.00
           ELSE 99000.00
       END,
       NULL,
       'AVAILABLE',
       @now,
       @now
FROM seq
WHERE n <= @hot_seat_count;

INSERT INTO seats (
    seat_id,
    schedule_id,
    seat_no,
    grade,
    price,
    member_id,
    status,
    created_at,
    updated_at
)
SELECT @hot_seat_count + n,
       @history_schedule_id,
       n,
       CASE
           WHEN n % 10 = 0 THEN 'VIP'
           WHEN n % 3 = 0 THEN 'R'
           ELSE 'STANDARD'
       END,
       CASE
           WHEN n % 10 = 0 THEN 150000.00
           WHEN n % 3 = 0 THEN 120000.00
           ELSE 99000.00
       END,
       CASE
           WHEN n % 5 = 0 THEN NULL
           ELSE ((n - 1) % @member_count) + 1
       END,
       CASE
           WHEN n % 5 = 0 THEN 'AVAILABLE'
           ELSE 'SOLD_OUT'
       END,
       @now,
       @now
FROM seq
WHERE n <= @history_seat_count;

INSERT INTO reservations (
    reservation_id,
    member_id,
    schedule_id,
    seat_id,
    status,
    reserved_at,
    expired_at,
    created_at,
    updated_at
)
SELECT n,
       ((n - 1) % @member_count) + 1,
       @history_schedule_id,
       @hot_seat_count + ((n - 1) % @history_seat_count) + 1,
       CASE
           WHEN n % 5 = 0 THEN 'CANCELLED'
           ELSE 'CONFIRMED'
       END,
       DATE_SUB(@now, INTERVAL (n % 90) DAY),
       NULL,
       DATE_SUB(@now, INTERVAL (n % 90) DAY),
       DATE_SUB(@now, INTERVAL (n % 89) DAY)
FROM seq
WHERE n <= @reservation_count;

INSERT INTO payments (
    payment_id,
    reservation_id,
    member_id,
    amount,
    status,
    paid_at,
    created_at,
    updated_at
)
SELECT n,
       n,
       ((n - 1) % @member_count) + 1,
       CASE
           WHEN n % 10 = 0 THEN 150000.00
           WHEN n % 3 = 0 THEN 120000.00
           ELSE 99000.00
       END,
       'COMPLETED',
       DATE_ADD(DATE_SUB(@now, INTERVAL (n % 90) DAY), INTERVAL 5 MINUTE),
       DATE_SUB(@now, INTERVAL (n % 90) DAY),
       DATE_ADD(DATE_SUB(@now, INTERVAL (n % 90) DAY), INTERVAL 5 MINUTE)
FROM seq
WHERE n <= @reservation_count
  AND n % 5 <> 0;

ALTER TABLE members AUTO_INCREMENT = 100001;
ALTER TABLE concerts AUTO_INCREMENT = 3;
ALTER TABLE concert_schedules AUTO_INCREMENT = 3;
ALTER TABLE seats AUTO_INCREMENT = 100001;
ALTER TABLE reservations AUTO_INCREMENT = 300001;
ALTER TABLE payments AUTO_INCREMENT = 300001;
