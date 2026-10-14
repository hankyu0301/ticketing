SET @hot_schedule_id = 1;

DELETE o
FROM payment_outbox o
INNER JOIN payments p ON p.payment_id = o.payment_id
INNER JOIN reservations r ON r.reservation_id = p.reservation_id
WHERE r.schedule_id = @hot_schedule_id;

DELETE p
FROM payments p
INNER JOIN reservations r
    ON r.reservation_id = p.reservation_id
WHERE r.schedule_id = @hot_schedule_id;

DELETE
FROM reservations
WHERE schedule_id = @hot_schedule_id;

UPDATE seats
SET member_id = NULL,
    status = 'AVAILABLE',
    updated_at = NOW()
WHERE schedule_id = @hot_schedule_id;

ALTER TABLE reservations AUTO_INCREMENT = 300001;
ALTER TABLE payments AUTO_INCREMENT = 300001;
