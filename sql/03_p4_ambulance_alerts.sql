-- ============================================================================
-- Module P4: Ambulance Dispatch, Bed Reservations, and Real-Time Alerts Schema
-- Author: Siddhant (Group 15 - CS1207 OOP Lab)
-- Engine: MySQL InnoDB (ACID compliant transactions & row-level locking)
-- ============================================================================

-- 1. AMBULANCES TABLE
-- Tracks emergency response transit vehicles (ALS and BLS)
CREATE TABLE IF NOT EXISTS ambulances (
    ambulance_id           VARCHAR(50)  NOT NULL,
    license_plate          VARCHAR(50)  NOT NULL,
    ambulance_type         VARCHAR(20)  NOT NULL, -- 'ALS' or 'BLS'
    current_hospital_name  VARCHAR(255) NOT NULL,
    is_available           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (ambulance_id),
    INDEX idx_amb_type_avail (ambulance_type, is_available),
    INDEX idx_amb_hospital (current_hospital_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. AMBULANCE RESERVATIONS TABLE
-- Tracks pre-arrival ICU/Ward bed holds with 15-minute expiry windows (SRS F8, F9, U5)
CREATE TABLE IF NOT EXISTS ambulance_reservations (
    reservation_id          BIGINT       NOT NULL AUTO_INCREMENT,
    ambulance_id            VARCHAR(50)  NOT NULL,
    ambulance_type          VARCHAR(20)  NOT NULL,
    patient_name            VARCHAR(255) NOT NULL,
    patient_triage          VARCHAR(50)  NOT NULL,
    required_equipment      VARCHAR(500) NULL, -- Comma-separated list: "VENTILATOR,OXYGEN,MONITOR"
    hospital_name           VARCHAR(255) NOT NULL,
    ward_name               VARCHAR(255) NOT NULL,
    assigned_bed_id         BIGINT       NOT NULL,
    reserved_at             DATETIME     NOT NULL,
    expires_at              DATETIME     NOT NULL,
    status                  VARCHAR(50)  NOT NULL DEFAULT 'RESERVED_EN_ROUTE',
    rerouted_from_hospital  VARCHAR(255) NULL,
    PRIMARY KEY (reservation_id),
    INDEX idx_amb_res_status_expires (status, expires_at),
    INDEX idx_amb_res_hospital (hospital_name, status),
    INDEX idx_amb_res_bed (assigned_bed_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. SYSTEM ALERTS TABLE
-- Real-time notification queue for triage screens, cleaning, and dispatch (SRS F13, U5, U6)
CREATE TABLE IF NOT EXISTS system_alerts (
    alert_id                BIGINT       NOT NULL AUTO_INCREMENT,
    alert_type              VARCHAR(50)  NOT NULL, -- 'BED_CLEANED', 'AMBULANCE_RESERVED', 'AMBULANCE_REROUTED', etc.
    target_role             VARCHAR(50)  NOT NULL, -- 'NURSE', 'PARAMEDIC', 'CLEANING_STAFF', 'DOCTOR', 'ADMIN'
    hospital_name           VARCHAR(255) NOT NULL,
    bed_id                  BIGINT       NULL,
    message                 TEXT         NOT NULL,
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_acknowledged         BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (alert_id),
    INDEX idx_alerts_lookup (hospital_name, target_role, is_acknowledged),
    INDEX idx_alerts_recent (hospital_name, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Sample Seed Data for Module P4 testing
INSERT IGNORE INTO ambulances (ambulance_id, license_plate, ambulance_type, current_hospital_name, is_available) VALUES
('AMB-ALS-101', 'KA-01-EQ-1001', 'ALS', 'City Central Hospital', TRUE),
('AMB-ALS-102', 'KA-01-EQ-1002', 'ALS', 'Memorial Care Hospital', TRUE),
('AMB-BLS-201', 'KA-02-TX-2001', 'BLS', 'City Central Hospital', TRUE),
('AMB-BLS-202', 'KA-02-TX-2002', 'BLS', 'Metro General Hospital', TRUE);
