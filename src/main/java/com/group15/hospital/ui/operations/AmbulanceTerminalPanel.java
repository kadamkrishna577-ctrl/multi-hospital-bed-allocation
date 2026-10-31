package com.group15.hospital.ui.operations;

import com.group15.hospital.dao.AmbulanceDAO;
import com.group15.hospital.exception.BedAlreadyBookedException;
import com.group15.hospital.model.ambulance.Ambulance;
import com.group15.hospital.model.ambulance.AmbulanceReservation;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.service.dispatch.AmbulanceDispatchService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

/**
 * Paramedic In-Transit Console Panel (SRS F8, F9, F13 & Use Case U5).
 * <p>
 * Provides pre-arrival ICU/Ward bed reservation, severity compatibility validation with warnings
 * (U5 Exception 3), live countdown timer for 15-minute hold windows, arrival confirmation,
 * ETA extensions, and capacity-loss partner rerouting in under 1.5 seconds.
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class AmbulanceTerminalPanel extends JPanel {

    private final AmbulanceDAO ambulanceDAO;
    private final AmbulanceDispatchService dispatchService;

    // Reservation Form Components
    private JComboBox<String> ambulanceCombo;
    private JTextField patientNameField;
    private JComboBox<TriageLevel> triageCombo;
    private JCheckBox ventBox;
    private JCheckBox oxyBox;
    private JCheckBox dialBox;
    private JCheckBox monBox;
    private JComboBox<String> hospitalCombo;
    private JSpinner etaSpinner;

    // Active Reservations Table
    private DefaultTableModel tableModel;
    private JTable reservationsTable;
    private javax.swing.Timer countdownTimer;

    public AmbulanceTerminalPanel(AmbulanceDAO ambulanceDAO, AmbulanceDispatchService dispatchService) {
        this.ambulanceDAO = Objects.requireNonNull(ambulanceDAO, "ambulanceDAO must not be null");
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService must not be null");

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // Top Banner
        add(createHeaderBanner(), BorderLayout.NORTH);

        // Center Split: Left = Reservation Form, Right = Active Fleet Table
        JPanel mainContent = new JPanel(new BorderLayout(10, 10));
        mainContent.add(createReservationFormPanel(), BorderLayout.WEST);
        mainContent.add(createActiveReservationsPanel(), BorderLayout.CENTER);
        add(mainContent, BorderLayout.CENTER);

        // Start 1-second countdown refresh timer
        startCountdownTimer();

        // Load initial data
        refreshAmbulanceDropdown();
        refreshReservationsTable();
    }

    private JPanel createHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(21, 101, 192)); // Royal Medical Blue
        banner.setBorder(new EmptyBorder(12, 15, 12, 15));

        JLabel title = new JLabel("🚑 AMBULANCE EMERGENCY DISPATCH & RESERVATION TERMINAL");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Module P4: Pre-Arrival Holds • Dynamic Reroute (< 1.5s) • Real-Time Alerts");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(227, 242, 253));

        banner.add(title, BorderLayout.WEST);
        banner.add(subtitle, BorderLayout.EAST);
        return banner;
    }

    // ──────────────────────────── Reservation Form ────────────────────────────

    private JPanel createReservationFormPanel() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setPreferredSize(new Dimension(360, 0));
        form.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(180, 180, 180), 1), "Pre-Arrival Bed Reservation (U5)",
                        TitledBorder.LEADING, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13)),
                new EmptyBorder(10, 10, 10, 10)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        // 1. Ambulance Selection
        gbc.gridx = 0; gbc.gridy = row++;
        form.add(new JLabel("Dispatch Ambulance Unit:"), gbc);

        ambulanceCombo = new JComboBox<>();
        ambulanceCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridy = row++;
        form.add(ambulanceCombo, gbc);

        // 2. Patient Name
        gbc.gridy = row++;
        form.add(new JLabel("Patient Name / Emergency Tag:"), gbc);

        patientNameField = new JTextField("Emergency Trauma Case");
        patientNameField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridy = row++;
        form.add(patientNameField, gbc);

        // 3. Triage Level
        gbc.gridy = row++;
        form.add(new JLabel("Clinical Triage Acuity (Levels 1 - 5):"), gbc);

        triageCombo = new JComboBox<>(TriageLevel.values());
        triageCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        triageCombo.setSelectedItem(TriageLevel.LEVEL_1);
        gbc.gridy = row++;
        form.add(triageCombo, gbc);

        // 4. Equipment Checkboxes
        gbc.gridy = row++;
        form.add(new JLabel("Required Life-Support Equipment:"), gbc);

        JPanel equipPanel = new JPanel(new GridLayout(2, 2, 5, 5));
        ventBox = new JCheckBox("Ventilator", true);
        oxyBox = new JCheckBox("Oxygen", true);
        dialBox = new JCheckBox("Dialysis", false);
        monBox = new JCheckBox("Cardiac Monitor", true);
        equipPanel.add(ventBox);
        equipPanel.add(oxyBox);
        equipPanel.add(dialBox);
        equipPanel.add(monBox);

        gbc.gridy = row++;
        form.add(equipPanel, gbc);

        // 5. Destination Hospital
        gbc.gridy = row++;
        form.add(new JLabel("Target Destination Hospital:"), gbc);

        hospitalCombo = new JComboBox<>(new String[]{
                "City Central Hospital",
                "Memorial Care Hospital",
                "Metro General Hospital",
                "St. Jude Emergency Center"
        });
        hospitalCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridy = row++;
        form.add(hospitalCombo, gbc);

        // 6. Estimated Arrival (Minutes)
        gbc.gridy = row++;
        form.add(new JLabel("Estimated Arrival Window (Minutes):"), gbc);

        etaSpinner = new JSpinner(new SpinnerNumberModel(15, 5, 60, 5));
        etaSpinner.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        gbc.gridy = row++;
        form.add(etaSpinner, gbc);

        // Spacer
        gbc.gridy = row++;
        gbc.weighty = 1.0;
        form.add(Box.createVerticalGlue(), gbc);
        gbc.weighty = 0.0;

        // Reserve Action Button
        JButton reserveButton = new JButton("📡 Reserve Bed En Route");
        reserveButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        reserveButton.setBackground(new Color(46, 125, 50)); // Forest Green
        reserveButton.setForeground(Color.WHITE);
        reserveButton.setFocusPainted(false);
        reserveButton.addActionListener(e -> handleReservationSubmission());

        gbc.gridy = row++;
        form.add(reserveButton, gbc);

        return form;
    }

    // ──────────────────────────── Active Reservations Table & Controls ────────────────────────────

    private JPanel createActiveReservationsPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(180, 180, 180), 1), "In-Flight Ambulance Holds & Live Status",
                        TitledBorder.LEADING, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13)),
                new EmptyBorder(8, 8, 8, 8)
        ));

        String[] headers = {
                "Res ID", "Ambulance", "Type", "Patient", "Triage", "Hospital", "Ward", "Bed #", "Window Countdown", "Status"
        };

        tableModel = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        reservationsTable = new JTable(tableModel);
        reservationsTable.setRowHeight(28);
        reservationsTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        reservationsTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        reservationsTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Status Renderer
        reservationsTable.getColumnModel().getColumn(9).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String status = value.toString();
                    if (status.contains("RESERVED")) {
                        setForeground(new Color(21, 101, 192));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("OCCUPIED") || status.contains("ARRIVED")) {
                        setForeground(new Color(46, 125, 50));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("REROUTED")) {
                        setForeground(new Color(230, 81, 0));
                        setFont(getFont().deriveFont(Font.BOLD));
                    } else if (status.contains("EXPIRED")) {
                        setForeground(Color.RED);
                        setFont(getFont().deriveFont(Font.BOLD));
                    }
                }
                return c;
            }
        });

        panel.add(new JScrollPane(reservationsTable), BorderLayout.CENTER);

        // Action Toolbar
        JPanel actionToolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

        JButton admitBtn = new JButton("✓ Confirm Arrival & Admit (OCCUPIED)");
        admitBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        admitBtn.setBackground(new Color(46, 125, 50));
        admitBtn.setForeground(Color.WHITE);
        admitBtn.setFocusPainted(false);
        admitBtn.addActionListener(e -> handleConfirmArrival());

        JButton extendBtn = new JButton("⏱ Extend ETA (+5 Min)");
        extendBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        extendBtn.addActionListener(e -> handleExtendWindow());

        JButton rerouteBtn = new JButton("⚡ Simulate Hospital Full / Reroute to Partner (< 1.5s)");
        rerouteBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        rerouteBtn.setBackground(new Color(230, 81, 0)); // Amber Orange
        rerouteBtn.setForeground(Color.WHITE);
        rerouteBtn.setFocusPainted(false);
        rerouteBtn.addActionListener(e -> handleSimulateReroute());

        JButton refreshBtn = new JButton("🔄 Refresh / Seed Demo Fleet");
        refreshBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        refreshBtn.addActionListener(e -> {
            seedDemoBedsAndFleet();
            refreshAmbulanceDropdown();
            refreshReservationsTable();
            JOptionPane.showMessageDialog(this, "Demo fleet and vacant hospital beds seeded successfully!", "Fleet Ready", JOptionPane.INFORMATION_MESSAGE);
        });

        actionToolbar.add(admitBtn);
        actionToolbar.add(extendBtn);
        actionToolbar.add(rerouteBtn);
        actionToolbar.add(refreshBtn);

        panel.add(actionToolbar, BorderLayout.SOUTH);
        return panel;
    }

    // ──────────────────────────── Business Actions ────────────────────────────

    private void handleReservationSubmission() {
        String selectedAmbStr = (String) ambulanceCombo.getSelectedItem();
        if (selectedAmbStr == null || selectedAmbStr.isBlank()) {
            JOptionPane.showMessageDialog(this, "Please select an ambulance unit.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String ambulanceId = selectedAmbStr.split(" ")[0].trim();
        String patientName = patientNameField.getText().trim();
        TriageLevel triage = (TriageLevel) triageCombo.getSelectedItem();
        String hospital = (String) hospitalCombo.getSelectedItem();

        Set<EquipmentType> equipment = new HashSet<>();
        if (ventBox.isSelected()) equipment.add(EquipmentType.VENTILATOR);
        if (oxyBox.isSelected()) equipment.add(EquipmentType.OXYGEN);
        if (dialBox.isSelected()) equipment.add(EquipmentType.DIALYSIS);
        if (monBox.isSelected()) equipment.add(EquipmentType.MONITOR);

        // 1. Clinical Severity Compatibility Check (SRS F9 & U5 Exception 3)
        Optional<Ambulance> ambOpt = ambulanceDAO.findAmbulanceById(ambulanceId);
        if (ambOpt.isPresent()) {
            Ambulance amb = ambOpt.get();
            Optional<String> warning = amb.getSeverityWarning(triage, equipment);
            if (warning.isPresent()) {
                int choice = JOptionPane.showConfirmDialog(
                        this,
                        "⚠ CLINICAL SEVERITY MISMATCH WARNING (U5 Exception 3):\n\n" + warning.get() +
                                "\n\nDo you still wish to proceed with this reservation?",
                        "Paramedic Safety Warning",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE
                );
                if (choice != JOptionPane.YES_OPTION) {
                    return; // Paramedic aborted reservation upon clinical warning
                }
            }
        }

        // 2. Execute reservation through Dispatch Service (< 1.5s)
        try {
            AmbulanceReservation res = dispatchService.requestBedReservation(
                    ambulanceId, triage, equipment, hospital, patientName
            );

            JOptionPane.showMessageDialog(
                    this,
                    String.format("✓ Reservation Confirmed!\n\nReservation ID: #%d\nBed Assigned: Bed #%d (%s)\nDestination: %s\nHold Window: 15 Minutes",
                            res.getReservationId(), res.getAssignedBedId(), res.getWardName(), res.getHospitalName()),
                    "Reservation Created",
                    JOptionPane.INFORMATION_MESSAGE
            );

            refreshAmbulanceDropdown();
            refreshReservationsTable();
        } catch (BedAlreadyBookedException bbe) {
            JOptionPane.showMessageDialog(this, bbe.getMessage(), "Hospital At Capacity", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Reservation failed: " + ex.getMessage(), "Dispatch Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleConfirmArrival() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an in-flight reservation from the table.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long resId = (Long) tableModel.getValueAt(row, 0);
        try {
            ambulanceDAO.confirmArrivalAtomic(resId);
            JOptionPane.showMessageDialog(this, "Patient arrival confirmed! Bed status transitioned to OCCUPIED.", "Patient Admitted", JOptionPane.INFORMATION_MESSAGE);
            refreshReservationsTable();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error confirming arrival: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleExtendWindow() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an in-flight reservation to extend.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long resId = (Long) tableModel.getValueAt(row, 0);
        boolean extended = ambulanceDAO.extendReservationWindow(resId, 5);
        if (extended) {
            JOptionPane.showMessageDialog(this, "Hold window extended by +5 minutes (U5 Alt Flow 2).", "Window Extended", JOptionPane.INFORMATION_MESSAGE);
            refreshReservationsTable();
        } else {
            JOptionPane.showMessageDialog(this, "Could not extend window (reservation may already be inactive).", "Notice", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void handleSimulateReroute() {
        int row = reservationsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a reservation to reroute.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long resId = (Long) tableModel.getValueAt(row, 0);
        try {
            long start = System.currentTimeMillis();
            AmbulanceReservation rerouted = dispatchService.checkAndRerouteIfFull(resId, true);
            long elapsed = System.currentTimeMillis() - start;

            JOptionPane.showMessageDialog(
                    this,
                    String.format("⚡ REROUTE SUCCESSFUL (< 1.5s)!\n\nExecution Time: %d ms\nOriginal Hospital: %s\nNew Destination: %s\nNew Bed: Bed #%d (%s)\n\nTriage pop-up alert delivered to partner terminal.",
                            elapsed, rerouted.getReroutedFromHospital(), rerouted.getHospitalName(), rerouted.getAssignedBedId(), rerouted.getWardName()),
                    "Partner Hospital Reroute Complete",
                    JOptionPane.INFORMATION_MESSAGE
            );
            refreshReservationsTable();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Reroute failed: " + ex.getMessage(), "Reroute Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ──────────────────────────── Table & Countdown Refresh ────────────────────────────

    private void startCountdownTimer() {
        countdownTimer = new javax.swing.Timer(1000, e -> updateCountdownValues());
        countdownTimer.start();
    }

    private void updateCountdownValues() {
        LocalDateTime now = LocalDateTime.now();
        List<AmbulanceReservation> list = ambulanceDAO.findAllActiveReservations();

        // Update countdown column (index 8) for each row
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            long resId = (Long) tableModel.getValueAt(i, 0);
            for (AmbulanceReservation r : list) {
                if (r.getReservationId() == resId) {
                    long remainingSec = r.getRemainingSeconds(now);
                    String timeStr;
                    if (remainingSec <= 0) {
                        timeStr = "EXPIRED (00:00)";
                    } else {
                        long mins = remainingSec / 60;
                        long secs = remainingSec % 60;
                        timeStr = String.format("%02d:%02d remaining", mins, secs);
                    }
                    tableModel.setValueAt(timeStr, i, 8);
                    break;
                }
            }
        }
    }

    public void refreshReservationsTable() {
        tableModel.setRowCount(0);
        List<AmbulanceReservation> reservations = ambulanceDAO.findAllActiveReservations();
        LocalDateTime now = LocalDateTime.now();

        for (AmbulanceReservation r : reservations) {
            long remSec = r.getRemainingSeconds(now);
            long m = remSec / 60;
            long s = remSec % 60;
            String cd = (remSec <= 0) ? "EXPIRED" : String.format("%02d:%02d remaining", m, s);

            tableModel.addRow(new Object[]{
                    r.getReservationId(),
                    r.getAmbulanceId(),
                    r.getAmbulanceType(),
                    r.getPatientName(),
                    r.getPatientTriage(),
                    r.getHospitalName(),
                    r.getWardName(),
                    r.getAssignedBedId(),
                    cd,
                    r.getStatus()
            });
        }
    }

    public void refreshAmbulanceDropdown() {
        ambulanceCombo.removeAllItems();
        List<Ambulance> fleet = ambulanceDAO.findAllAmbulances();
        for (Ambulance a : fleet) {
            String label = String.format("%s (%s) — %s [%s]",
                    a.getAmbulanceId(), a.getAmbulanceType(), a.getCurrentHospitalName(),
                    a.isAvailable() ? "AVAILABLE" : "DISPATCHED");
            ambulanceCombo.addItem(label);
        }
    }

    public void seedDemoBedsAndFleet() {
        // Seed partner hospital beds across the network
        dispatchService.seedSimulatedBed("City Central Hospital", 101L, BedStatus.VACANT);
        dispatchService.seedSimulatedBed("City Central Hospital", 102L, BedStatus.VACANT);
        dispatchService.seedSimulatedBed("Memorial Care Hospital", 201L, BedStatus.VACANT);
        dispatchService.seedSimulatedBed("Memorial Care Hospital", 202L, BedStatus.VACANT);
        dispatchService.seedSimulatedBed("Metro General Hospital", 301L, BedStatus.VACANT);
        dispatchService.seedSimulatedBed("St. Jude Emergency Center", 401L, BedStatus.VACANT);
    }

    public DefaultTableModel getTableModel() {
        return tableModel;
    }

    public JTable getReservationsTable() {
        return reservationsTable;
    }
}
