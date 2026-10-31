package com.group15.hospital.exception;

// whenever someone tries an invalid state transition — for example, trying to put a new Patient
// into a bed that is currently in RequiresSanitizationState or RestrictedBufferState!
public class IllegalBedStateException extends RuntimeException {

    public IllegalBedStateException(String message) {
        super(message);
    }

    public IllegalBedStateException(String message, Throwable cause) {
        super(message, cause);
    }
}
