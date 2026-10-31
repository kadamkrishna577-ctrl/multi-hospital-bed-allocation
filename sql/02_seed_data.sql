-- ==============================================================================
-- PROJECT: Multi-Hospital Emergency Bed Allocation System
-- FILE: sql/02_seed_data.sql
-- PURPOSE: Unified mock seed data across all modules (P1, P2, P3, P4)
-- Engine: MySQL InnoDB
-- ==============================================================================

USE hospital_bed_db;

-- 1. HOSPITALS (P1 - Saksham)
INSERT INTO hospitals (hospital_id, name, address, latitude, longitude, contact_phone, total_beds) VALUES
(1, 'City Central Hospital', '123 Main St, Central District, Bangalore', 12.9716, 77.5946, '080-22221111', 120),
(2, 'Memorial Care Hospital', '456 Health Ave, South District, Bangalore', 12.9352, 77.6245, '080-33332222', 85),
(3, 'Metro General Hospital', '789 North Blvd, North District, Bangalore', 13.0358, 77.5970, '080-44443333', 150)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 2. WARDS (P1 - Saksham)
INSERT INTO wards (ward_id, hospital_id, ward_name, ward_type, floor_number) VALUES
(1, 1, 'Emergency ICU Ward A', 'ICU', 1),
(2, 1, 'General Medicine Ward B', 'GENERAL', 2),
(3, 1, 'Infectious Isolation Ward C', 'ISOLATION', 3),
(4, 2, 'Cardiology Intensive Care', 'ICU', 1),
(5, 2, 'General Post-Op Ward', 'GENERAL', 2),
(6, 3, 'Trauma ICU', 'ICU', 1),
(7, 3, 'Standard Recovery Ward', 'GENERAL', 2)
ON DUPLICATE KEY UPDATE ward_name=VALUES(ward_name);

-- 3. ROOMS (P1 - Saksham)
INSERT INTO rooms (room_id, ward_id, room_number, is_negative_pressure) VALUES
(1, 1, 'ICU-101', FALSE),
(2, 1, 'ICU-102', FALSE),
(3, 2, 'GEN-201', FALSE),
(4, 3, 'ISO-301', TRUE),
(5, 3, 'ISO-302', TRUE),
(6, 4, 'CARD-101', FALSE),
(7, 6, 'TRM-101', FALSE)
ON DUPLICATE KEY UPDATE room_number=VALUES(room_number);

-- 4. USERS (P1 - Saksham)
INSERT INTO users (user_id, username, password_hash, full_name, role, hospital_id) VALUES
(1, 'admin', 'admin123', 'Chief Administrator', 'ADMIN', NULL),
(2, 'doctor', 'doc123', 'Dr. Aakash Roy', 'DOCTOR', 1),
(3, 'nurse', 'nurse123', 'Sister Mary', 'NURSE', 1),
(4, 'paramedic', 'para123', 'Alex Turner', 'PARAMEDIC', 1),
(5, 'cleaner', 'clean123', 'Sunil Yadav', 'CLEANING_STAFF', 1)
ON DUPLICATE KEY UPDATE full_name=VALUES(full_name);

-- 5. BEDS (Unified across P1, P2, P4)
INSERT INTO beds (bed_id, room_id, bed_number, ward_name, hospital_name, status, base_hourly_rate, patient_id, patient_name, patient_triage) VALUES
(101, 1, 'BED-101-A', 'Emergency ICU Ward A', 'City Central Hospital', 'VACANT', 100.00, NULL, NULL, NULL),
(102, 1, 'BED-101-B', 'Emergency ICU Ward A', 'City Central Hospital', 'OCCUPIED', 100.00, 5001, 'Robert Chen', 'LEVEL_4'),
(103, 2, 'BED-102-A', 'Emergency ICU Ward A', 'City Central Hospital', 'OCCUPIED', 100.00, 5002, 'Emily Davis', 'LEVEL_5'),
(104, 3, 'BED-201-A', 'General Medicine Ward B', 'City Central Hospital', 'REQUIRES_SANITIZATION', 45.00, NULL, NULL, NULL),
(105, 3, 'BED-201-B', 'General Medicine Ward B', 'City Central Hospital', 'RESTRICTED_BUFFER', 45.00, NULL, NULL, NULL),
(106, 4, 'BED-301-A', 'Infectious Isolation Ward C', 'City Central Hospital', 'VACANT', 80.00, NULL, NULL, NULL),
(201, 6, 'BED-CARD-1', 'Cardiology Intensive Care', 'Memorial Care Hospital', 'VACANT', 120.00, NULL, NULL, NULL),
(202, 6, 'BED-CARD-2', 'Cardiology Intensive Care', 'Memorial Care Hospital', 'OCCUPIED', 120.00, 5003, 'James Wilson', 'LEVEL_2'),
(203, 6, 'BED-CARD-3', 'Cardiology Intensive Care', 'Memorial Care Hospital', 'VACANT', 120.00, NULL, NULL, NULL),
(301, 7, 'BED-TRM-1', 'Trauma ICU', 'Metro General Hospital', 'VACANT', 110.00, NULL, NULL, NULL),
(302, 7, 'BED-TRM-2', 'Trauma ICU', 'Metro General Hospital', 'RESERVED_EN_ROUTE', 110.00, NULL, NULL, NULL),
(303, 7, 'BED-TRM-3', 'Trauma ICU', 'Metro General Hospital', 'OCCUPIED', 110.00, 5005, 'Sarah Jenkins', 'LEVEL_5')
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- 6. MEDICAL MACHINES (P2 - Krishna)
INSERT INTO medical_machines (machine_id, bed_id, equipment_type, serial_number, hospital_name, operating_hours_used, safe_limit_hours, under_maintenance) VALUES
(1, 101, 'VENTILATOR', 'VENT-CC-101', 'City Central Hospital', 120.5, 500.0, FALSE),
(2, 101, 'VENTILATOR', 'VENT-CC-102', 'City Central Hospital', 485.0, 500.0, FALSE),
(3, 102, 'OXYGEN',     'OXY-CC-201',  'City Central Hospital', 250.0, 1000.0, FALSE),
(4, 103, 'DIALYSIS',   'DIA-CC-301',  'City Central Hospital', 290.0, 300.0, FALSE),
(5, 101, 'MONITOR',    'MON-CC-401',  'City Central Hospital', 800.0, 1500.0, FALSE),
(6, 201, 'VENTILATOR', 'VENT-MC-101', 'Memorial Care Hospital', 510.0, 500.0, TRUE),
(7, 202, 'OXYGEN',     'OXY-MC-201',  'Memorial Care Hospital', 920.0, 1000.0, FALSE),
(8, 203, 'MONITOR',    'MON-MC-401',  'Memorial Care Hospital', 1450.0, 1500.0, FALSE),
(9, 301, 'VENTILATOR', 'VENT-MG-101', 'Metro General Hospital', 50.0, 500.0, FALSE),
(10, 302, 'DIALYSIS',  'DIA-MG-301',  'Metro General Hospital', 110.0, 300.0, FALSE)
ON DUPLICATE KEY UPDATE serial_number=VALUES(serial_number), operating_hours_used=VALUES(operating_hours_used);

-- 7. WAITLIST ENTRIES (P2 - Krishna)
INSERT INTO waitlist_entries (entry_id, patient_id, patient_name, triage_level, requested_hospital_name, requested_ward_name, request_time, notes) VALUES
(1, 6001, 'Arthur Pendelton', 'LEVEL_1', 'City Central Hospital', 'Emergency ICU Ward A', DATE_SUB(NOW(), INTERVAL 25 MINUTE), 'Cardiac arrest resuscitation in ER'),
(2, 6002, 'David Miller',     'LEVEL_2', 'City Central Hospital', 'Emergency ICU Ward A', DATE_SUB(NOW(), INTERVAL 40 MINUTE), 'Severe respiratory distress, SpO2 82%'),
(3, 6003, 'Carlos Santana',   'LEVEL_3', 'Memorial Care Hospital', 'Cardiology Intensive Care', DATE_SUB(NOW(), INTERVAL 60 MINUTE), 'Complicated post-op observation'),
(4, 6004, 'Angela White',     'LEVEL_5', 'City Central Hospital', 'General Medicine Ward B', DATE_SUB(NOW(), INTERVAL 90 MINUTE), 'Stable bed transfer')
ON DUPLICATE KEY UPDATE triage_level=VALUES(triage_level);

-- 8. AMBULANCES (P4 - Siddhant)
INSERT INTO ambulances (ambulance_id, license_plate, ambulance_type, current_hospital_name, is_available) VALUES
('AMB-ALS-101', 'KA-01-EQ-1001', 'ALS', 'City Central Hospital', TRUE),
('AMB-ALS-102', 'KA-01-EQ-1002', 'ALS', 'Memorial Care Hospital', TRUE),
('AMB-ALS-103', 'KA-01-EQ-1003', 'ALS', 'Metro General Hospital', TRUE),
('AMB-BLS-201', 'KA-02-TX-2001', 'BLS', 'City Central Hospital', TRUE),
('AMB-BLS-202', 'KA-02-TX-2002', 'BLS', 'Metro General Hospital', TRUE)
ON DUPLICATE KEY UPDATE is_available=VALUES(is_available);

-- 9. INITIAL AUDIT LOG (P1 - Saksham)
INSERT INTO audit_logs (actor_username, action_type, entity_id, details) VALUES
('system', 'DATABASE_INITIALIZATION', 'SYSTEM', 'Seed mock data successfully loaded.');
