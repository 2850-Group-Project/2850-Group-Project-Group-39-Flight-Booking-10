----------------------------------------
--       SQL QUERIES GO HERE          --
--      RUN WITH CTRL+SHIFT+Q         --
----------------------------------------

-- CREATE TABLE IF NOT EXISTS user_points (
--     user_points_id INTEGER PRIMARY KEY AUTOINCREMENT,
--     user_id INTEGER NOT NULL UNIQUE REFERENCES user(user_id),
--     balance INTEGER NOT NULL DEFAULT 0
-- );

-- CREATE TABLE IF NOT EXISTS points_transaction (
--     points_transaction_id INTEGER PRIMARY KEY AUTOINCREMENT,
--     user_id INTEGER NOT NULL REFERENCES user(user_id),
--     booking_id INTEGER REFERENCES booking(booking_id),
--     type VARCHAR(20) NOT NULL, -- earn redeem expire adjust
--     points INTEGER NOT NULL,
--     balance_after INTEGER NOT NULL,
--     description VARCHAR(255),
--     created_at VARCHAR(50) NOT NULL
-- );

select * from complaint;

-- select * from user_points;
-- select * from points_transaction;


-- select * from passenger where passenger_id = 1234;
-- select * from seat_assignment where passenger_id = 1234;
-- select * from passenger where passenger_id > 1250;
-- select * from seat_assignment where passenger_id = 1252;
-- select * from seat where seat_id = 3114;
-- select * from passenger;
-- ADDED LONDON TO NEW YORK
-- INSERT INTO flight_fare (
--     flight_fare_id, flight_id, fare_class_id,
--     price, currency, seats_available
-- ) VALUES (
--     3001, 10001, 1,
--     349.99, 'GBP', 100
-- );

-- INSERT INTO flight (
--     flight_id, flight_number, origin_airport, destination_airport,
--     scheduled_departure_time, scheduled_arrival_time, status, capacity
-- ) VALUES (
--     10001, 1001, 1, 2,
--     '2026-08-12 10:00:00',
--     '2026-08-12 13:00:00',
--     'scheduled',
--     180
-- );


-- SELECT 
--     f.flight_id,
--     f.flight_number,
--     a1.iata_code AS origin,
--     a2.iata_code AS destination,
--     COUNT(DISTINCT s.seat_id) AS seat_count,
--     COUNT(DISTINCT ff.flight_fare_id) AS fare_options
-- FROM flight f
-- JOIN airport a1 ON f.origin_airport = a1.airport_id
-- JOIN airport a2 ON f.destination_airport = a2.airport_id

-- -- must have seats
-- JOIN seat s ON s.flight_id = f.flight_id

-- -- must have fares
-- JOIN flight_fare ff ON ff.flight_id = f.flight_id

-- GROUP BY f.flight_id
-- HAVING seat_count > 0 AND fare_options > 0
-- ORDER BY seat_count DESC, fare_options DESC;



-- SELECT iata_code, name, city, airport_id 
-- FROM airport 
-- WHERE iata_code IN ('GLA', 'LHR');

-- select * from flight where origin_airport = 1919;

-- THESE ARE THE AIRPORTS WE WILL USE FOR THE DEMO
-- SELECT airport_id, iata_code, city FROM airport WHERE iata_code IN ('MHD', 'ABD');

-- select * from flight where flight_id = 503;
-- select * from airport where airport.airport_id = 911;

-- SELECT 
--     f.flight_id,
--     f.flight_number,
--     a1.iata_code AS origin,
--     a2.iata_code AS destination,
--     s.seat_id,
--     s.seat_code,
--     s.cabin_class,
--     s.status,
--     sa.passenger_id
-- FROM flight f
-- JOIN airport a1 ON f.origin_airport = a1.airport_id
-- JOIN airport a2 ON f.destination_airport = a2.airport_id
-- JOIN seat s ON s.flight_id = f.flight_id
-- LEFT JOIN seat_assignment sa ON s.seat_id = sa.seat_id
-- WHERE a1.iata_code IN ('MHD', 'ABD')
--    OR a2.iata_code IN ('MHD', 'ABD')
-- ORDER BY f.flight_id, s.seat_code;










-- INSERT INTO flight (flight_number, origin_airport, destination_airport, scheduled_departure_time, scheduled_arrival_time, status, capacity) VALUES
-- (601, 27, 3421, '2026-08-20 08:00:00', '2026-08-20 12:30:00', 'scheduled', 300),
-- (602, 27, 3421, '2026-08-21 14:00:00', '2026-08-21 18:30:00', 'scheduled', 280),
-- (603, 27, 3421, '2026-08-22 21:00:00', '2026-08-23 01:30:00', 'scheduled', 300);

-- INSERT INTO flight_fare (flight_id, fare_class_id, price, currency, seats_available, sale_start, sale_end)
-- SELECT f.flight_id, fc.fare_class_id,
--     CASE fc.fare_class_id WHEN 1 THEN 349.99 WHEN 2 THEN 449.99 WHEN 3 THEN 1899.99 WHEN 4 THEN 899.99 WHEN 5 THEN 1499.99 END,
--     'GBP',
--     CASE fc.fare_class_id WHEN 1 THEN 120 WHEN 2 THEN 80 WHEN 3 THEN 20 WHEN 4 THEN 40 WHEN 5 THEN 30 END,
--     '2026-01-01', '2026-08-23'
-- FROM flight f
-- CROSS JOIN fare_class fc
-- WHERE f.flight_number IN (601, 602, 603);


-- INSERT INTO flight (flight_number, origin_airport, destination_airport, scheduled_departure_time, scheduled_arrival_time, status, capacity) VALUES
-- -- ABD -> MHD
-- (701, 27, 3421, '2026-08-10 06:00:00', '2026-08-10 10:30:00', 'scheduled', 300),
-- (702, 27, 3421, '2026-08-10 14:00:00', '2026-08-10 18:30:00', 'scheduled', 280),
-- (703, 27, 3421, '2026-08-11 08:00:00', '2026-08-11 12:30:00', 'scheduled', 300),
-- (704, 27, 3421, '2026-08-11 19:00:00', '2026-08-11 23:30:00', 'scheduled', 280),
-- (705, 27, 3421, '2026-08-12 07:00:00', '2026-08-12 11:30:00', 'scheduled', 300),
-- (706, 27, 3421, '2026-08-12 15:00:00', '2026-08-12 19:30:00', 'scheduled', 280),
-- (707, 27, 3421, '2026-08-12 22:00:00', '2026-08-13 02:30:00', 'scheduled', 300),
-- (708, 27, 3421, '2026-08-13 09:00:00', '2026-08-13 13:30:00', 'scheduled', 280),
-- (709, 27, 3421, '2026-08-13 17:00:00', '2026-08-13 21:30:00', 'scheduled', 300),
-- (710, 27, 3421, '2026-08-14 05:00:00', '2026-08-14 09:30:00', 'scheduled', 280),
-- (711, 27, 3421, '2026-08-14 13:00:00', '2026-08-14 17:30:00', 'scheduled', 300),
-- (712, 27, 3421, '2026-08-15 07:00:00', '2026-08-15 11:30:00', 'scheduled', 280),
-- (713, 27, 3421, '2026-08-15 16:00:00', '2026-08-15 20:30:00', 'scheduled', 300),
-- (714, 27, 3421, '2026-08-16 08:00:00', '2026-08-16 12:30:00', 'scheduled', 280),
-- (715, 27, 3421, '2026-08-16 20:00:00', '2026-08-17 00:30:00', 'scheduled', 300),
-- (716, 27, 3421, '2026-08-17 10:00:00', '2026-08-17 14:30:00', 'scheduled', 280),
-- (717, 27, 3421, '2026-08-17 22:00:00', '2026-08-18 02:30:00', 'scheduled', 300),
-- (718, 27, 3421, '2026-08-18 09:00:00', '2026-08-18 13:30:00', 'scheduled', 280),
-- (719, 27, 3421, '2026-08-18 17:00:00', '2026-08-18 21:30:00', 'scheduled', 300),
-- (720, 27, 3421, '2026-08-19 06:00:00', '2026-08-19 10:30:00', 'scheduled', 280),
-- -- MHD -> ABD
-- (721, 3421, 27, '2026-08-10 07:00:00', '2026-08-10 11:30:00', 'scheduled', 300),
-- (722, 3421, 27, '2026-08-10 16:00:00', '2026-08-10 20:30:00', 'scheduled', 280),
-- (723, 3421, 27, '2026-08-11 09:00:00', '2026-08-11 13:30:00', 'scheduled', 300),
-- (724, 3421, 27, '2026-08-11 21:00:00', '2026-08-12 01:30:00', 'scheduled', 280),
-- (725, 3421, 27, '2026-08-12 08:00:00', '2026-08-12 12:30:00', 'scheduled', 300),
-- (726, 3421, 27, '2026-08-12 17:00:00', '2026-08-12 21:30:00', 'scheduled', 280),
-- (727, 3421, 27, '2026-08-13 06:00:00', '2026-08-13 10:30:00', 'scheduled', 300),
-- (728, 3421, 27, '2026-08-13 14:00:00', '2026-08-13 18:30:00', 'scheduled', 280),
-- (729, 3421, 27, '2026-08-13 22:00:00', '2026-08-14 02:30:00', 'scheduled', 300),
-- (730, 3421, 27, '2026-08-14 07:00:00', '2026-08-14 11:30:00', 'scheduled', 280),
-- (731, 3421, 27, '2026-08-14 15:00:00', '2026-08-14 19:30:00', 'scheduled', 300),
-- (732, 3421, 27, '2026-08-15 08:00:00', '2026-08-15 12:30:00', 'scheduled', 280),
-- (733, 3421, 27, '2026-08-15 18:00:00', '2026-08-15 22:30:00', 'scheduled', 300),
-- (734, 3421, 27, '2026-08-16 09:00:00', '2026-08-16 13:30:00', 'scheduled', 280),
-- (735, 3421, 27, '2026-08-16 21:00:00', '2026-08-17 01:30:00', 'scheduled', 300),
-- (736, 3421, 27, '2026-08-17 11:00:00', '2026-08-17 15:30:00', 'scheduled', 280),
-- (737, 3421, 27, '2026-08-17 23:00:00', '2026-08-18 03:30:00', 'scheduled', 300),
-- (738, 3421, 27, '2026-08-18 10:00:00', '2026-08-18 14:30:00', 'scheduled', 280),
-- (739, 3421, 27, '2026-08-18 19:00:00', '2026-08-18 23:30:00', 'scheduled', 300),
-- (740, 3421, 27, '2026-08-19 07:00:00', '2026-08-19 11:30:00', 'scheduled', 280);

-- INSERT INTO flight_fare (flight_id, fare_class_id, price, currency, seats_available, sale_start, sale_end)
-- SELECT f.flight_id, fc.fare_class_id,
--     CASE fc.fare_class_id WHEN 1 THEN 349.99 WHEN 2 THEN 449.99 WHEN 3 THEN 1899.99 WHEN 4 THEN 899.99 WHEN 5 THEN 1499.99 END,
--     'GBP',
--     CASE fc.fare_class_id WHEN 1 THEN 120 WHEN 2 THEN 80 WHEN 3 THEN 20 WHEN 4 THEN 40 WHEN 5 THEN 30 END,
--     '2026-01-01', '2026-08-20'
-- FROM flight f
-- CROSS JOIN fare_class fc
-- WHERE f.flight_number BETWEEN 701 AND 740;






-- SELECT 
--     date(f.scheduled_departure_time) AS "Date",
--     o.name AS "Origin",
--     d.name AS "Destination",
--     COUNT(*) AS "Flights"
-- FROM flight f
-- JOIN airport o ON f.origin_airport = o.airport_id
-- JOIN airport d ON f.destination_airport = d.airport_id
-- WHERE f.scheduled_departure_time BETWEEN '2026-08-10' AND '2026-08-17'
-- GROUP BY date(f.scheduled_departure_time), o.airport_id, d.airport_id
-- ORDER BY date(f.scheduled_departure_time), o.name;

----------------------------------------
--            TABLE NAMES             --
----------------------------------------

-- airport
-- flight
-- fare_class
-- flight_fare
-- user
-- booking
-- payment
-- passenger
-- booking_segment
-- change_request
-- seat
-- seat_assignment
-- staff
-- complaint
-- notification


----------------------------------------
--       TABLE SQL CONSTRUCTION       --
----------------------------------------

-- CREATE TABLE "airport" (
--   "airport_id" INTEGER PRIMARY KEY,
--   "iata_code" TEXT UNIQUE NOT NULL,
--   "name" TEXT,
--   "city" TEXT,
--   "country" TEXT
-- );

-- CREATE TABLE "flight" (
--   "flight_id" INTEGER PRIMARY KEY,
--   "flight_number" INTEGER,
--   "origin_airport" INTEGER,
--   "destination_airport" INTEGER,
--   "scheduled_departure_time" TEXT,
--   "scheduled_arrival_time" TEXT,
--   "status" TEXT DEFAULT 'scheduled',
--   "capacity" INTEGER,
--   FOREIGN KEY ("origin_airport") REFERENCES "airport" ("airport_id"),
--   FOREIGN KEY ("destination_airport") REFERENCES "airport" ("airport_id")
-- );

-- CREATE TABLE "fare_class" (
--   "fare_class_id" INTEGER PRIMARY KEY,
--   "class_code" TEXT UNIQUE,
--   "cabin_class" TEXT,
--   "display_name" TEXT,
--   "refundable" INTEGER DEFAULT 0,
--   "cancel_protocol" TEXT DEFAULT 'free cancellation',
--   "advance_seat_selection" INTEGER DEFAULT 0,
--   "priority_checkin" INTEGER DEFAULT 0,
--   "priority_boarding" INTEGER DEFAULT 0,
--   "lounge_access" INTEGER DEFAULT 0,
--   "carry_on_allowed" INTEGER DEFAULT 1,
--   "carry_on_weight_kg" INTEGER DEFAULT 7,
--   "checked_baggage_pieces" INTEGER DEFAULT 0,
--   "checked_baggage_weight_kg" INTEGER DEFAULT 0,
--   "miles_earn_rate" REAL DEFAULT 1.0,
--   "minimum_miles_for_booking" INTEGER,
--   "description" TEXT,
--   "created_at" TEXT DEFAULT (datetime('now')),
--   "updated_at" TEXT DEFAULT (datetime('now'))
-- );

-- CREATE TABLE "flight_fare" (
--   "flight_fare_id" INTEGER PRIMARY KEY,
--   "flight_id" INTEGER NOT NULL,
--   "fare_class_id" INTEGER NOT NULL,
--   "price" REAL NOT NULL,
--   "currency" TEXT DEFAULT 'GBP',
--   "seats_available" INTEGER NOT NULL,
--   "sale_start" TEXT,
--   "sale_end" TEXT,
--   FOREIGN KEY ("flight_id") REFERENCES "flight" ("flight_id"),
--   FOREIGN KEY ("fare_class_id") REFERENCES "fare_class" ("fare_class_id")
-- );

-- CREATE TABLE "user" (
--   "user_id" INTEGER PRIMARY KEY,
--   "email" TEXT UNIQUE,
--   "password_hash" TEXT,
--   "first_name" TEXT,
--   "last_name" TEXT,
--   "phone_number" TEXT,
--   "date_of_birth" TEXT,
--   "created_at" TEXT DEFAULT (datetime('now')),
--   "account_status" TEXT DEFAULT 'active'
-- );

-- CREATE TABLE "booking" (
--   "booking_id" INTEGER PRIMARY KEY,
--   "user_id" INTEGER,
--   "booking_reference" TEXT UNIQUE,
--   "payment_id" INTEGER UNIQUE,
--   "created_at" TEXT DEFAULT (datetime('now')),
--   "booking_status" TEXT DEFAULT 'pending',
--   "cancelled_at" TEXT,
--   "amendable" INTEGER DEFAULT 1,
--   FOREIGN KEY ("user_id") REFERENCES "user" ("user_id")
-- );

-- CREATE TABLE "payment" (
--   "payment_id" INTEGER PRIMARY KEY,
--   "booking_id" INTEGER UNIQUE,
--   "amount" REAL,
--   "payment_method" TEXT,
--   "payment_status" TEXT DEFAULT 'pending',
--   "paid_at" TEXT,
--   "provider_reference" TEXT,
--   "currency" TEXT DEFAULT 'GBP',
--   FOREIGN KEY ("booking_id") REFERENCES "booking" ("booking_id")
-- );

-- CREATE TABLE "passenger" (
--   "passenger_id" INTEGER PRIMARY KEY,
--   "booking_id" INTEGER,
--   "email" TEXT,
--   "checked_in" INTEGER DEFAULT 0,
--   "title" TEXT,
--   "first_name" TEXT,
--   "last_name" TEXT,
--   "date_of_birth" TEXT,
--   "gender" TEXT,
--   "nationality" TEXT,
--   "document_type" TEXT,
--   "document_number" TEXT,
--   "document_country" TEXT,
--   "document_expiry" TEXT,
--   FOREIGN KEY ("booking_id") REFERENCES "booking" ("booking_id")
-- );

-- CREATE TABLE "booking_segment" (
--   "booking_segment_id" INTEGER PRIMARY KEY,
--   "booking_id" INTEGER NOT NULL,
--   "flight_id" INTEGER NOT NULL,
--   "flight_fare_id" INTEGER NOT NULL,
--   FOREIGN KEY ("booking_id") REFERENCES "booking" ("booking_id"),
--   FOREIGN KEY ("flight_id") REFERENCES "flight" ("flight_id"),
--   FOREIGN KEY ("flight_fare_id") REFERENCES "flight_fare" ("flight_fare_id")
-- );

-- CREATE TABLE "seat" (
--   "seat_id" INTEGER PRIMARY KEY,
--   "flight_id" INTEGER NOT NULL,
--   "seat_code" TEXT NOT NULL,
--   "cabin_class" TEXT,
--   "position" TEXT,
--   "extra_legroom" INTEGER DEFAULT 0,
--   "exit_row" INTEGER DEFAULT 0,
--   "reduced_mobility" INTEGER DEFAULT 0,
--   "status" TEXT DEFAULT 'available',
--   FOREIGN KEY ("flight_id") REFERENCES "flight" ("flight_id")
-- );

-- CREATE TABLE "seat_assignment" (
--   "seat_assignment_id" INTEGER PRIMARY KEY,
--   "passenger_id" INTEGER UNIQUE,
--   "booking_segment_id" INTEGER UNIQUE,
--   "seat_id" INTEGER,
--   FOREIGN KEY ("passenger_id") REFERENCES "passenger" ("passenger_id"),
--   FOREIGN KEY ("booking_segment_id") REFERENCES "booking_segment" ("booking_segment_id"),
--   FOREIGN KEY ("seat_id") REFERENCES "seat" ("seat_id")
-- );

-- CREATE TABLE "change_request" (
--   "change_request_id" INTEGER PRIMARY KEY,
--   "user_id" INTEGER NOT NULL,
--   "booking_id" INTEGER NOT NULL,
--   "booking_segment_id" INTEGER NOT NULL,
--   "current_flight_id" INTEGER,
--   "requested_flight_id" INTEGER,
--   "requested_seat_id" INTEGER,
--   "reason" TEXT,
--   "status" TEXT DEFAULT 'pending',         -- pending / approved / rejected / cancelled
--   "created_at" TEXT DEFAULT (datetime('now')),
--   "updated_at" TEXT DEFAULT (datetime('now')),
--   FOREIGN KEY ("user_id") REFERENCES "user" ("user_id"),
--   FOREIGN KEY ("booking_id") REFERENCES "booking" ("booking_id"),
--   FOREIGN KEY ("booking_segment_id") REFERENCES "booking_segment" ("booking_segment_id"),
--   FOREIGN KEY ("current_flight_id") REFERENCES "flight" ("flight_id"),
--   FOREIGN KEY ("requested_flight_id") REFERENCES "flight" ("flight_id"),
--   FOREIGN KEY ("requested_seat_id") REFERENCES "seat" ("seat_id")
-- );

-- CREATE TABLE "staff" (
--   "staff_id" INTEGER PRIMARY KEY,
--   "email" TEXT UNIQUE,
--   "password_hash" TEXT,
--   "first_name" TEXT,
--   "last_name" TEXT,
--   "phone_number" TEXT,
--   "role" TEXT,
--   "created_at" TEXT DEFAULT (datetime('now'))
-- );

-- CREATE TABLE "complaint" (
--   "complaint_id" INTEGER PRIMARY KEY,
--   "user_id" INTEGER,
--   "type" TEXT,
--   "message" TEXT,
--   "created_at" TEXT DEFAULT (datetime('now')),
--   "status" TEXT DEFAULT 'open',
--   "handled_by_staff_id" INTEGER,
--   FOREIGN KEY ("user_id") REFERENCES "user" ("user_id"),
--   FOREIGN KEY ("handled_by_staff_id") REFERENCES "staff" ("staff_id")
-- );

-- CREATE TABLE "notification" (
--   "notification_id" INTEGER PRIMARY KEY,
--   "user_id" INTEGER,
--   "type" TEXT,
--   "message" TEXT,
--   "created_at" TEXT DEFAULT (datetime('now')),
--   "read_at" TEXT,
--   FOREIGN KEY ("user_id") REFERENCES "user" ("user_id")
-- );


----------------------------------------
--       INPUT SOME RANDOM DATA       --
----------------------------------------

-- GENERATED THIS DATA USING CLAUDE BECAUSE I AM LAZY

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-13 18:00:00'
-- WHERE flight_id = 9641; -- 202 DXB->LHR 07:00 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-13 22:30:00'
-- WHERE flight_id = 9642; -- 204 DXB->LHR 11:30 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-14 02:00:00'
-- WHERE flight_id = 9643; -- 206 DXB->LHR 15:00 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-14 06:30:00'
-- WHERE flight_id = 9644; -- 208 DXB->LHR 19:30 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-14 10:00:00'
-- WHERE flight_id = 9645; -- 210 DXB->LHR 23:00 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-14 17:30:00'
-- WHERE flight_id = 9646; -- 212 DXB->LHR 06:30 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-14 22:00:00'
-- WHERE flight_id = 9647; -- 214 DXB->LHR 11:00 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-15 02:30:00'
-- WHERE flight_id = 9648; -- 216 DXB->LHR 15:30 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-15 07:00:00'
-- WHERE flight_id = 9649; -- 218 DXB->LHR 20:00 departure

-- UPDATE flight SET
--     scheduled_arrival_time = '2026-08-15 10:30:00'
-- WHERE flight_id = 9650; -- 220 DXB->LHR 23:30 departure

-- 20 new flights (10 LHR->DXB, 10 DXB->LHR)
-- INSERT INTO flight (flight_number, origin_airport, destination_airport, scheduled_departure_time, scheduled_arrival_time, status, capacity) VALUES
-- -- LHR -> DXB
-- (201, 1, 5, '2026-08-13 06:00:00', '2026-08-13 17:00:00', 'scheduled', 350),
-- (203, 1, 5, '2026-08-13 09:30:00', '2026-08-13 20:30:00', 'scheduled', 280),
-- (205, 1, 5, '2026-08-13 13:00:00', '2026-08-14 00:00:00', 'scheduled', 350),
-- (207, 1, 5, '2026-08-13 17:45:00', '2026-08-14 04:45:00', 'scheduled', 280),
-- (209, 1, 5, '2026-08-13 22:00:00', '2026-08-14 09:00:00', 'scheduled', 350),
-- (211, 1, 5, '2026-08-14 05:30:00', '2026-08-14 16:30:00', 'scheduled', 350),
-- (213, 1, 5, '2026-08-14 10:00:00', '2026-08-14 21:00:00', 'scheduled', 280),
-- (215, 1, 5, '2026-08-14 14:30:00', '2026-08-15 01:30:00', 'scheduled', 350),
-- (217, 1, 5, '2026-08-14 18:00:00', '2026-08-15 05:00:00', 'scheduled', 280),
-- (219, 1, 5, '2026-08-14 23:00:00', '2026-08-15 10:00:00', 'scheduled', 350),
-- -- DXB -> LHR
-- (202, 5, 1, '2026-08-13 07:00:00', '2026-08-13 11:00:00', 'scheduled', 350),
-- (204, 5, 1, '2026-08-13 11:30:00', '2026-08-13 15:30:00', 'scheduled', 280),
-- (206, 5, 1, '2026-08-13 15:00:00', '2026-08-13 19:00:00', 'scheduled', 350),
-- (208, 5, 1, '2026-08-13 19:30:00', '2026-08-13 23:30:00', 'scheduled', 280),
-- (210, 5, 1, '2026-08-13 23:00:00', '2026-08-14 03:00:00', 'scheduled', 350),
-- (212, 5, 1, '2026-08-14 06:30:00', '2026-08-14 10:30:00', 'scheduled', 350),
-- (214, 5, 1, '2026-08-14 11:00:00', '2026-08-14 15:00:00', 'scheduled', 280),
-- (216, 5, 1, '2026-08-14 15:30:00', '2026-08-14 19:30:00', 'scheduled', 350),
-- (218, 5, 1, '2026-08-14 20:00:00', '2026-08-15 00:00:00', 'scheduled', 280),
-- (220, 5, 1, '2026-08-14 23:30:00', '2026-08-15 03:30:00', 'scheduled', 350);

-- -- fares for all 20 flights (flight_ids 9631-9650, fare_class_ids 1-6)
-- INSERT INTO flight_fare (flight_id, fare_class_id, price, currency, seats_available, sale_start, sale_end) VALUES
-- -- flight 9631 (201 LHR->DXB)
-- (9631, 1, 379.99, 'GBP', 95, '2026-03-21', '2026-08-13'),
-- (9631, 2, 469.99, 'GBP', 72, '2026-03-21', '2026-08-13'),
-- (9631, 3, 629.99, 'GBP', 48, '2026-03-21', '2026-08-13'),
-- (9631, 4, 989.99, 'GBP', 28, '2026-03-21', '2026-08-13'),
-- (9631, 5, 1849.99, 'GBP', 14, '2026-03-21', '2026-08-13'),
-- (9631, 6, 4699.99, 'GBP', 6, '2026-03-21', '2026-08-13'),
-- -- flight 9632 (203 LHR->DXB)
-- (9632, 1, 354.99, 'GBP', 110, '2026-03-21', '2026-08-13'),
-- (9632, 2, 449.99, 'GBP', 65, '2026-03-21', '2026-08-13'),
-- (9632, 3, 609.99, 'GBP', 52, '2026-03-21', '2026-08-13'),
-- (9632, 4, 959.99, 'GBP', 24, '2026-03-21', '2026-08-13'),
-- (9632, 5, 1799.99, 'GBP', 12, '2026-03-21', '2026-08-13'),
-- (9632, 6, 4499.99, 'GBP', 4, '2026-03-21', '2026-08-13'),
-- -- flight 9633 (205 LHR->DXB)
-- (9633, 1, 399.99, 'GBP', 88, '2026-03-21', '2026-08-13'),
-- (9633, 2, 489.99, 'GBP', 70, '2026-03-21', '2026-08-13'),
-- (9633, 3, 649.99, 'GBP', 44, '2026-03-21', '2026-08-13'),
-- (9633, 4, 1009.99, 'GBP', 22, '2026-03-21', '2026-08-13'),
-- (9633, 5, 1899.99, 'GBP', 10, '2026-03-21', '2026-08-13'),
-- (9633, 6, 4799.99, 'GBP', 5, '2026-03-21', '2026-08-13'),
-- -- flight 9634 (207 LHR->DXB)
-- (9634, 1, 369.99, 'GBP', 102, '2026-03-21', '2026-08-13'),
-- (9634, 2, 459.99, 'GBP', 68, '2026-03-21', '2026-08-13'),
-- (9634, 3, 619.99, 'GBP', 50, '2026-03-21', '2026-08-13'),
-- (9634, 4, 969.99, 'GBP', 26, '2026-03-21', '2026-08-13'),
-- (9634, 5, 1829.99, 'GBP', 13, '2026-03-21', '2026-08-13'),
-- (9634, 6, 4599.99, 'GBP', 5, '2026-03-21', '2026-08-13'),
-- -- flight 9635 (209 LHR->DXB)
-- (9635, 1, 389.99, 'GBP', 93, '2026-03-21', '2026-08-13'),
-- (9635, 2, 479.99, 'GBP', 74, '2026-03-21', '2026-08-13'),
-- (9635, 3, 639.99, 'GBP', 46, '2026-03-21', '2026-08-13'),
-- (9635, 4, 999.99, 'GBP', 25, '2026-03-21', '2026-08-13'),
-- (9635, 5, 1869.99, 'GBP', 11, '2026-03-21', '2026-08-13'),
-- (9635, 6, 4749.99, 'GBP', 6, '2026-03-21', '2026-08-13'),
-- -- flight 9636 (211 LHR->DXB)
-- (9636, 1, 344.99, 'GBP', 115, '2026-03-21', '2026-08-14'),
-- (9636, 2, 439.99, 'GBP', 62, '2026-03-21', '2026-08-14'),
-- (9636, 3, 599.99, 'GBP', 55, '2026-03-21', '2026-08-14'),
-- (9636, 4, 949.99, 'GBP', 30, '2026-03-21', '2026-08-14'),
-- (9636, 5, 1779.99, 'GBP', 15, '2026-03-21', '2026-08-14'),
-- (9636, 6, 4449.99, 'GBP', 7, '2026-03-21', '2026-08-14'),
-- -- flight 9637 (213 LHR->DXB)
-- (9637, 1, 409.99, 'GBP', 85, '2026-03-21', '2026-08-14'),
-- (9637, 2, 499.99, 'GBP', 66, '2026-03-21', '2026-08-14'),
-- (9637, 3, 659.99, 'GBP', 42, '2026-03-21', '2026-08-14'),
-- (9637, 4, 1019.99, 'GBP', 20, '2026-03-21', '2026-08-14'),
-- (9637, 5, 1919.99, 'GBP', 9, '2026-03-21', '2026-08-14'),
-- (9637, 6, 4849.99, 'GBP', 4, '2026-03-21', '2026-08-14'),
-- -- flight 9638 (215 LHR->DXB)
-- (9638, 1, 359.99, 'GBP', 98, '2026-03-21', '2026-08-14'),
-- (9638, 2, 454.99, 'GBP', 71, '2026-03-21', '2026-08-14'),
-- (9638, 3, 614.99, 'GBP', 49, '2026-03-21', '2026-08-14'),
-- (9638, 4, 964.99, 'GBP', 27, '2026-03-21', '2026-08-14'),
-- (9638, 5, 1814.99, 'GBP', 12, '2026-03-21', '2026-08-14'),
-- (9638, 6, 4549.99, 'GBP', 5, '2026-03-21', '2026-08-14'),
-- -- flight 9639 (217 LHR->DXB)
-- (9639, 1, 394.99, 'GBP', 91, '2026-03-21', '2026-08-14'),
-- (9639, 2, 484.99, 'GBP', 73, '2026-03-21', '2026-08-14'),
-- (9639, 3, 644.99, 'GBP', 45, '2026-03-21', '2026-08-14'),
-- (9639, 4, 994.99, 'GBP', 23, '2026-03-21', '2026-08-14'),
-- (9639, 5, 1859.99, 'GBP', 11, '2026-03-21', '2026-08-14'),
-- (9639, 6, 4649.99, 'GBP', 6, '2026-03-21', '2026-08-14'),
-- -- flight 9640 (219 LHR->DXB)
-- (9640, 1, 374.99, 'GBP', 100, '2026-03-21', '2026-08-14'),
-- (9640, 2, 464.99, 'GBP', 69, '2026-03-21', '2026-08-14'),
-- (9640, 3, 624.99, 'GBP', 47, '2026-03-21', '2026-08-14'),
-- (9640, 4, 974.99, 'GBP', 26, '2026-03-21', '2026-08-14'),
-- (9640, 5, 1839.99, 'GBP', 13, '2026-03-21', '2026-08-14'),
-- (9640, 6, 4624.99, 'GBP', 5, '2026-03-21', '2026-08-14'),
-- -- flight 9641 (202 DXB->LHR)
-- (9641, 1, 369.99, 'GBP', 97, '2026-03-21', '2026-08-13'),
-- (9641, 2, 459.99, 'GBP', 70, '2026-03-21', '2026-08-13'),
-- (9641, 3, 619.99, 'GBP', 47, '2026-03-21', '2026-08-13'),
-- (9641, 4, 979.99, 'GBP', 25, '2026-03-21', '2026-08-13'),
-- (9641, 5, 1849.99, 'GBP', 12, '2026-03-21', '2026-08-13'),
-- (9641, 6, 4649.99, 'GBP', 5, '2026-03-21', '2026-08-13'),
-- -- flight 9642 (204 DXB->LHR)
-- (9642, 1, 349.99, 'GBP', 112, '2026-03-21', '2026-08-13'),
-- (9642, 2, 439.99, 'GBP', 64, '2026-03-21', '2026-08-13'),
-- (9642, 3, 599.99, 'GBP', 56, '2026-03-21', '2026-08-13'),
-- (9642, 4, 959.99, 'GBP', 28, '2026-03-21', '2026-08-13'),
-- (9642, 5, 1799.99, 'GBP', 14, '2026-03-21', '2026-08-13'),
-- (9642, 6, 4499.99, 'GBP', 6, '2026-03-21', '2026-08-13'),
-- -- flight 9643 (206 DXB->LHR)
-- (9643, 1, 389.99, 'GBP', 90, '2026-03-21', '2026-08-13'),
-- (9643, 2, 479.99, 'GBP', 72, '2026-03-21', '2026-08-13'),
-- (9643, 3, 639.99, 'GBP', 44, '2026-03-21', '2026-08-13'),
-- (9643, 4, 999.99, 'GBP', 22, '2026-03-21', '2026-08-13'),
-- (9643, 5, 1879.99, 'GBP', 11, '2026-03-21', '2026-08-13'),
-- (9643, 6, 4749.99, 'GBP', 5, '2026-03-21', '2026-08-13'),
-- -- flight 9644 (208 DXB->LHR)
-- (9644, 1, 374.99, 'GBP', 103, '2026-03-21', '2026-08-13'),
-- (9644, 2, 464.99, 'GBP', 68, '2026-03-21', '2026-08-13'),
-- (9644, 3, 624.99, 'GBP', 48, '2026-03-21', '2026-08-13'),
-- (9644, 4, 984.99, 'GBP', 26, '2026-03-21', '2026-08-13'),
-- (9644, 5, 1834.99, 'GBP', 13, '2026-03-21', '2026-08-13'),
-- (9644, 6, 4574.99, 'GBP', 5, '2026-03-21', '2026-08-13'),
-- -- flight 9645 (210 DXB->LHR)
-- (9645, 1, 359.99, 'GBP', 107, '2026-03-21', '2026-08-13'),
-- (9645, 2, 449.99, 'GBP', 61, '2026-03-21', '2026-08-13'),
-- (9645, 3, 609.99, 'GBP', 53, '2026-03-21', '2026-08-13'),
-- (9645, 4, 969.99, 'GBP', 31, '2026-03-21', '2026-08-13'),
-- (9645, 5, 1809.99, 'GBP', 15, '2026-03-21', '2026-08-13'),
-- (9645, 6, 4524.99, 'GBP', 7, '2026-03-21', '2026-08-13'),
-- -- flight 9646 (212 DXB->LHR)
-- (9646, 1, 394.99, 'GBP', 89, '2026-03-21', '2026-08-14'),
-- (9646, 2, 484.99, 'GBP', 73, '2026-03-21', '2026-08-14'),
-- (9646, 3, 644.99, 'GBP', 43, '2026-03-21', '2026-08-14'),
-- (9646, 4, 1004.99, 'GBP', 21, '2026-03-21', '2026-08-14'),
-- (9646, 5, 1894.99, 'GBP', 10, '2026-03-21', '2026-08-14'),
-- (9646, 6, 4774.99, 'GBP', 4, '2026-03-21', '2026-08-14'),
-- -- flight 9647 (214 DXB->LHR)
-- (9647, 1, 379.99, 'GBP', 99, '2026-03-21', '2026-08-14'),
-- (9647, 2, 469.99, 'GBP', 71, '2026-03-21', '2026-08-14'),
-- (9647, 3, 629.99, 'GBP', 46, '2026-03-21', '2026-08-14'),
-- (9647, 4, 989.99, 'GBP', 24, '2026-03-21', '2026-08-14'),
-- (9647, 5, 1859.99, 'GBP', 12, '2026-03-21', '2026-08-14'),
-- (9647, 6, 4599.99, 'GBP', 5, '2026-03-21', '2026-08-14'),
-- -- flight 9648 (216 DXB->LHR)
-- (9648, 1, 344.99, 'GBP', 116, '2026-03-21', '2026-08-14'),
-- (9648, 2, 434.99, 'GBP', 60, '2026-03-21', '2026-08-14'),
-- (9648, 3, 594.99, 'GBP', 57, '2026-03-21', '2026-08-14'),
-- (9648, 4, 944.99, 'GBP', 33, '2026-03-21', '2026-08-14'),
-- (9648, 5, 1784.99, 'GBP', 16, '2026-03-21', '2026-08-14'),
-- (9648, 6, 4449.99, 'GBP', 8, '2026-03-21', '2026-08-14'),
-- -- flight 9649 (218 DXB->LHR)
-- (9649, 1, 404.99, 'GBP', 86, '2026-03-21', '2026-08-14'),
-- (9649, 2, 494.99, 'GBP', 65, '2026-03-21', '2026-08-14'),
-- (9649, 3, 654.99, 'GBP', 41, '2026-03-21', '2026-08-14'),
-- (9649, 4, 1014.99, 'GBP', 19, '2026-03-21', '2026-08-14'),
-- (9649, 5, 1914.99, 'GBP', 9, '2026-03-21', '2026-08-14'),
-- (9649, 6, 4824.99, 'GBP', 4, '2026-03-21', '2026-08-14'),
-- -- flight 9650 (220 DXB->LHR)
-- (9650, 1, 384.99, 'GBP', 94, '2026-03-21', '2026-08-14'),
-- (9650, 2, 474.99, 'GBP', 74, '2026-03-21', '2026-08-14'),
-- (9650, 3, 634.99, 'GBP', 50, '2026-03-21', '2026-08-14'),
-- (9650, 4, 994.99, 'GBP', 27, '2026-03-21', '2026-08-14'),
-- (9650, 5, 1874.99, 'GBP', 13, '2026-03-21', '2026-08-14'),
-- (9650, 6, 4674.99, 'GBP', 5, '2026-03-21', '2026-08-14');

-- airports
-- INSERT INTO airport (iata_code, name, city, country) VALUES
--     ('LHR', 'Heathrow Airport', 'London', 'United Kingdom'),
--     ('DXB', 'Dubai International Airport', 'Dubai', 'United Arab Emirates');

-- -- fare classes
-- INSERT INTO fare_class (
--     class_code, cabin_class, display_name, refundable, cancel_protocol,
--     advance_seat_selection, priority_checkin, priority_boarding, lounge_access,
--     carry_on_allowed, carry_on_weight_kg, checked_baggage_pieces, checked_baggage_weight_kg,
--     miles_earn_rate, description, created_at, updated_at
-- ) VALUES
--     ('Y', 'economy', 'Economy Lite', 0, 'no cancellation', 0, 0, 0, 0, 1, 7, 0, 0, 0.5, 'Basic economy fare. No changes or cancellations.', '2025-03-21', '2025-03-21'),
--     ('M', 'economy', 'Economy Standard', 0, 'fee applies', 1, 0, 0, 0, 1, 7, 1, 23, 1.0, 'Standard economy with one checked bag.', '2025-03-21', '2025-03-21'),
--     ('W', 'economy', 'Economy Flex', 1, 'free cancellation', 1, 0, 1, 0, 1, 10, 1, 23, 1.25, 'Flexible economy. Free cancellation and seat selection.', '2025-03-21', '2025-03-21'),
--     ('P', 'premium_economy', 'Premium Economy', 1, 'free cancellation', 1, 1, 1, 0, 1, 10, 2, 23, 1.5, 'Premium economy with extra legroom and priority boarding.', '2025-03-21', '2025-03-21'),
--     ('C', 'business', 'Business Class', 1, 'free cancellation', 1, 1, 1, 1, 1, 15, 2, 32, 2.0, 'Business class with lounge access and lie-flat seats.', '2025-03-21', '2025-03-21'),
--     ('F', 'first', 'First Class', 1, 'free cancellation', 1, 1, 1, 1, 1, 15, 3, 32, 3.0, 'First class with private suite and dedicated concierge.', '2025-03-21', '2025-03-21');

-- -- LHR -> DXB flights
-- INSERT INTO flight (flight_number, origin_airport, destination_airport, scheduled_departure_time, scheduled_arrival_time, status, capacity) VALUES
--     (101, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-22 06:00:00', '2025-03-22 17:00:00', 'scheduled', 350),
--     (103, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-22 14:00:00', '2025-03-23 01:00:00', 'scheduled', 350),
--     (105, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-23 08:30:00', '2025-03-23 19:30:00', 'scheduled', 280),
--     (107, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-24 11:00:00', '2025-03-24 22:00:00', 'scheduled', 350),
--     (109, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-25 07:00:00', '2025-03-25 18:00:00', 'scheduled', 280),
--     (111, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-26 15:30:00', '2025-03-27 02:30:00', 'scheduled', 350),
--     (113, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-27 06:00:00', '2025-03-27 17:00:00', 'scheduled', 350),
--     (115, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-28 09:00:00', '2025-03-28 20:00:00', 'scheduled', 280),
--     (117, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-29 13:00:00', '2025-03-30 00:00:00', 'scheduled', 350),
--     (119, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-30 07:30:00', '2025-03-30 18:30:00', 'scheduled', 350),
--     (121, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-03-31 06:00:00', '2025-03-31 17:00:00', 'scheduled', 280),
--     (123, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-04-01 14:00:00', '2025-04-02 01:00:00', 'scheduled', 350),
--     (125, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-04-02 08:00:00', '2025-04-02 19:00:00', 'scheduled', 350),
--     (127, (SELECT airport_id FROM airport WHERE iata_code='LHR'), (SELECT airport_id FROM airport WHERE iata_code='DXB'), '2025-04-03 11:30:00', '2025-04-03 22:30:00', 'scheduled', 280);

-- -- DXB -> LHR flights
-- INSERT INTO flight (flight_number, origin_airport, destination_airport, scheduled_departure_time, scheduled_arrival_time, status, capacity) VALUES
--     (102, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-22 20:00:00', '2025-03-23 00:00:00', 'scheduled', 350),
--     (104, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-23 03:00:00', '2025-03-23 07:00:00', 'scheduled', 350),
--     (106, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-24 09:00:00', '2025-03-24 13:00:00', 'scheduled', 280),
--     (108, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-25 14:00:00', '2025-03-25 18:00:00', 'scheduled', 350),
--     (110, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-26 22:00:00', '2025-03-27 02:00:00', 'scheduled', 280),
--     (112, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-27 07:30:00', '2025-03-27 11:30:00', 'scheduled', 350),
--     (114, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-28 16:00:00', '2025-03-28 20:00:00', 'scheduled', 350),
--     (116, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-29 02:00:00', '2025-03-29 06:00:00', 'scheduled', 280),
--     (118, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-30 11:00:00', '2025-03-30 15:00:00', 'scheduled', 350),
--     (120, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-03-31 19:00:00', '2025-03-31 23:00:00', 'scheduled', 350),
--     (122, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-04-01 05:00:00', '2025-04-01 09:00:00', 'scheduled', 280),
--     (124, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-04-02 13:00:00', '2025-04-02 17:00:00', 'scheduled', 350),
--     (126, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-04-03 21:00:00', '2025-04-04 01:00:00', 'scheduled', 350),
--     (128, (SELECT airport_id FROM airport WHERE iata_code='DXB'), (SELECT airport_id FROM airport WHERE iata_code='LHR'), '2025-04-04 08:00:00', '2025-04-04 12:00:00', 'scheduled', 280);

-- -- flight fares — SQLite has no RANDOM() cross join trick so we insert explicitly
-- -- prices are fixed but varied per class to simulate realistic pricing
-- INSERT INTO flight_fare (flight_id, fare_class_id, price, currency, seats_available, sale_start, sale_end) VALUES
-- -- flight 1 (101 LHR->DXB)
-- (1, 1, 379.99, 'GBP', 95, '2025-03-21', '2025-04-03'),
-- (1, 2, 469.99, 'GBP', 72, '2025-03-21', '2025-04-03'),
-- (1, 3, 629.99, 'GBP', 48, '2025-03-21', '2025-04-03'),
-- (1, 4, 989.99, 'GBP', 28, '2025-03-21', '2025-04-03'),
-- (1, 5, 1849.99, 'GBP', 14, '2025-03-21', '2025-04-03'),
-- (1, 6, 4699.99, 'GBP', 6, '2025-03-21', '2025-04-03'),
-- -- flight 2 (103 LHR->DXB)
-- (2, 1, 354.99, 'GBP', 110, '2025-03-21', '2025-04-03'),
-- (2, 2, 449.99, 'GBP', 65, '2025-03-21', '2025-04-03'),
-- (2, 3, 609.99, 'GBP', 52, '2025-03-21', '2025-04-03'),
-- (2, 4, 959.99, 'GBP', 24, '2025-03-21', '2025-04-03'),
-- (2, 5, 1799.99, 'GBP', 12, '2025-03-21', '2025-04-03'),
-- (2, 6, 4499.99, 'GBP', 4, '2025-03-21', '2025-04-03'),
-- -- flight 3 (105 LHR->DXB)
-- (3, 1, 399.99, 'GBP', 88, '2025-03-21', '2025-04-03'),
-- (3, 2, 489.99, 'GBP', 70, '2025-03-21', '2025-04-03'),
-- (3, 3, 649.99, 'GBP', 44, '2025-03-21', '2025-04-03'),
-- (3, 4, 1009.99, 'GBP', 22, '2025-03-21', '2025-04-03'),
-- (3, 5, 1899.99, 'GBP', 10, '2025-03-21', '2025-04-03'),
-- (3, 6, 4799.99, 'GBP', 5, '2025-03-21', '2025-04-03'),
-- -- flight 4 (107 LHR->DXB)
-- (4, 1, 369.99, 'GBP', 102, '2025-03-21', '2025-04-03'),
-- (4, 2, 459.99, 'GBP', 68, '2025-03-21', '2025-04-03'),
-- (4, 3, 619.99, 'GBP', 50, '2025-03-21', '2025-04-03'),
-- (4, 4, 969.99, 'GBP', 26, '2025-03-21', '2025-04-03'),
-- (4, 5, 1829.99, 'GBP', 13, '2025-03-21', '2025-04-03'),
-- (4, 6, 4599.99, 'GBP', 5, '2025-03-21', '2025-04-03'),
-- -- flight 5 (109 LHR->DXB)
-- (5, 1, 389.99, 'GBP', 93, '2025-03-21', '2025-04-03'),
-- (5, 2, 479.99, 'GBP', 74, '2025-03-21', '2025-04-03'),
-- (5, 3, 639.99, 'GBP', 46, '2025-03-21', '2025-04-03'),
-- (5, 4, 999.99, 'GBP', 25, '2025-03-21', '2025-04-03'),
-- (5, 5, 1869.99, 'GBP', 11, '2025-03-21', '2025-04-03'),
-- (5, 6, 4749.99, 'GBP', 6, '2025-03-21', '2025-04-03'),
-- -- flight 6 (111 LHR->DXB)
-- (6, 1, 344.99, 'GBP', 115, '2025-03-21', '2025-04-03'),
-- (6, 2, 439.99, 'GBP', 62, '2025-03-21', '2025-04-03'),
-- (6, 3, 599.99, 'GBP', 55, '2025-03-21', '2025-04-03'),
-- (6, 4, 949.99, 'GBP', 30, '2025-03-21', '2025-04-03'),
-- (6, 5, 1779.99, 'GBP', 15, '2025-03-21', '2025-04-03'),
-- (6, 6, 4449.99, 'GBP', 7, '2025-03-21', '2025-04-03'),
-- -- flight 7 (113 LHR->DXB)
-- (7, 1, 409.99, 'GBP', 85, '2025-03-21', '2025-04-03'),
-- (7, 2, 499.99, 'GBP', 66, '2025-03-21', '2025-04-03'),
-- (7, 3, 659.99, 'GBP', 42, '2025-03-21', '2025-04-03'),
-- (7, 4, 1019.99, 'GBP', 20, '2025-03-21', '2025-04-03'),
-- (7, 5, 1919.99, 'GBP', 9, '2025-03-21', '2025-04-03'),
-- (7, 6, 4849.99, 'GBP', 4, '2025-03-21', '2025-04-03'),
-- -- flight 8 (115 LHR->DXB)
-- (8, 1, 359.99, 'GBP', 98, '2025-03-21', '2025-04-03'),
-- (8, 2, 454.99, 'GBP', 71, '2025-03-21', '2025-04-03'),
-- (8, 3, 614.99, 'GBP', 49, '2025-03-21', '2025-04-03'),
-- (8, 4, 964.99, 'GBP', 27, '2025-03-21', '2025-04-03'),
-- (8, 5, 1814.99, 'GBP', 12, '2025-03-21', '2025-04-03'),
-- (8, 6, 4549.99, 'GBP', 5, '2025-03-21', '2025-04-03'),
-- -- flight 9 (117 LHR->DXB)
-- (9, 1, 394.99, 'GBP', 91, '2025-03-21', '2025-04-03'),
-- (9, 2, 484.99, 'GBP', 73, '2025-03-21', '2025-04-03'),
-- (9, 3, 644.99, 'GBP', 45, '2025-03-21', '2025-04-03'),
-- (9, 4, 994.99, 'GBP', 23, '2025-03-21', '2025-04-03'),
-- (9, 5, 1859.99, 'GBP', 11, '2025-03-21', '2025-04-03'),
-- (9, 6, 4649.99, 'GBP', 6, '2025-03-21', '2025-04-03'),
-- -- flight 10 (119 LHR->DXB)
-- (10, 1, 374.99, 'GBP', 100, '2025-03-21', '2025-04-03'),
-- (10, 2, 464.99, 'GBP', 69, '2025-03-21', '2025-04-03'),
-- (10, 3, 624.99, 'GBP', 47, '2025-03-21', '2025-04-03'),
-- (10, 4, 974.99, 'GBP', 26, '2025-03-21', '2025-04-03'),
-- (10, 5, 1839.99, 'GBP', 13, '2025-03-21', '2025-04-03'),
-- (10, 6, 4624.99, 'GBP', 5, '2025-03-21', '2025-04-03'),
-- -- flight 11 (121 LHR->DXB)
-- (11, 1, 384.99, 'GBP', 96, '2025-03-21', '2025-04-03'),
-- (11, 2, 474.99, 'GBP', 75, '2025-03-21', '2025-04-03'),
-- (11, 3, 634.99, 'GBP', 51, '2025-03-21', '2025-04-03'),
-- (11, 4, 984.99, 'GBP', 29, '2025-03-21', '2025-04-03'),
-- (11, 5, 1884.99, 'GBP', 14, '2025-03-21', '2025-04-03'),
-- (11, 6, 4724.99, 'GBP', 7, '2025-03-21', '2025-04-03'),
-- -- flight 12 (123 LHR->DXB)
-- (12, 1, 349.99, 'GBP', 108, '2025-03-21', '2025-04-03'),
-- (12, 2, 444.99, 'GBP', 63, '2025-03-21', '2025-04-03'),
-- (12, 3, 604.99, 'GBP', 53, '2025-03-21', '2025-04-03'),
-- (12, 4, 954.99, 'GBP', 31, '2025-03-21', '2025-04-03'),
-- (12, 5, 1794.99, 'GBP', 15, '2025-03-21', '2025-04-03'),
-- (12, 6, 4474.99, 'GBP', 8, '2025-03-21', '2025-04-03'),
-- -- flight 13 (125 LHR->DXB)
-- (13, 1, 404.99, 'GBP', 87, '2025-03-21', '2025-04-03'),
-- (13, 2, 494.99, 'GBP', 67, '2025-03-21', '2025-04-03'),
-- (13, 3, 654.99, 'GBP', 43, '2025-03-21', '2025-04-03'),
-- (13, 4, 1004.99, 'GBP', 21, '2025-03-21', '2025-04-03'),
-- (13, 5, 1904.99, 'GBP', 10, '2025-03-21', '2025-04-03'),
-- (13, 6, 4774.99, 'GBP', 4, '2025-03-21', '2025-04-03'),
-- -- flight 14 (127 LHR->DXB)
-- (14, 1, 364.99, 'GBP', 104, '2025-03-21', '2025-04-03'),
-- (14, 2, 454.99, 'GBP', 76, '2025-03-21', '2025-04-03'),
-- (14, 3, 614.99, 'GBP', 54, '2025-03-21', '2025-04-03'),
-- (14, 4, 964.99, 'GBP', 32, '2025-03-21', '2025-04-03'),
-- (14, 5, 1814.99, 'GBP', 16, '2025-03-21', '2025-04-03'),
-- (14, 6, 4524.99, 'GBP', 8, '2025-03-21', '2025-04-03'),
-- -- flight 15 (102 DXB->LHR)
-- (15, 1, 369.99, 'GBP', 97, '2025-03-21', '2025-04-04'),
-- (15, 2, 459.99, 'GBP', 70, '2025-03-21', '2025-04-04'),
-- (15, 3, 619.99, 'GBP', 47, '2025-03-21', '2025-04-04'),
-- (15, 4, 979.99, 'GBP', 25, '2025-03-21', '2025-04-04'),
-- (15, 5, 1849.99, 'GBP', 12, '2025-03-21', '2025-04-04'),
-- (15, 6, 4649.99, 'GBP', 5, '2025-03-21', '2025-04-04'),
-- -- flight 16 (104 DXB->LHR)
-- (16, 1, 349.99, 'GBP', 112, '2025-03-21', '2025-04-04'),
-- (16, 2, 439.99, 'GBP', 64, '2025-03-21', '2025-04-04'),
-- (16, 3, 599.99, 'GBP', 56, '2025-03-21', '2025-04-04'),
-- (16, 4, 959.99, 'GBP', 28, '2025-03-21', '2025-04-04'),
-- (16, 5, 1799.99, 'GBP', 14, '2025-03-21', '2025-04-04'),
-- (16, 6, 4499.99, 'GBP', 6, '2025-03-21', '2025-04-04'),
-- -- flight 17 (106 DXB->LHR)
-- (17, 1, 389.99, 'GBP', 90, '2025-03-21', '2025-04-04'),
-- (17, 2, 479.99, 'GBP', 72, '2025-03-21', '2025-04-04'),
-- (17, 3, 639.99, 'GBP', 44, '2025-03-21', '2025-04-04'),
-- (17, 4, 999.99, 'GBP', 22, '2025-03-21', '2025-04-04'),
-- (17, 5, 1879.99, 'GBP', 11, '2025-03-21', '2025-04-04'),
-- (17, 6, 4749.99, 'GBP', 5, '2025-03-21', '2025-04-04'),
-- -- flight 18 (108 DXB->LHR)
-- (18, 1, 374.99, 'GBP', 103, '2025-03-21', '2025-04-04'),
-- (18, 2, 464.99, 'GBP', 68, '2025-03-21', '2025-04-04'),
-- (18, 3, 624.99, 'GBP', 48, '2025-03-21', '2025-04-04'),
-- (18, 4, 984.99, 'GBP', 26, '2025-03-21', '2025-04-04'),
-- (18, 5, 1834.99, 'GBP', 13, '2025-03-21', '2025-04-04'),
-- (18, 6, 4574.99, 'GBP', 5, '2025-03-21', '2025-04-04'),
-- -- flight 19 (110 DXB->LHR)
-- (19, 1, 359.99, 'GBP', 107, '2025-03-21', '2025-04-04'),
-- (19, 2, 449.99, 'GBP', 61, '2025-03-21', '2025-04-04'),
-- (19, 3, 609.99, 'GBP', 53, '2025-03-21', '2025-04-04'),
-- (19, 4, 969.99, 'GBP', 31, '2025-03-21', '2025-04-04'),
-- (19, 5, 1809.99, 'GBP', 15, '2025-03-21', '2025-04-04'),
-- (19, 6, 4524.99, 'GBP', 7, '2025-03-21', '2025-04-04'),
-- -- flight 20 (112 DXB->LHR)
-- (20, 1, 394.99, 'GBP', 89, '2025-03-21', '2025-04-04'),
-- (20, 2, 484.99, 'GBP', 73, '2025-03-21', '2025-04-04'),
-- (20, 3, 644.99, 'GBP', 43, '2025-03-21', '2025-04-04'),
-- (20, 4, 1004.99, 'GBP', 21, '2025-03-21', '2025-04-04'),
-- (20, 5, 1894.99, 'GBP', 10, '2025-03-21', '2025-04-04'),
-- (20, 6, 4774.99, 'GBP', 4, '2025-03-21', '2025-04-04'),
-- -- flight 21 (114 DXB->LHR)
-- (21, 1, 379.99, 'GBP', 99, '2025-03-21', '2025-04-04'),
-- (21, 2, 469.99, 'GBP', 71, '2025-03-21', '2025-04-04'),
-- (21, 3, 629.99, 'GBP', 46, '2025-03-21', '2025-04-04'),
-- (21, 4, 989.99, 'GBP', 24, '2025-03-21', '2025-04-04'),
-- (21, 5, 1859.99, 'GBP', 12, '2025-03-21', '2025-04-04'),
-- (21, 6, 4599.99, 'GBP', 5, '2025-03-21', '2025-04-04'),
-- -- flight 22 (116 DXB->LHR)
-- (22, 1, 344.99, 'GBP', 116, '2025-03-21', '2025-04-04'),
-- (22, 2, 434.99, 'GBP', 60, '2025-03-21', '2025-04-04'),
-- (22, 3, 594.99, 'GBP', 57, '2025-03-21', '2025-04-04'),
-- (22, 4, 944.99, 'GBP', 33, '2025-03-21', '2025-04-04'),
-- (22, 5, 1784.99, 'GBP', 16, '2025-03-21', '2025-04-04'),
-- (22, 6, 4449.99, 'GBP', 8, '2025-03-21', '2025-04-04'),
-- -- flight 23 (118 DXB->LHR)
-- (23, 1, 404.99, 'GBP', 86, '2025-03-21', '2025-04-04'),
-- (23, 2, 494.99, 'GBP', 65, '2025-03-21', '2025-04-04'),
-- (23, 3, 654.99, 'GBP', 41, '2025-03-21', '2025-04-04'),
-- (23, 4, 1014.99, 'GBP', 19, '2025-03-21', '2025-04-04'),
-- (23, 5, 1914.99, 'GBP', 9, '2025-03-21', '2025-04-04'),
-- (23, 6, 4824.99, 'GBP', 4, '2025-03-21', '2025-04-04'),
-- -- flight 24 (120 DXB->LHR)
-- (24, 1, 384.99, 'GBP', 94, '2025-03-21', '2025-04-04'),
-- (24, 2, 474.99, 'GBP', 74, '2025-03-21', '2025-04-04'),
-- (24, 3, 634.99, 'GBP', 50, '2025-03-21', '2025-04-04'),
-- (24, 4, 994.99, 'GBP', 27, '2025-03-21', '2025-04-04'),
-- (24, 5, 1874.99, 'GBP', 13, '2025-03-21', '2025-04-04'),
-- (24, 6, 4674.99, 'GBP', 6, '2025-03-21', '2025-04-04'),
-- -- flight 25 (122 DXB->LHR)
-- (25, 1, 364.99, 'GBP', 105, '2025-03-21', '2025-04-04'),
-- (25, 2, 454.99, 'GBP', 63, '2025-03-21', '2025-04-04'),
-- (25, 3, 614.99, 'GBP', 55, '2025-03-21', '2025-04-04'),
-- (25, 4, 974.99, 'GBP', 30, '2025-03-21', '2025-04-04'),
-- (25, 5, 1824.99, 'GBP', 14, '2025-03-21', '2025-04-04'),
-- (25, 6, 4549.99, 'GBP', 7, '2025-03-21', '2025-04-04'),
-- -- flight 26 (124 DXB->LHR)
-- (26, 1, 399.99, 'GBP', 88, '2025-03-21', '2025-04-04'),
-- (26, 2, 489.99, 'GBP', 66, '2025-03-21', '2025-04-04'),
-- (26, 3, 649.99, 'GBP', 42, '2025-03-21', '2025-04-04'),
-- (26, 4, 1009.99, 'GBP', 20, '2025-03-21', '2025-04-04'),
-- (26, 5, 1909.99, 'GBP', 10, '2025-03-21', '2025-04-04'),
-- (26, 6, 4799.99, 'GBP', 4, '2025-03-21', '2025-04-04'),
-- -- flight 27 (126 DXB->LHR)
-- (27, 1, 354.99, 'GBP', 111, '2025-03-21', '2025-04-04'),
-- (27, 2, 444.99, 'GBP', 62, '2025-03-21', '2025-04-04'),
-- (27, 3, 604.99, 'GBP', 54, '2025-03-21', '2025-04-04'),
-- (27, 4, 964.99, 'GBP', 32, '2025-03-21', '2025-04-04'),
-- (27, 5, 1804.99, 'GBP', 16, '2025-03-21', '2025-04-04'),
-- (27, 6, 4474.99, 'GBP', 8, '2025-03-21', '2025-04-04'),
-- -- flight 28 (128 DXB->LHR)
-- (28, 1, 409.99, 'GBP', 84, '2025-03-21', '2025-04-04'),
-- (28, 2, 499.99, 'GBP', 67, '2025-03-21', '2025-04-04'),
-- (28, 3, 659.99, 'GBP', 40, '2025-03-21', '2025-04-04'),
-- (28, 4, 1019.99, 'GBP', 18, '2025-03-21', '2025-04-04'),
-- (28, 5, 1929.99, 'GBP', 8, '2025-03-21', '2025-04-04'),
-- (28, 6, 4849.99, 'GBP', 3, '2025-03-21', '2025-04-04');


-- -- LHR -> DXB flights around today (2026-03-14) so they show in ±5 day window
-- INSERT INTO "flight" VALUES (NULL, 401, 1, 5, '2026-03-10 06:00:00', '2026-03-10 17:00:00', 'scheduled', 280);
-- INSERT INTO "flight" VALUES (NULL, 402, 1, 5, '2026-03-11 09:30:00', '2026-03-11 20:30:00', 'scheduled', 280);
-- INSERT INTO "flight" VALUES (NULL, 403, 1, 5, '2026-03-12 14:00:00', '2026-03-13 01:00:00', 'scheduled', 300);
-- INSERT INTO "flight" VALUES (NULL, 404, 1, 5, '2026-03-13 07:00:00', '2026-03-13 18:00:00', 'scheduled', 220);
-- INSERT INTO "flight" VALUES (NULL, 405, 1, 5, '2026-03-14 08:00:00', '2026-03-14 19:00:00', 'scheduled', 300);
-- INSERT INTO "flight" VALUES (NULL, 406, 1, 5, '2026-03-15 11:00:00', '2026-03-15 22:00:00', 'scheduled', 280);
-- INSERT INTO "flight" VALUES (NULL, 407, 1, 5, '2026-03-16 16:00:00', '2026-03-17 03:00:00', 'scheduled', 260);
-- INSERT INTO "flight" VALUES (NULL, 408, 1, 5, '2026-03-17 20:00:00', '2026-03-18 07:00:00', 'scheduled', 300);
-- INSERT INTO "flight" VALUES (NULL, 409, 1, 5, '2026-03-18 05:30:00', '2026-03-18 16:30:00', 'scheduled', 280);
-- INSERT INTO "flight" VALUES (NULL, 410, 1, 5, '2026-03-19 13:00:00', '2026-03-20 00:00:00', 'delayed',   300);

-- -- DXB -> LHR return flights around today (2026-03-16) ±5 day window
-- INSERT INTO "flight" VALUES (NULL, 411, 5, 1, '2026-03-10 08:00:00', '2026-03-10 13:00:00', 'scheduled', 280);
-- INSERT INTO "flight" VALUES (NULL, 412, 5, 1, '2026-03-11 13:00:00', '2026-03-11 18:30:00', 'scheduled', 260);
-- INSERT INTO "flight" VALUES (NULL, 413, 5, 1, '2026-03-12 07:30:00', '2026-03-12 12:30:00', 'scheduled', 300);
-- INSERT INTO "flight" VALUES (NULL, 414, 5, 1, '2026-03-13 22:00:00', '2026-03-14 03:00:00', 'scheduled', 220);
-- INSERT INTO "flight" VALUES (NULL, 415, 5, 1, '2026-03-14 10:00:00', '2026-03-14 15:00:00', 'scheduled', 300);
-- INSERT INTO "flight" VALUES (NULL, 416, 5, 1, '2026-03-15 15:00:00', '2026-03-15 20:00:00', 'scheduled', 280);
-- INSERT INTO "flight" VALUES (NULL, 417, 5, 1, '2026-03-16 06:00:00', '2026-03-16 11:00:00', 'scheduled', 260);
-- INSERT INTO "flight" VALUES (NULL, 418, 5, 1, '2026-03-17 18:30:00', '2026-03-17 23:30:00', 'delayed',   300);
-- INSERT INTO "flight" VALUES (NULL, 419, 5, 1, '2026-03-18 09:00:00', '2026-03-18 14:00:00', 'scheduled', 280);
-- INSERT INTO "flight" VALUES (NULL, 420, 5, 1, '2026-03-19 21:00:00', '2026-03-20 02:00:00', 'scheduled', 300);

-- -- Flight fares for DXB -> LHR return flights (flights 411-420)
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=411 AND origin_airport=5 AND destination_airport=1), 1, 199.99, 'GBP', 180, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=411 AND origin_airport=5 AND destination_airport=1), 2, 349.99, 'GBP', 60,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=411 AND origin_airport=5 AND destination_airport=1), 3, 899.99, 'GBP', 20,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=412 AND origin_airport=5 AND destination_airport=1), 1, 179.99, 'GBP', 200, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=412 AND origin_airport=5 AND destination_airport=1), 2, 299.99, 'GBP', 50,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=412 AND origin_airport=5 AND destination_airport=1), 3, 849.99, 'GBP', 15,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=413 AND origin_airport=5 AND destination_airport=1), 1, 219.99, 'GBP', 150, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=413 AND origin_airport=5 AND destination_airport=1), 2, 379.99, 'GBP', 45,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=413 AND origin_airport=5 AND destination_airport=1), 3, 949.99, 'GBP', 18,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=414 AND origin_airport=5 AND destination_airport=1), 1, 159.99, 'GBP', 180, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=414 AND origin_airport=5 AND destination_airport=1), 2, 279.99, 'GBP', 55,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=414 AND origin_airport=5 AND destination_airport=1), 3, 799.99, 'GBP', 22,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=415 AND origin_airport=5 AND destination_airport=1), 1, 249.99, 'GBP', 120, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=415 AND origin_airport=5 AND destination_airport=1), 2, 399.99, 'GBP', 40,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=415 AND origin_airport=5 AND destination_airport=1), 3, 999.99, 'GBP', 12,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=416 AND origin_airport=5 AND destination_airport=1), 1, 189.99, 'GBP', 200, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=416 AND origin_airport=5 AND destination_airport=1), 2, 319.99, 'GBP', 50,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=416 AND origin_airport=5 AND destination_airport=1), 3, 869.99, 'GBP', 16,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=417 AND origin_airport=5 AND destination_airport=1), 1, 209.99, 'GBP', 160, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=417 AND origin_airport=5 AND destination_airport=1), 2, 359.99, 'GBP', 48,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=417 AND origin_airport=5 AND destination_airport=1), 3, 919.99, 'GBP', 14,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=418 AND origin_airport=5 AND destination_airport=1), 1, 169.99, 'GBP', 220, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=418 AND origin_airport=5 AND destination_airport=1), 2, 289.99, 'GBP', 52,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=418 AND origin_airport=5 AND destination_airport=1), 3, 829.99, 'GBP', 20,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=419 AND origin_airport=5 AND destination_airport=1), 1, 229.99, 'GBP', 140, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=419 AND origin_airport=5 AND destination_airport=1), 2, 389.99, 'GBP', 42,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=419 AND origin_airport=5 AND destination_airport=1), 3, 979.99, 'GBP', 10,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=420 AND origin_airport=5 AND destination_airport=1), 1, 139.99, 'GBP', 240, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=420 AND origin_airport=5 AND destination_airport=1), 2, 259.99, 'GBP', 58,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=420 AND origin_airport=5 AND destination_airport=1), 3, 779.99, 'GBP', 24,  '2026-01-01', '2026-03-20');

-- -- Fare classes (if not already inserted)
-- INSERT OR IGNORE INTO "fare_class" VALUES (1, 'Y', 'Economy',  'Economy Standard', 0, 'no cancellation',  0, 0, 0, 0, 1, 7,  0, 0,  1.0, NULL, 'Basic economy fare',                      datetime('now'), datetime('now'));
-- INSERT OR IGNORE INTO "fare_class" VALUES (2, 'W', 'Economy',  'Economy Flex',     1, 'free cancellation', 1, 0, 0, 0, 1, 7,  1, 23, 1.2, NULL, 'Flexible economy with one checked bag',   datetime('now'), datetime('now'));
-- INSERT OR IGNORE INTO "fare_class" VALUES (3, 'J', 'Business', 'Business Flex',    1, 'free cancellation', 1, 1, 1, 1, 1, 10, 2, 32, 2.0, NULL, 'Full business class experience',          datetime('now'), datetime('now'));

-- Flight fares for each new flight (3 fare classes each)
-- Get the flight_ids first with: SELECT flight_id, flight_number FROM flight WHERE origin_airport=1 AND destination_airport=5;
-- Then replace the flight_id values below accordingly

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=401 AND origin_airport=1 AND destination_airport=5), 1, 199.99, 'GBP', 180, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=401 AND origin_airport=1 AND destination_airport=5), 2, 349.99, 'GBP', 60,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=401 AND origin_airport=1 AND destination_airport=5), 3, 899.99, 'GBP', 20,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=402 AND origin_airport=1 AND destination_airport=5), 1, 179.99, 'GBP', 200, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=402 AND origin_airport=1 AND destination_airport=5), 2, 299.99, 'GBP', 50,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=402 AND origin_airport=1 AND destination_airport=5), 3, 849.99, 'GBP', 15,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=403 AND origin_airport=1 AND destination_airport=5), 1, 219.99, 'GBP', 150, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=403 AND origin_airport=1 AND destination_airport=5), 2, 379.99, 'GBP', 45,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=403 AND origin_airport=1 AND destination_airport=5), 3, 949.99, 'GBP', 18,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=404 AND origin_airport=1 AND destination_airport=5), 1, 159.99, 'GBP', 180, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=404 AND origin_airport=1 AND destination_airport=5), 2, 279.99, 'GBP', 55,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=404 AND origin_airport=1 AND destination_airport=5), 3, 799.99, 'GBP', 22,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=405 AND origin_airport=1 AND destination_airport=5), 1, 249.99, 'GBP', 120, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=405 AND origin_airport=1 AND destination_airport=5), 2, 399.99, 'GBP', 40,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=405 AND origin_airport=1 AND destination_airport=5), 3, 999.99, 'GBP', 12,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=406 AND origin_airport=1 AND destination_airport=5), 1, 189.99, 'GBP', 200, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=406 AND origin_airport=1 AND destination_airport=5), 2, 319.99, 'GBP', 50,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=406 AND origin_airport=1 AND destination_airport=5), 3, 869.99, 'GBP', 16,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=407 AND origin_airport=1 AND destination_airport=5), 1, 209.99, 'GBP', 160, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=407 AND origin_airport=1 AND destination_airport=5), 2, 359.99, 'GBP', 48,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=407 AND origin_airport=1 AND destination_airport=5), 3, 919.99, 'GBP', 14,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=408 AND origin_airport=1 AND destination_airport=5), 1, 169.99, 'GBP', 220, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=408 AND origin_airport=1 AND destination_airport=5), 2, 289.99, 'GBP', 52,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=408 AND origin_airport=1 AND destination_airport=5), 3, 829.99, 'GBP', 20,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=409 AND origin_airport=1 AND destination_airport=5), 1, 229.99, 'GBP', 140, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=409 AND origin_airport=1 AND destination_airport=5), 2, 389.99, 'GBP', 42,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=409 AND origin_airport=1 AND destination_airport=5), 3, 979.99, 'GBP', 10,  '2026-01-01', '2026-03-20');

-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=410 AND origin_airport=1 AND destination_airport=5), 1, 139.99, 'GBP', 240, '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=410 AND origin_airport=1 AND destination_airport=5), 2, 259.99, 'GBP', 58,  '2026-01-01', '2026-03-20');
-- INSERT INTO "flight_fare" VALUES (NULL, (SELECT flight_id FROM flight WHERE flight_number=410 AND origin_airport=1 AND destination_airport=5), 3, 779.99, 'GBP', 24,  '2026-01-01', '2026-03-20');



-- -- More flights
-- INSERT INTO flight (flight_number, origin_airport, destination_airport, scheduled_departure_time, scheduled_arrival_time, status, capacity) VALUES
-- -- LHR <-> JFK
-- (101, 1, 2, '2026-04-01 08:00', '2026-04-01 11:00', 'scheduled', 180),
-- (102, 2, 1, '2026-04-01 14:00', '2026-04-02 02:00', 'scheduled', 180),
-- (103, 1, 2, '2026-04-02 09:30', '2026-04-02 12:30', 'scheduled', 220),
-- (104, 2, 1, '2026-04-02 16:00', '2026-04-03 04:00', 'scheduled', 220),

-- -- LHR <-> CDG
-- (201, 1, 3, '2026-04-01 06:00', '2026-04-01 08:15', 'scheduled', 150),
-- (202, 3, 1, '2026-04-01 10:00', '2026-04-01 10:15', 'scheduled', 150),
-- (203, 1, 3, '2026-04-01 18:00', '2026-04-01 20:15', 'scheduled', 160),
-- (204, 3, 1, '2026-04-02 07:00', '2026-04-02 07:15', 'scheduled', 160),

-- -- LHR <-> MAN
-- (301, 1, 4, '2026-04-01 07:00', '2026-04-01 08:05', 'scheduled', 100),
-- (302, 4, 1, '2026-04-01 09:00', '2026-04-01 10:05', 'scheduled', 100),
-- (303, 1, 4, '2026-04-01 13:00', '2026-04-01 14:05', 'scheduled', 120),
-- (304, 4, 1, '2026-04-01 17:00', '2026-04-01 18:05', 'scheduled', 120),

-- -- LHR <-> DXB
-- (401, 1, 5, '2026-04-01 10:00', '2026-04-01 21:00', 'scheduled', 280),
-- (402, 5, 1, '2026-04-01 23:00', '2026-04-02 03:30', 'scheduled', 280),
-- (403, 1, 5, '2026-04-02 14:00', '2026-04-03 01:00', 'scheduled', 300),
-- (404, 5, 1, '2026-04-03 08:00', '2026-04-03 12:30', 'scheduled', 300),

-- -- JFK <-> CDG
-- (501, 2, 3, '2026-04-01 11:00', '2026-04-02 00:00', 'scheduled', 200),
-- (502, 3, 2, '2026-04-02 13:00', '2026-04-02 15:30', 'scheduled', 200),
-- (503, 2, 3, '2026-04-03 22:00', '2026-04-04 11:00', 'scheduled', 240),

-- -- JFK <-> DXB
-- (601, 2, 5, '2026-04-01 22:00', '2026-04-02 18:00', 'scheduled', 280),
-- (602, 5, 2, '2026-04-02 03:00', '2026-04-02 09:00', 'scheduled', 280),

-- -- MAN <-> CDG
-- (701, 4, 3, '2026-04-01 07:30', '2026-04-01 10:00', 'scheduled', 130),
-- (702, 3, 4, '2026-04-01 12:00', '2026-04-01 12:30', 'scheduled', 130),
-- (703, 4, 3, '2026-04-02 15:00', '2026-04-02 17:30', 'scheduled', 140),

-- -- MAN <-> DXB
-- (801, 4, 5, '2026-04-01 09:00', '2026-04-01 19:30', 'scheduled', 260),
-- (802, 5, 4, '2026-04-02 02:00', '2026-04-02 06:30', 'scheduled', 260),

-- -- CDG <-> DXB
-- (901, 3, 5, '2026-04-01 08:00', '2026-04-01 16:30', 'scheduled', 270),
-- (902, 5, 3, '2026-04-01 18:00', '2026-04-02 22:30', 'scheduled', 270);

-- -- Airports
-- INSERT INTO "airport" VALUES (1, 'LHR', 'London Heathrow', 'London', 'GB');
-- INSERT INTO "airport" VALUES (2, 'JFK', 'John F. Kennedy International', 'New York', 'US');
-- INSERT INTO "airport" VALUES (3, 'CDG', 'Charles de Gaulle', 'Paris', 'FR');
-- INSERT INTO "airport" VALUES (4, 'MAN', 'Manchester Airport', 'Manchester', 'GB');
-- INSERT INTO "airport" VALUES (5, 'DXB', 'Dubai International', 'Dubai', 'AE');

-- -- Flights
-- INSERT INTO "flight" VALUES (1, 101, 1, 2, '2026-03-15 08:00:00', '2026-03-15 11:00:00', 'scheduled', 180);
-- INSERT INTO "flight" VALUES (2, 202, 1, 3, '2026-03-15 09:30:00', '2026-03-15 12:00:00', 'scheduled', 220);
-- INSERT INTO "flight" VALUES (3, 303, 4, 5, '2026-03-16 14:00:00', '2026-03-17 00:30:00', 'scheduled', 300);

-- -- Fare Classes
-- INSERT INTO "fare_class" VALUES (1, 'Y', 'Economy', 'Economy Standard', 0, 'no cancellation', 0, 0, 0, 0, 1, 7, 0, 0, 1.0, NULL, 'Basic economy fare', datetime('now'), datetime('now'));
-- INSERT INTO "fare_class" VALUES (2, 'W', 'Economy', 'Economy Flex', 1, 'free cancellation', 1, 0, 0, 0, 1, 7, 1, 23, 1.2, NULL, 'Flexible economy with one checked bag', datetime('now'), datetime('now'));
-- INSERT INTO "fare_class" VALUES (3, 'J', 'Business', 'Business Flex', 1, 'free cancellation', 1, 1, 1, 1, 1, 10, 2, 32, 2.0, NULL, 'Full business class experience', datetime('now'), datetime('now'));

-- -- Flight Fares
-- INSERT INTO "flight_fare" VALUES (1, 1, 1, 199.99, 'GBP', 120, '2026-01-01 00:00:00', '2026-03-14 23:59:59');
-- INSERT INTO "flight_fare" VALUES (2, 1, 2, 349.99, 'GBP', 40,  '2026-01-01 00:00:00', '2026-03-14 23:59:59');
-- INSERT INTO "flight_fare" VALUES (3, 1, 3, 899.99, 'GBP', 20,  '2026-01-01 00:00:00', '2026-03-14 23:59:59');
-- INSERT INTO "flight_fare" VALUES (4, 2, 1, 89.99,  'GBP', 150, '2026-01-01 00:00:00', '2026-03-14 23:59:59');
-- INSERT INTO "flight_fare" VALUES (5, 3, 2, 499.99, 'GBP', 60,  '2026-01-01 00:00:00', '2026-03-15 23:59:59');

-- -- Users
-- INSERT INTO "user" VALUES (1, 'james.walker@email.com', 'hashed_pw_1', 'James', 'Walker', '07700900001', '1990-04-12', datetime('now'), 'active');
-- INSERT INTO "user" VALUES (2, 'priya.sharma@email.com', 'hashed_pw_2', 'Priya', 'Sharma', '07700900002', '1985-11-23', datetime('now'), 'active');
-- INSERT INTO "user" VALUES (3, 'tom.nguyen@email.com',   'hashed_pw_3', 'Tom',   'Nguyen',  '07700900003', '2000-07-05', datetime('now'), 'active');

-- -- Bookings
-- INSERT INTO "booking" VALUES (1, 1, 'BOOK001', 1, datetime('now'), 'confirmed', NULL, 1);
-- INSERT INTO "booking" VALUES (2, 2, 'BOOK002', 2, datetime('now'), 'confirmed', NULL, 1);
-- INSERT INTO "booking" VALUES (3, 3, 'BOOK003', 3, datetime('now'), 'cancelled', datetime('now'), 0);

-- -- Payments
-- INSERT INTO "payment" VALUES (1, 1, 199.99, 'credit', 'paid',    datetime('now'), 'PAY-REF-001', 'GBP');
-- INSERT INTO "payment" VALUES (2, 2, 349.99, 'debit',  'paid',    datetime('now'), 'PAY-REF-002', 'GBP');
-- INSERT INTO "payment" VALUES (3, 3, 89.99,  'paypal', 'refunded',datetime('now'), 'PAY-REF-003', 'GBP');

-- -- Passengers
-- INSERT INTO "passenger" VALUES (1, 1, 'james.walker@email.com', 1, 'Mr', 'James', 'Walker', '1990-04-12', 'M', 'GB', 'passport', 'GB123456789', 'GB', '2031-01-01');
-- INSERT INTO "passenger" VALUES (2, 2, 'priya.sharma@email.com', 1, 'Ms', 'Priya', 'Sharma',  '1985-11-23', 'F', 'IN', 'passport', 'IN987654321', 'IN', '2029-06-15');
-- INSERT INTO "passenger" VALUES (3, 3, 'tom.nguyen@email.com',   0, 'Mr', 'Tom',   'Nguyen',  '2000-07-05', 'M', 'US', 'passport', 'US111222333', 'US', '2028-09-30');

-- -- Booking Segments
-- INSERT INTO "booking_segment" VALUES (1, 1, 1, 1);
-- INSERT INTO "booking_segment" VALUES (2, 2, 1, 2);
-- INSERT INTO "booking_segment" VALUES (3, 3, 2, 4);

-- -- Seats
-- INSERT INTO "seat" VALUES (1,  1, '1A',  'Business', 'window', 1, 0, 0, 'occupied');
-- INSERT INTO "seat" VALUES (2,  1, '14B', 'Economy',  'middle', 0, 0, 0, 'occupied');
-- INSERT INTO "seat" VALUES (3,  1, '22C', 'Economy',  'aisle',  0, 0, 0, 'occupied');
-- INSERT INTO "seat" VALUES (4,  1, '15A', 'Economy',  'window', 0, 0, 0, 'available');
-- INSERT INTO "seat" VALUES (5,  1, '20F', 'Economy',  'window', 0, 1, 0, 'available');
-- INSERT INTO "seat" VALUES (6,  2, '2A',  'Business', 'window', 1, 0, 0, 'available');
-- INSERT INTO "seat" VALUES (7,  2, '10D', 'Economy',  'aisle',  0, 0, 1, 'available');
-- INSERT INTO "seat" VALUES (8,  3, '5A',  'Business', 'window', 1, 0, 0, 'available');

-- -- Seat Assignments
-- INSERT INTO "seat_assignment" VALUES (1, 1, 1, 1);
-- INSERT INTO "seat_assignment" VALUES (2, 2, 2, 2);
-- INSERT INTO "seat_assignment" VALUES (3, 3, 3, 3);

-- -- Staff
-- INSERT INTO "staff" VALUES (1, 'sarah.jones@airline.com', 'hashed_staff_pw_1', 'Sarah', 'Jones', '07700800001', 'customer_service', datetime('now'));
-- INSERT INTO "staff" VALUES (2, 'david.chen@airline.com',  'hashed_staff_pw_2', 'David', 'Chen',  '07700800002', 'admin', datetime('now'));

-- -- Complaints
-- INSERT INTO "complaint" VALUES (1, 2, 'delay', 'My flight was delayed by 3 hours and I missed a connection.', datetime('now'), 'open', 1);
-- INSERT INTO "complaint" VALUES (2, 1, 'baggage', 'My checked bag arrived damaged.', datetime('now'), 'resolved', 1);

-- -- Notifications
-- INSERT INTO "notification" VALUES (1, 1, 'booking_confirmed', 'Your booking BOOK001 has been confirmed.', datetime('now'), datetime('now'));
-- INSERT INTO "notification" VALUES (2, 2, 'booking_confirmed', 'Your booking BOOK002 has been confirmed.', datetime('now'), datetime('now'));
-- INSERT INTO "notification" VALUES (3, 3, 'booking_cancelled', 'Your booking BOOK003 has been cancelled and a refund issued.', datetime('now'), NULL);