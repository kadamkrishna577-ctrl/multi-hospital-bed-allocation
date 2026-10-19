package com.group15.hospital.config;

// req ones are io exception and input stream...
import com.group15.hospital.exception.TransactionRollbackException;

import java.io.*;
import java.sql.*;
import java.util.Properties;

// marked final so that no other class can inherit from it
// and potentially break it...
// as this class is a singleton class...
public final class DatabaseConnection {

    // holds the single instance of the DatabaseConnection class
    // volatile so that when multi-threaded work runs
    // when a thread out of many finish the work the DatabaseConnection object
    // is directly visible to other thready rather than a value from a CPU register
    private static volatile DatabaseConnection instance;

    // final because when we read these from a system at startup
    // they shouldnt change then as this is the main root password kind of
    private final String url;
    private final String username;
    private final String password;

    // so that a normal lamba function can be formed for the connection to connect
    // execute(conn -> (some dao query fnctions here))
    // the <T> lets you output any type of dataType
    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }

    private DatabaseConnection() {
        // javas own key value map to read .properties files...
        Properties properties = new Properties();

        try (InputStream input = getClass().getClassLoader().getResourceAsStream("db.properties")) {
            // if the user created db.properties
            // load the key value pairs
            if (input != null) {
                properties.load(input);
            }
        } catch (IOException e) {
            System.err.println(
                    "Warning: Could not read db.properties defaulting to local defaults");
        }
        // here error is thrown because we have a fallback credential so that
        // the program can keep running...

        this.username = properties.getProperty("db.user", "root");
        this.url =
                properties.getProperty(
                        "db.url",
                        "jdbc:mysql://localhost:3306/hospital_bed_db?useSSL=false&allowPublicKeyRetrieval=true");
        this.password = properties.getProperty("db.password", "root");
    }

    // access point to get the global singleton class...
    public static DatabaseConnection getInstance() {
        // if the instance already exists then return it
        if (instance == null) {
            // now we have to make sure that only one thread makes the instance
            // and then the class instance is locked...
            synchronized (DatabaseConnection.class) {
                // if still two threads enter the if statement at the exact millisec
                // to make sure only one can make the instance...
                if (instance == null) {
                    instance = new DatabaseConnection();
                }
            }
        }

        return instance;
    }

    // opening a new connection to jdbc
    // because having only a connection in a multi-threaded
    // can delete and update a table at the same time
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    // ACID transaction helper method...
    public <T> T executeTransaction(TransactionWork<T> work) {
        // open a new connection
        try (Connection conn = getConnection()) {
            // save the default auto commit state...
            boolean original_auto_commit = conn.getAutoCommit();

            // turn off auto commit so that all the other updation halt
            // for the databse...
            conn.setAutoCommit(false);

            try {
                // running all the dao queries passed...
                T result = work.execute(conn);

                // if the query worked without error
                // commiting them
                conn.commit();

                return result;
            } catch (Exception e) {
                try {
                    // undo all the changes done until now
                    // so that the db isnt in some half
                    // updated state...
                    conn.rollback();
                } catch (SQLException rollbackEX) {
                    // even the rollback failed... add that exception too
                    e.addSuppressed(rollbackEX);
                }

                // finally throw all the errors..
                throw new TransactionRollbackException("Transaction failed and was rolled back", e);
            } finally {
                // restoring the commit we saved before executing all the queries
                // done in finally so that always runs...
                conn.setAutoCommit(original_auto_commit);
            }
        } catch (SQLException connEX) {
            throw new TransactionRollbackException(
                    "Could not open the database connection for transacrtion", connEX);
        }
    }
}
