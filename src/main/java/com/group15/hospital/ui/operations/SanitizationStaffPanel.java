package com.group15.hospital.ui.operations;

import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.state.BedState;
import com.group15.hospital.service.dispatch.SanitizationService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Ward Cleaning & Terminal Sanitization Staff Terminal (SRS F10, F13 & Use Case U6).
 * <p>
 * Displays discharged beds held in {@link BedStatus#REQUIRES_SANITIZATION}. Provides one-click
 * terminal cleaning confirmation (U6 Step 2 & Exception 1), prevents sanitizing beds in invalid
 * states (U6 Exception 2), triggers real-time &lt; 0.5s pop-up alerts on triage screens upon
 * completion, and detects waiting patients via WaitlistManager (U6 Alt Flow 1).
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class SanitizationStaffPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final SanitizationService sanitizationService;

    // Track dirty beds in memory for the UI terminal
    private static class DirtyBedRecord {
        long bedId;
        String hospitalName;
        String wardName;
        LocalDateTime dischargedAt;

        DirtyBedRecord(long bedId, String hospitalName, String wardName, LocalDateTime dischargedAt) {
            this.bedId = bedId;
            this.hospitalName = hospitalName;
            this.wardName = wardName;
            this.dischargedAt = dischargedAt;
        }
    }

    private final Map<Long, DirtyBedRecord> dirtyBedRegistry = new ConcurrentHashMap<>();
    private final AtomicLong simulatedBedCounter = new AtomicLong(501);

    // UI Components
    private JComboBox<String> hospitalFilterCombo;
    private JTextField staffNameField;
    private DefaultTableModel dirtyTableModel;
    private JTable dirtyTable;
    private DefaultTableModel logTableModel;

    public SanitizationStaffPanel(SanitizationService sanitizationService) {
        this.sanitizationService = Objects.requireNonNull(sanitizationService, "sanitizationService must not be null");

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Header Banner
        add(createHeaderBanner(), BorderLayout.NORTH);

        // 2. Center Content Split
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createDirtyRoomsPanel(),
                createHistoryLogPanel());
        splitPane.setResizeWeight(0.65);
        splitPane.setBorder(null);
        add(splitPane, BorderLayout.CENTER);

        // Seed sample dirty rooms for evaluation demo
        seedDefaultDirtyBeds();
        refreshTable();
    }

    private JPanel createHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(46, 125, 50)); // Sanitization Medical Green
        banner.setBorder(new EmptyBorder(12, 15, 12, 15));

        JLabel title = new JLabel("🧹 WARD SANITIZATION & TERMINAL CLEANING CONSOLE");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Module P4: Room Clean Confirmation • Real-Time Triage Alerts (< 0.5s) • Waitlist Matching");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(232, 245, 233));

        banner.add(title, BorderLayout.WEST);
        banner.add(subtitle, BorderLayout.EAST);
        return banner;
    }

    private JPanel createDirtyRoomsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(180, 180, 180), 1), "Rooms Awaiting Terminal Sanitization (REQUIRES_SANITIZATION)",
                        TitledBorder.LEADING, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13)),
                new EmptyBorder(8, 8, 8, 8)
        ));

        // Filter & Staff Input Bar
        JPanel topFilterBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));

        topFilterBar.add(new JLabel("Hospital Filter:"));
        hospitalFilterCombo = new JComboBox<>(new String[]{
                "All Hospitals",
                "City Central Hospital",
                "Memorial Care Hospital",
                "Metro General Hospital",
                "St. Jude Emergency Center"
        });
        hospitalFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        hospitalFilterCombo.addActionListener(e -> refreshTable());
        topFilterBar.add(hospitalFilterCombo);

        topFilterBar.add(Box.createHorizontalStrut(15));
        topFilterBar.add(new JLabel("Staff Member:"));
        staffNameField = new JTextField("Ravi Kumar (Sanitization)", 18);
        staffNameField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        topFilterBar.add(staffNameField);

        panel.add(topFilterBar, BorderLayout.NORTH);

        // Table
        String[] headers = {"Bed ID", "Hospital", "Ward", "Discharged Time", "Status", "Clinical State Description"};
        dirtyTableModel = new DefaultTableModel(headers, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        dirtyTable = new JTable(dirtyTableModel);
        dirtyTable.setRowHeight(28);
        dirtyTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dirtyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        dirtyTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        // Status Renderer
        dirtyTable.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    setForeground(new Color(198, 40, 40)); // Red warning for dirty beds
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });

        dirtyTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        dirtyTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        dirtyTable.getColumnModel().getColumn(2).setPreferredWidth(90);
        dirtyTable.getColumnModel().getColumn(3).setPreferredWidth(80);
        dirtyTable.getColumnModel().getColumn(4).setPreferredWidth(150);
        dirtyTable.getColumnModel().getColumn(5).setPreferredWidth(260);

        panel.add(new JScrollPane(dirtyTable), BorderLayout.CENTER);

        // Action Toolbar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));

        JButton markCleanBtn = new JButton("✓ Mark Selected Room Clean (Confirm)");
        markCleanBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        markCleanBtn.setBackground(new Color(46, 125, 50));
        markCleanBtn.setForeground(Color.WHITE);
        markCleanBtn.setFocusPainted(false);
        markCleanBtn.addActionListener(e -> handleMarkSelectedClean());

        JButton simulateDirtyBtn = new JButton("+ Simulate Discharged Dirty Bed");
        simulateDirtyBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        simulateDirtyBtn.addActionListener(e -> handleSimulateDirtyBed());

        JButton refreshBtn = new JButton("🔄 Refresh List");
        refreshBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        refreshBtn.addActionListener(e -> refreshTable());

        bottomBar.add(markCleanBtn);
        bottomBar.add(simulateDirtyBtn);
        bottomBar.add(refreshBtn);

        panel.add(bottomBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createHistoryLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(180, 180, 180), 1), "Completed Sanitization Activity Log & Alerts Triggered",
                        TitledBorder.LEADING, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 12)),
                new EmptyBorder(6, 6, 6, 6)
        ));

        String[] logHeaders = {"Time Sanitized", "Bed ID", "Hospital", "Ward", "Sanitized By", "Transition Log"};
        logTableModel = new DefaultTableModel(logHeaders, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable logTable = new JTable(logTableModel);
        logTable.setRowHeight(24);
        logTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        logTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        panel.add(new JScrollPane(logTable), BorderLayout.CENTER);
        return panel;
    }

    // ──────────────────────────── Action Handlers ────────────────────────────

    private void handleMarkSelectedClean() {
        int[] selectedRows = dirtyTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one room from the table to mark as clean.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String staffName = staffNameField.getText().trim();
        if (staffName.isBlank()) {
            staffName = "Cleaning Staff (Janitorial)";
        }

        int cleanedCount = 0;

        // Loop over selected beds (supports U6 Alt Flow 2: several rooms marked clean one after another)
        for (int row : selectedRows) {
            long bedId = (Long) dirtyTableModel.getValueAt(row, 0);
            String hospital = (String) dirtyTableModel.getValueAt(row, 1);
            String ward = (String) dirtyTableModel.getValueAt(row, 2);

            // U6 Step 2 & Exception 1: Confirmation prompt
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    String.format("Confirm sanitization & terminal disinfection complete for:\n\nBed: #%d\nHospital: %s\nWard: %s\nStaff: %s\n\nSetting status to VACANT and alerting triage?",
                            bedId, hospital, ward, staffName),
                    "Confirm Room Cleaning",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (choice != JOptionPane.YES_OPTION) {
                // U6 Exception 1: Staff cancels at confirmation step -> nothing changed
                continue;
            }

            try {
                // U6 Step 4–6: Execute transition to VACANT and fire triage alert (< 0.5s)
                long start = System.currentTimeMillis();
                sanitizationService.markRoomSanitized(bedId, staffName);
                long elapsed = System.currentTimeMillis() - start;

                // Remove from dirty registry
                dirtyBedRegistry.remove(bedId);
                cleanedCount++;

                // Add to history log
                logTableModel.insertRow(0, new Object[]{
                        LocalDateTime.now().format(TIME_FMT),
                        bedId,
                        hospital,
                        ward,
                        staffName,
                        String.format("REQUIRES_SANITIZATION → VACANT (%d ms)", elapsed)
                });

            } catch (IllegalBedStateException ibse) {
                // U6 Exception 2: Bed is not in REQUIRES_SANITIZATION state
                JOptionPane.showMessageDialog(
                        this,
                        "Sanitization rejected by State Pattern:\n\n" + ibse.getMessage(),
                        "Illegal Bed State Transition",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }

        refreshTable();
        if (cleanedCount > 0) {
            JOptionPane.showMessageDialog(this, "Selected rooms sanitized! Pop-up notifications broadcast to triage screens in < 0.5s.",
                    "Sanitization Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void handleSimulateDirtyBed() {
        long newBedId = simulatedBedCounter.getAndIncrement();
        String selectedHospital = (String) hospitalFilterCombo.getSelectedItem();
        if (selectedHospital == null || selectedHospital.equals("All Hospitals")) {
            selectedHospital = "City Central Hospital";
        }

        String ward = "ICU";
        addDirtyBed(newBedId, selectedHospital, ward);
        sanitizationService.seedWaitlistPatient(selectedHospital, "Vikram Seth", TriageLevel.LEVEL_1);
        refreshTable();

        JOptionPane.showMessageDialog(this,
                String.format("Simulated patient discharge: Bed #%d at %s/%s transitioned to REQUIRES_SANITIZATION.",
                        newBedId, selectedHospital, ward),
                "Discharge Simulated", JOptionPane.INFORMATION_MESSAGE);
    }

    public void addDirtyBed(long bedId, String hospitalName, String wardName) {
        DirtyBedRecord record = new DirtyBedRecord(bedId, hospitalName, wardName, LocalDateTime.now());
        dirtyBedRegistry.put(bedId, record);

        // Register in SanitizationService
        sanitizationService.seedBedState(
                bedId,
                BedState.fromStatus(BedStatus.REQUIRES_SANITIZATION),
                hospitalName,
                wardName
        );
    }

    public void refreshTable() {
        dirtyTableModel.setRowCount(0);
        String selectedFilter = (String) hospitalFilterCombo.getSelectedItem();
        boolean filterAll = (selectedFilter == null || selectedFilter.equals("All Hospitals"));

        for (DirtyBedRecord r : dirtyBedRegistry.values()) {
            if (filterAll || r.hospitalName.equalsIgnoreCase(selectedFilter)) {
                dirtyTableModel.addRow(new Object[]{
                        r.bedId,
                        r.hospitalName,
                        r.wardName,
                        r.dischargedAt.format(TIME_FMT),
                        BedStatus.REQUIRES_SANITIZATION.name(),
                        "Discharged; terminal disinfection required before reuse."
                });
            }
        }
    }

    private void seedDefaultDirtyBeds() {
        addDirtyBed(401L, "City Central Hospital", "ICU");
        addDirtyBed(402L, "City Central Hospital", "Emergency Ward");
        addDirtyBed(403L, "Memorial Care Hospital", "ICU");
        addDirtyBed(404L, "Metro General Hospital", "General Ward");
        sanitizationService.seedWaitlistPatient("City Central Hospital", "Vikram Seth", TriageLevel.LEVEL_1);
    }

    public DefaultTableModel getDirtyTableModel() {
        return dirtyTableModel;
    }

    public JTable getDirtyTable() {
        return dirtyTable;
    }

    public DefaultTableModel getLogTableModel() {
        return logTableModel;
    }

    public void removeDirtyBed(long bedId) {
        dirtyBedRegistry.remove(bedId);
        refreshTable();
    }

    public boolean performSanitization(long bedId, String staffName) {
        DirtyBedRecord r = dirtyBedRegistry.get(bedId);
        if (r == null) {
            sanitizationService.markRoomSanitized(bedId, staffName);
            return true;
        }
        long start = System.currentTimeMillis();
        sanitizationService.markRoomSanitized(bedId, staffName, r.hospitalName, r.wardName);
        long elapsed = System.currentTimeMillis() - start;
        dirtyBedRegistry.remove(bedId);
        logTableModel.insertRow(0, new Object[]{
                LocalDateTime.now().format(TIME_FMT),
                bedId,
                r.hospitalName,
                r.wardName,
                staffName,
                String.format("REQUIRES_SANITIZATION -> VACANT (%d ms)", elapsed)
        });
        refreshTable();
        return true;
    }
}
