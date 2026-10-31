package com.group15.hospital.exception;

// A MedicalMachine's operatingHours exceeds its safeLimitHours without repair, OR
// Someone tries to attach more equipment decorators to a bed than the room/ward supports.
public class EquipmentLimitExceededException extends RuntimeException {

    public EquipmentLimitExceededException(String message) {
        super(message);
    }

    public EquipmentLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}
