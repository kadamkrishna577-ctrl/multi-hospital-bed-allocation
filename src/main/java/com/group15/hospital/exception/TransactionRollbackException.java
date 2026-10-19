package com.group15.hospital.exception;

// if a database transaction fails halfway through
// exception for the transaction failing
public class TransactionRollbackException extends RuntimeException {
    // main constr
    // takes in the main error(SQL Exception) and also our custom message
    // so that we can know the error and also the error message we made...
    public TransactionRollbackException(String message, Throwable error) {
        super(message, error);
    }

    // normal exception handling if anyone has to throw the exception with a custom message
    public TransactionRollbackException(String message) {
        super(message);
    }
}
