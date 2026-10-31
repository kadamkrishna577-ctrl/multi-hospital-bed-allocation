package com.group15.hospital.exception;

// Thrown when two threads or users try to book or access the same bed at the same time
public class BedAlreadyBookedException extends RuntimeException {

    public BedAlreadyBookedException(String message) {
        super(message);
    }

    public BedAlreadyBookedException(String message, Throwable cause) {
        super(message, cause);
    }
}
