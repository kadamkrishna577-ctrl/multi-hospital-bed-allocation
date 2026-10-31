CREATE DATABASE IF NOT EXISTS hospital_bed_db;
USE hospital_bed_db;

DROP TABLE IF EXISTS system_alerts;
DROP TABLE IF EXISTS ambulance_reservations;
DROP TABLE IF EXISTS ambulances;
DROP TABLE IF EXISTS kitchen_meal_orders;
DROP TABLE IF EXISTS billing_line_items;
DROP TABLE IF EXISTS billing_records;
DROP TABLE IF EXISTS ward_topology;
DROP TABLE IF EXISTS waitlist_entries;
DROP TABLE IF EXISTS medical_machines;
DROP TABLE IF EXISTS audit_logs;
DROP TABLE IF EXISTS users;
DROP TABLE IF EXISTS patients;
DROP TABLE IF EXISTS beds;
DROP TABLE IF EXISTS rooms;
DROP TABLE IF EXISTS wards;
DROP TABLE IF EXISTS hospitals;


CREATE TABLE hospitals(
    hospital_id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    address VARCHAR(255) NOT NULL,
    latitude DECIMAL(10, 6) NOT NULL,
    longitude DECIMAL(10, 6) NOT NULL,
    contact_phone VARCHAR(25) NOT NULL,
    total_beds INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wards (
    ward_id INT AUTO_INCREMENT PRIMARY KEY,
    hospital_id INT NOT NULL,
    ward_name VARCHAR(100) NOT NULL,
    ward_type VARCHAR(50) NOT NULL, 
    floor_number INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ward_hospital 
        FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id) 
        ON DELETE CASCADE
);

CREATE TABLE rooms (
    room_id INT AUTO_INCREMENT PRIMARY KEY,
    ward_id INT NOT NULL,
    room_number VARCHAR(20) NOT NULL,
    is_negative_pressure BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_ward 
        FOREIGN KEY (ward_id) REFERENCES wards(ward_id) 
        ON DELETE CASCADE
);

-- 4. BEDS TABLE
-- Supports both Saksham's Room hierarchy AND Krishna/Siddhant's DAO queries.
-- Contains denormalized hospital_name, ward_name, and patient fields for fast lookups
-- and atomic optimistic-locking booking in BedDAO and AmbulanceDAO.
CREATE TABLE beds (
    bed_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    room_id           INT NULL,
    bed_number        VARCHAR(30) NULL,
    ward_name         VARCHAR(255) NOT NULL DEFAULT 'General Ward',
    hospital_name     VARCHAR(255) NOT NULL DEFAULT 'City Central Hospital',
    status            VARCHAR(50) NOT NULL DEFAULT 'VACANT',
    patient_id        BIGINT NULL,
    patient_name      VARCHAR(255) NULL,
    patient_triage    VARCHAR(50) NULL,
    base_hourly_rate  DECIMAL(10, 2) NOT NULL DEFAULT 50.00,
    created_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_bed_room 
        FOREIGN KEY (room_id) REFERENCES rooms(room_id) 
        ON DELETE CASCADE
);

CREATE INDEX idx_bed_status ON beds(status);
CREATE INDEX idx_bed_hospital ON beds(hospital_name, status);

CREATE TABLE patients (
    patient_id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(10) NOT NULL,
    triage_level VARCHAR(20) NOT NULL DEFAULT 'LEVEL_5',
    assigned_bed_id BIGINT NULL,
    diet_type VARCHAR(30) NOT NULL DEFAULT 'REGULAR', 
    admission_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    discharge_time TIMESTAMP NULL DEFAULT NULL,
    is_discharged BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_patient_bed 
        FOREIGN KEY (assigned_bed_id) REFERENCES beds(bed_id) 
        ON DELETE SET NULL
);

CREATE INDEX idx_patient_bed ON patients(assigned_bed_id);
CREATE INDEX idx_patient_triage ON patients(triage_level);

CREATE TABLE users (
    user_id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(30) NOT NULL,
    hospital_id INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_hospital 
        FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id) 
        ON DELETE SET NULL
);

CREATE TABLE audit_logs (
    log_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_username VARCHAR(50) NOT NULL DEFAULT 'system',
    action_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NOT NULL DEFAULT '',
    details TEXT NOT NULL
);

CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_actor ON audit_logs(actor_username);



-- 8. MEDICAL MACHINES TABLE
-- Matches Krishna's EquipmentDAO.java and BedEquipmentDecorator
CREATE TABLE medical_machines (
    machine_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    bed_id               BIGINT NULL,
    equipment_type       VARCHAR(50) NOT NULL, -- VENTILATOR, OXYGEN, DIALYSIS, MONITOR
    serial_number        VARCHAR(100) NOT NULL UNIQUE,
    hospital_name        VARCHAR(255) NOT NULL DEFAULT 'City Central Hospital',
    operating_hours_used DOUBLE NOT NULL DEFAULT 0.0,
    safe_limit_hours     DOUBLE NOT NULL DEFAULT 500.0,
    under_maintenance    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_machine_bed 
        FOREIGN KEY (bed_id) REFERENCES beds(bed_id) 
        ON DELETE SET NULL
);

CREATE INDEX idx_machine_type ON medical_machines(equipment_type);
CREATE INDEX idx_machine_hospital ON medical_machines(hospital_name);

-- 9. WAITLIST ENTRIES TABLE
-- Matches Krishna's WaitlistDAO.java and Siddhant's SanitizationService
CREATE TABLE waitlist_entries (
    entry_id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id              BIGINT NOT NULL,
    patient_name            VARCHAR(255) NOT NULL,
    triage_level            VARCHAR(50) NOT NULL,
    requested_hospital_name VARCHAR(255) NOT NULL DEFAULT 'City Central Hospital',
    requested_ward_name     VARCHAR(255) NOT NULL DEFAULT 'ICU Ward A',
    request_time            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    notes                   TEXT NULL,
    INDEX idx_waitlist_priority (triage_level, request_time),
    INDEX idx_waitlist_hosp (requested_hospital_name)
);

CREATE TABLE ward_topology (
    topology_id INT AUTO_INCREMENT PRIMARY KEY,
    bed_id_1 BIGINT NOT NULL,
    bed_id_2 BIGINT NOT NULL,
    relationship_type VARCHAR(30) NOT NULL DEFAULT 'NEIGHBOR', 
    CONSTRAINT fk_topo_bed1 
        FOREIGN KEY (bed_id_1) REFERENCES beds(bed_id) 
        ON DELETE CASCADE,
    CONSTRAINT fk_topo_bed2 
        FOREIGN KEY (bed_id_2) REFERENCES beds(bed_id) 
        ON DELETE CASCADE
);

CREATE TABLE billing_records (
    bill_id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL DEFAULT 0.00,
    is_paid BOOLEAN NOT NULL DEFAULT FALSE,
    generated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP NULL DEFAULT NULL,
    CONSTRAINT fk_billing_patient 
        FOREIGN KEY (patient_id) REFERENCES patients(patient_id) 
        ON DELETE CASCADE
);

CREATE TABLE billing_line_items (
    item_id INT AUTO_INCREMENT PRIMARY KEY,
    bill_id INT NOT NULL,
    description VARCHAR(255) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_line_bill 
        FOREIGN KEY (bill_id) REFERENCES billing_records(bill_id) 
        ON DELETE CASCADE
);

CREATE TABLE kitchen_meal_orders (
    order_id INT AUTO_INCREMENT PRIMARY KEY,
    patient_id INT NOT NULL,
    diet_type VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ordered_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_meal_patient 
        FOREIGN KEY (patient_id) REFERENCES patients(patient_id) 
        ON DELETE CASCADE
);



-- ==============================================================================
-- SECTION 4: AMBULANCES & SYSTEM ALERTS [Siddhant - Phase 4]
-- Matches sql/03_p4_ambulance_alerts.sql
-- ==============================================================================

-- 14. AMBULANCES TABLE
-- Tracks emergency response transit vehicles (ALS and BLS)
CREATE TABLE ambulances (
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

-- 15. AMBULANCE RESERVATIONS TABLE
-- Tracks pre-arrival ICU/Ward bed holds with 15-minute expiry windows
CREATE TABLE ambulance_reservations (
    reservation_id          BIGINT       NOT NULL AUTO_INCREMENT,
    ambulance_id            VARCHAR(50)  NOT NULL,
    ambulance_type          VARCHAR(20)  NOT NULL,
    patient_name            VARCHAR(255) NOT NULL,
    patient_triage          VARCHAR(50)  NOT NULL,
    required_equipment      VARCHAR(500) NULL, -- Comma-separated: "VENTILATOR,OXYGEN,MONITOR"
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

-- 16. SYSTEM ALERTS TABLE
-- Real-time notification queue for triage screens, cleaning, and dispatch
CREATE TABLE system_alerts (
    alert_id                BIGINT       NOT NULL AUTO_INCREMENT,
    alert_type              VARCHAR(50)  NOT NULL, -- 'BED_CLEANED', 'AMBULANCE_RESERVED', etc.
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



DELIMITER //

CREATE TRIGGER trg_audit_bed_status_change
AFTER UPDATE ON beds
FOR EACH ROW
BEGIN
    IF OLD.status <> NEW.status THEN
        INSERT INTO audit_logs (actor_username, action_type, entity_id, details)
        VALUES (
            'SYSTEM_TRIGGER',
            'BED_STATUS_UPDATE',
            CAST(NEW.bed_id AS CHAR),
            CONCAT('Bed #', CAST(NEW.bed_id AS CHAR), ' status changed from [', OLD.status, '] to [', NEW.status, ']')
        );
    END IF;
END //

CREATE TRIGGER trg_audit_patient_admission
AFTER INSERT ON patients
FOR EACH ROW
BEGIN
    IF NEW.assigned_bed_id IS NOT NULL THEN
        INSERT INTO audit_logs (actor_username, action_type, entity_id, details)
        VALUES (
            'SYSTEM_TRIGGER',
            'PATIENT_ADMITTED',
            CAST(NEW.patient_id AS CHAR),
            CONCAT('Patient ', NEW.full_name, ' (Triage: ', NEW.triage_level, ') admitted to bed ID: ', NEW.assigned_bed_id)
        );
    END IF;
END //

DELIMITER ;



