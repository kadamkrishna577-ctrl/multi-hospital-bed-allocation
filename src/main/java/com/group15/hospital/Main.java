package com.group15.hospital;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.ui.LoginFrame;

import javax.swing.*;
import java.util.logging.Level;
import java.util.logging.Logger;

// main class to start the application
public class Main {

    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        LOGGER.info("Starting Multi-Hospital Emergency Bed Allocation System...");

        // 1. Set System Look and Feel for clean native UI appearance
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Using default Swing Look and Feel.");
        }

        // 2. Pre-load Database Connection in background
        new Thread(() -> {
            try {
                DatabaseConnection.getInstance().getConnection().close();
                LOGGER.info("Database connection established successfully.");
            } catch (Exception e) {
                LOGGER.warning("Running in local fallback mode (MySQL not reached: " + e.getMessage() + ")");
            }
        }).start();

        // 3. Launch UI on Event Dispatch Thread (EDT)
        SwingUtilities.invokeLater(() -> {
            LoginFrame loginFrame = new LoginFrame();
            loginFrame.setVisible(true);
        });
    }
}
