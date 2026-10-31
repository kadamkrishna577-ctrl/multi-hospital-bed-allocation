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

    // functional interface for transactions that return a value...
    // execute(conn -> (some dao query fnctions here))
    // the <T> lets you output any type of dataType
    @FunctionalInterface
    public interface TransactionWork<T> {
        T execute(Connection connection) throws Exception;
    }

    // functional interface for void transactions so callers dont need to return null...
    // for fucntions that dont return anything
    // no need to write return null...
    // like update and other things...
    @FunctionalInterface
    public interface VoidTransactionWork {
        void execute(Connection connection) throws Exception;
    }

    private DatabaseConnection() {
        // loading the mysql driver explicitly so that older runtimes or scripts dont fail...
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            // driver might be auto loaded by jdbc 4 service provider mechanism...
        }

        // javas own key value map to read .properties files...
        Properties properties = new Properties();

        // first try db.properties, if not present try db.properties.example...
        InputStream input = getClass().getClassLoader().getResourceAsStream("db.properties");
        if (input == null) {
            input = getClass().getClassLoader().getResourceAsStream("db.properties.example");
        }

        if (input != null) {
            try (InputStream in = input) {
                properties.load(in);
            } catch (IOException e) {
                System.err.println("Warning: Could not read db properties, defaulting to local defaults");
            }
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
                    "Could not open the database connection for transaction", connEX);
        }
    }

    // overload for transactions that do not return any result...
    // so teammates dont have to write return null at the end of their lambda!
    public void executeTransactionVoid(VoidTransactionWork work) {
        executeTransaction(conn -> {
            work.execute(conn);
            return null;
        });
    }

    // helper getters for tests or debugging connections...
    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }
}

