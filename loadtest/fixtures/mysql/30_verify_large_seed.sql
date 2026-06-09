SELECT 'members' AS table_name, COUNT(*) AS row_count FROM members
UNION ALL
SELECT 'concerts', COUNT(*) FROM concerts
UNION ALL
SELECT 'concert_schedules', COUNT(*) FROM concert_schedules
UNION ALL
SELECT 'seats', COUNT(*) FROM seats
UNION ALL
SELECT 'reservations', COUNT(*) FROM reservations
UNION ALL
SELECT 'payments', COUNT(*) FROM payments;

SELECT schedule_id,
       COUNT(*) AS seat_count,
       SUM(status = 'AVAILABLE') AS available_seats,
       SUM(status = 'TEMPORARY') AS temporary_seats,
       SUM(status = 'SOLD_OUT') AS sold_out_seats
FROM seats
GROUP BY schedule_id
ORDER BY schedule_id;

SELECT schedule_id,
       status,
       COUNT(*) AS reservation_count
FROM reservations
GROUP BY schedule_id, status
ORDER BY schedule_id, status;
