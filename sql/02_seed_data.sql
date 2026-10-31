-- ==============================================================================
-- Module Shared: Seed Data & Testing Fixtures
-- Multi-Hospital Real-Time Bed Allocation & Emergency Pre-emption System
-- Engine: MySQL InnoDB
-- ==============================================================================

-- 1. Ensure core operational tables exist if 01_schema.sql has not been run yet
CREATE TABLE IF NOT EXISTS beds (
    bed_id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
    ward_name         VARCHAR(255) NOT NULL,
    hospital_name     VARCHAR(255) NOT NULL,
    status            VARCHAR(50)  NOT NULL DEFAULT 'VACANT',
    patient_id        BIGINT       NULL,
    patient_name      VARCHAR(255) NULL,
    patient_triage    VARCHAR(50)  NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS medical_machines (
    machine_id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
    equipment_type       VARCHAR(50)  NOT NULL,
    serial_number        VARCHAR(100) NOT NULL,
    hospital_name        VARCHAR(255) NOT NULL,
    operating_hours_used DOUBLE       NOT NULL DEFAULT 0,
    under_maintenance    BOOLEAN      NOT NULL DEFAULT FALSE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS waitlist_entries (
    entry_id                BIGINT       PRIMARY KEY AUTO_INCREMENT,
    patient_id              BIGINT       NOT NULL,
    patient_name            VARCHAR(255) NOT NULL,
    triage_level            VARCHAR(50)  NOT NULL,
    requested_hospital_name VARCHAR(255) NOT NULL,
    requested_ward_name     VARCHAR(255) NOT NULL,
    request_time            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes                   TEXT         NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Seed Beds across Partner Hospitals
INSERT INTO beds (bed_id, ward_name, hospital_name, status, patient_id, patient_name, patient_triage) VALUES
(101, 'ICU-North', 'City Central Hospital', 'VACANT', NULL, NULL, NULL),
(102, 'ICU-North', 'City Central Hospital', 'OCCUPIED', 5001, 'Robert Chen', 'LEVEL_4'),
(103, 'ICU-South', 'City Central Hospital', 'OCCUPIED', 5002, 'Emily Davis', 'LEVEL_5'),
(104, 'General-Ward-A', 'City Central Hospital', 'REQUIRES_SANITIZATION', NULL, NULL, NULL),
(105, 'Isolation-Ward', 'City Central Hospital', 'RESTRICTED_BUFFER', NULL, NULL, NULL),

(201, 'ICU-Main', 'Memorial Care Hospital', 'VACANT', NULL, NULL, NULL),
(202, 'ICU-Main', 'Memorial Care Hospital', 'OCCUPIED', 5003, 'James Wilson', 'LEVEL_2'),
(203, 'Emergency-Ward', 'Memorial Care Hospital', 'VACANT', NULL, NULL, NULL),
(204, 'Cardiac-Care', 'Memorial Care Hospital', 'OCCUPIED', 5004, 'Maria Garcia', 'LEVEL_3'),

(301, 'ICU-Critical', 'Metro General Hospital', 'VACANT', NULL, NULL, NULL),
(302, 'ICU-Critical', 'Metro General Hospital', 'RESERVED_EN_ROUTE', NULL, NULL, NULL),
(303, 'Trauma-Unit', 'Metro General Hospital', 'OCCUPIED', 5005, 'Sarah Jenkins', 'LEVEL_5')
ON DUPLICATE KEY UPDATE status=VALUES(status);

-- 3. Seed Medical Machines (Ventilators, Oxygen, Dialysis, Cardiac Monitors)
INSERT INTO medical_machines (machine_id, equipment_type, serial_number, hospital_name, operating_hours_used, under_maintenance) VALUES
(1, 'VENTILATOR', 'VENT-CC-101', 'City Central Hospital', 120.5, FALSE),
(2, 'VENTILATOR', 'VENT-CC-102', 'City Central Hospital', 485.0, FALSE), -- Near safe limit (500h)
(3, 'OXYGEN',     'OXY-CC-201',  'City Central Hospital', 250.0, FALSE),
(4, 'DIALYSIS',   'DIA-CC-301',  'City Central Hospital', 290.0, FALSE), -- Near safe limit (300h)
(5, 'MONITOR',    'MON-CC-401',  'City Central Hospital', 800.0, FALSE),

(6, 'VENTILATOR', 'VENT-MC-101', 'Memorial Care Hospital', 510.0, TRUE),  -- Over limit, in repair
(7, 'OXYGEN',     'OXY-MC-201',  'Memorial Care Hospital', 920.0, FALSE), -- Near limit (1000h)
(8, 'MONITOR',    'MON-MC-401',  'Memorial Care Hospital', 1450.0, FALSE),-- Near limit (1500h)

(9, 'VENTILATOR', 'VENT-MG-101', 'Metro General Hospital', 50.0, FALSE),
(10, 'DIALYSIS',  'DIA-MG-301',  'Metro General Hospital', 110.0, FALSE)
ON DUPLICATE KEY UPDATE operating_hours_used=VALUES(operating_hours_used), under_maintenance=VALUES(under_maintenance);

-- 4. Seed Waitlist Entries
INSERT INTO waitlist_entries (entry_id, patient_id, patient_name, triage_level, requested_hospital_name, requested_ward_name, request_time, notes) VALUES
(1, 6001, 'Arthur Pendelton', 'LEVEL_1', 'City Central Hospital', 'ICU-North', DATE_SUB(NOW(), INTERVAL 25 MINUTE), 'Cardiac arrest resuscitation in ER'),
(2, 6002, 'David Miller',     'LEVEL_2', 'City Central Hospital', 'ICU-North', DATE_SUB(NOW(), INTERVAL 40 MINUTE), 'Severe respiratory distress, SpO2 82%'),
(3, 6003, 'Carlos Santana',   'LEVEL_3', 'Memorial Care Hospital', 'ICU-Main', DATE_SUB(NOW(), INTERVAL 60 MINUTE), 'Complicated post-op observation'),
(4, 6004, 'Angela White',     'LEVEL_5', 'City Central Hospital', 'ICU-North', DATE_SUB(NOW(), INTERVAL 90 MINUTE), 'Stable bed transfer')
ON DUPLICATE KEY UPDATE triage_level=VALUES(triage_level);

-- 5. Seed Ambulances
INSERT INTO ambulances (ambulance_id, license_plate, ambulance_type, current_hospital_name, is_available) VALUES
('AMB-ALS-101', 'KA-01-EQ-1001', 'ALS', 'City Central Hospital', TRUE),
('AMB-ALS-102', 'KA-01-EQ-1002', 'ALS', 'Memorial Care Hospital', TRUE),
('AMB-ALS-103', 'KA-01-EQ-1003', 'ALS', 'Metro General Hospital', TRUE),
('AMB-BLS-201', 'KA-02-TX-2001', 'BLS', 'City Central Hospital', TRUE),
('AMB-BLS-202', 'KA-02-TX-2002', 'BLS', 'Metro General Hospital', TRUE)
ON DUPLICATE KEY UPDATE is_available=VALUES(is_available);
