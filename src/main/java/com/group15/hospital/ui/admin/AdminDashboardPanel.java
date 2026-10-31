package com.group15.hospital.ui.admin;

import com.group15.hospital.model.hierarchy.AuditLogEntry;
import com.group15.hospital.model.hierarchy.Hospital;
import com.group15.hospital.service.strategy.HospitalAdminService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

// admin dashboard panel to manage hospitals and view audit logs
public class AdminDashboardPanel extends JPanel {

    private final HospitalAdminService adminService;
    private final String currentUsername;

    private DefaultTableModel hospitalTableModel;
    private DefaultTableModel auditTableModel;
    private JLabel capacityLabel;

    public AdminDashboardPanel(HospitalAdminService adminService, String currentUsername) {
        this.adminService = (adminService != null) ? adminService : new HospitalAdminService();
        this.currentUsername = (currentUsername != null) ? currentUsername : "admin";

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Hospitals & Bed Capacity", createHospitalsPanel());
        tabbedPane.addTab("Audit Log Diary", createAuditLogPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // Load initial data
        refreshHospitalTable();
        refreshAuditTable();
    }

    public AdminDashboardPanel() {
        this(new HospitalAdminService(), "admin");
    }

    // tab for managing hospitals
    private JPanel createHospitalsPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Header with summary capacity
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        capacityLabel = new JLabel("Total Network Bed Capacity: 0 beds");
        capacityLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        JButton refreshBtn = new JButton("Refresh Data");
        refreshBtn.addActionListener(e -> refreshHospitalTable());
        topPanel.add(capacityLabel);
        topPanel.add(Box.createHorizontalStrut(20));
        topPanel.add(refreshBtn);
        panel.add(topPanel, BorderLayout.NORTH);

        // Hospitals Table
        String[] columns = {"ID", "Hospital Name", "Address", "Latitude", "Longitude", "Phone", "Total Beds"};
        hospitalTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable table = new JTable(hospitalTableModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        // Bottom: Quick Add Hospital Form
        JPanel formPanel = new JPanel(new GridLayout(2, 6, 5, 5));
        formPanel.setBorder(BorderFactory.createTitledBorder("Register New Hospital"));

        JTextField nameField = new JTextField();
        JTextField addrField = new JTextField();
        JTextField latField = new JTextField("12.97");
        JTextField lonField = new JTextField("77.59");
        JTextField phoneField = new JTextField();
        JTextField bedsField = new JTextField("100");

        formPanel.add(new JLabel("Name:"));
        formPanel.add(new JLabel("Address:"));
        formPanel.add(new JLabel("Latitude:"));
        formPanel.add(new JLabel("Longitude:"));
        formPanel.add(new JLabel("Phone:"));
        formPanel.add(new JLabel("Beds:"));

        formPanel.add(nameField);
        formPanel.add(addrField);
        formPanel.add(latField);
        formPanel.add(lonField);
        formPanel.add(phoneField);
        formPanel.add(bedsField);

        JButton addBtn = new JButton("Add Hospital");
        addBtn.addActionListener(e -> {
            try {
                String name = nameField.getText().trim();
                String addr = addrField.getText().trim();
                double lat = Double.parseDouble(latField.getText().trim());
                double lon = Double.parseDouble(lonField.getText().trim());
                String phone = phoneField.getText().trim();
                int beds = Integer.parseInt(bedsField.getText().trim());

                if (name.isEmpty() || addr.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Hospital Name and Address are required.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (beds <= 0) {
                    JOptionPane.showMessageDialog(this, "Bed count must be greater than 0.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                    JOptionPane.showMessageDialog(this, "Please enter valid GPS coordinates (Latitude: -90 to 90, Longitude: -180 to 180).", "Validation Error", JOptionPane.WARNING_MESSAGE);
                    return;
                }

                adminService.registerHospital(currentUsername, name, addr, lat, lon, phone, beds);
                refreshHospitalTable();
                refreshAuditTable();

                nameField.setText("");
                addrField.setText("");
                phoneField.setText("");
                JOptionPane.showMessageDialog(this, "Hospital registered successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Please enter valid numbers for Latitude, Longitude, and Beds.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel bottomContainer = new JPanel(new BorderLayout(5, 5));
        bottomContainer.add(formPanel, BorderLayout.CENTER);
        bottomContainer.add(addBtn, BorderLayout.SOUTH);
        panel.add(bottomContainer, BorderLayout.SOUTH);

        return panel;
    }

    // tab for viewing audit logs
    private JPanel createAuditLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton refreshBtn = new JButton("Refresh Audit Log");
        refreshBtn.addActionListener(e -> refreshAuditTable());
        topBar.add(refreshBtn);
        panel.add(topBar, BorderLayout.NORTH);

        String[] columns = {"Log ID", "Timestamp", "Actor / User", "Action Type", "Entity ID", "Details"};
        auditTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable table = new JTable(auditTableModel);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);

        return panel;
    }

    public void refreshHospitalTable() {
        hospitalTableModel.setRowCount(0);
        List<Hospital> hospitals = adminService.getAllHospitals();
        for (Hospital h : hospitals) {
            hospitalTableModel.addRow(new Object[]{
                    h.getHospitalId(),
                    h.getName(),
                    h.getAddress(),
                    h.getLatitude(),
                    h.getLongitude(),
                    h.getContactPhone(),
                    h.getTotalBeds()
            });
        }
        capacityLabel.setText("Total Network Bed Capacity: " + adminService.getTotalNetworkBedCapacity() + " beds");
    }

    public void refreshAuditTable() {
        auditTableModel.setRowCount(0);
        List<AuditLogEntry> logs = adminService.getAuditTrail();
        for (AuditLogEntry log : logs) {
            String timeStr = (log.getTimestamp() != null)
                    ? log.getTimestamp().toString().replace('T', ' ')
                    : "";
            auditTableModel.addRow(new Object[]{
                    log.getLogId(),
                    timeStr,
                    log.getActorUsername(),
                    log.getActionType(),
                    log.getEntityId(),
                    log.getDetails()
            });
        }
    }
}
