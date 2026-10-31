-- ==============================================================================
-- PROJECT: Multi-Hospital Emergency Bed Allocation System
-- FILE: sql/02_seed_data.sql
-- PURPOSE: Sample mock data for hospitals, wards, rooms, beds, users, and equipment.
-- ==============================================================================

USE hospital_bed_db;

-- 1. HOSPITALS
INSERT INTO hospitals (hospital_id, name, address, latitude, longitude, contact_phone, total_beds) VALUES
(1, 'City Central Hospital', '123 Main St, Central District, Bangalore', 12.9716, 77.5946, '080-22221111', 120),
(2, 'Memorial Care Hospital', '456 Health Ave, South District, Bangalore', 12.9352, 77.6245, '080-33332222', 85),
(3, 'Metro General Hospital', '789 North Blvd, North District, Bangalore', 13.0358, 77.5970, '080-44443333', 150)
ON DUPLICATE KEY UPDATE name=VALUES(name);

-- 2. WARDS
INSERT INTO wards (ward_id, hospital_id, ward_name, ward_type, floor_number) VALUES
(1, 1, 'Emergency ICU Ward A', 'ICU', 1),
(2, 1, 'General Medicine Ward B', 'GENERAL', 2),
(3, 1, 'Infectious Isolation Ward C', 'ISOLATION', 3),
(4, 2, 'Cardiology Intensive Care', 'ICU', 1),
(5, 2, 'General Post-Op Ward', 'GENERAL', 2),
(6, 3, 'Trauma ICU', 'ICU', 1),
(7, 3, 'Standard Recovery Ward', 'GENERAL', 2)
ON DUPLICATE KEY UPDATE ward_name=VALUES(ward_name);

-- 3. ROOMS
INSERT INTO rooms (room_id, ward_id, room_number, is_negative_pressure) VALUES
(1, 1, 'ICU-101', FALSE),
(2, 1, 'ICU-102', FALSE),
(3, 2, 'GEN-201', FALSE),
(4, 3, 'ISO-301', TRUE),
(5, 3, 'ISO-302', TRUE),
(6, 4, 'CARD-101', FALSE),
(7, 6, 'TRM-101', FALSE)
ON DUPLICATE KEY UPDATE room_number=VALUES(room_number);

-- 4. BEDS
INSERT INTO beds (bed_id, room_id, bed_number, ward_name, hospital_name, status, base_hourly_rate) VALUES
(101, 1, 'BED-101-A', 'Emergency ICU Ward A', 'City Central Hospital', 'VACANT', 100.00),
(102, 1, 'BED-101-B', 'Emergency ICU Ward A', 'City Central Hospital', 'VACANT', 100.00),
(103, 2, 'BED-102-A', 'Emergency ICU Ward A', 'City Central Hospital', 'VACANT', 100.00),
(104, 3, 'BED-201-A', 'General Medicine Ward B', 'City Central Hospital', 'VACANT', 45.00),
(105, 3, 'BED-201-B', 'General Medicine Ward B', 'City Central Hospital', 'VACANT', 45.00),
(106, 4, 'BED-301-A', 'Infectious Isolation Ward C', 'City Central Hospital', 'VACANT', 80.00),
(201, 6, 'BED-CARD-1', 'Cardiology Intensive Care', 'Memorial Care Hospital', 'VACANT', 120.00),
(301, 7, 'BED-TRM-1', 'Trauma ICU', 'Metro General Hospital', 'VACANT', 110.00)
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- 5. USERS
INSERT INTO users (user_id, username, password_hash, full_name, role, hospital_id) VALUES
(1, 'admin', 'admin123', 'Chief Administrator', 'ADMIN', NULL),
(2, 'doctor', 'doc123', 'Dr. Aakash Roy', 'DOCTOR', 1),
(3, 'nurse', 'nurse123', 'Sister Mary', 'NURSE', 1),
(4, 'paramedic', 'para123', 'Alex Turner', 'PARAMEDIC', 1),
(5, 'cleaner', 'clean123', 'Sunil Yadav', 'CLEANING_STAFF', 1)
ON DUPLICATE KEY UPDATE full_name=VALUES(full_name);

-- 6. MEDICAL MACHINES
INSERT INTO medical_machines (machine_id, bed_id, equipment_type, serial_number, hospital_name, operating_hours_used, safe_limit_hours, under_maintenance) VALUES
(1, 101, 'VENTILATOR', 'VENT-CCH-001', 'City Central Hospital', 45.0, 500.0, FALSE),
(2, 101, 'MONITOR', 'MON-CCH-001', 'City Central Hospital', 60.0, 1500.0, FALSE),
(3, 102, 'OXYGEN', 'OXY-CCH-001', 'City Central Hospital', 30.0, 1000.0, FALSE),
(4, 201, 'VENTILATOR', 'VENT-MCH-001', 'Memorial Care Hospital', 10.0, 500.0, FALSE)
ON DUPLICATE KEY UPDATE serial_number=VALUES(serial_number);

-- 7. INITIAL AUDIT LOG
INSERT INTO audit_logs (actor_username, action_type, entity_id, details) VALUES
('system', 'DATABASE_INITIALIZATION', 'SYSTEM', 'Seed mock data successfully loaded.');
