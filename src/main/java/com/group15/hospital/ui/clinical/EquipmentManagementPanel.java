package com.group15.hospital.ui.clinical;

import com.group15.hospital.model.bed.MedicalMachine;
import com.group15.hospital.service.preemption.EquipmentMaintenanceService;
import com.group15.hospital.service.preemption.EquipmentMaintenanceService.MaintenanceReport;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Swing panel that provides a dashboard for monitoring and managing
 * medical equipment across hospitals.
 * <p>
 * Displays a colour-coded table of all medical machines showing their
 * operating hours, usage percentage, and maintenance status. Provides
 * buttons for refreshing data, running maintenance scans, and completing
 * repairs.
 * </p>
 *
 * <h3>Colour Coding</h3>
 * <ul>
 *   <li><span style="color:green">■</span> <strong>Green</strong> — usage &lt; 70%</li>
 *   <li><span style="color:orange">■</span> <strong>Yellow</strong> — usage 70–89%</li>
 *   <li><span style="color:red">■</span> <strong>Red</strong> — usage ≥ 90%</li>
 *   <li><span style="color:darkred">■</span> <strong>Dark Red</strong> — under maintenance</li>
 * </ul>
 *
 * @author Group 15
 * @see EquipmentMaintenanceService
 */
public class EquipmentManagementPanel extends JPanel {

    private static final Logger LOGGER = Logger.getLogger(EquipmentManagementPanel.class.getName());

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ── Service ──
    private final EquipmentMaintenanceService maintenanceService;

    // ── UI Components ──
    private final EquipmentTableModel tableModel;
    private final JTable equipmentTable;
    private final JLabel statusBar;
    private final JButton refreshButton;
    private final JButton scanButton;
    private final JButton repairButton;

    // ──────────────────────────── Constructor ─────────────────────────────

    /**
     * Creates the equipment management panel.
     *
     * @param maintenanceService the service for equipment queries and maintenance
     */
    public EquipmentManagementPanel(EquipmentMaintenanceService maintenanceService) {
        super(new BorderLayout(5, 5));
        this.maintenanceService = maintenanceService;
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // ── Title ──
        JLabel titleLabel = new JLabel("\u2699\uFE0F Equipment Management", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setBorder(new EmptyBorder(0, 0, 10, 0));
        add(titleLabel, BorderLayout.NORTH);

        // ── Table ──
        tableModel = new EquipmentTableModel();
        equipmentTable = new JTable(tableModel);
        equipmentTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        equipmentTable.setRowHeight(24);
        equipmentTable.setDefaultRenderer(Object.class, new UsageCellRenderer());
        equipmentTable.getTableHeader().setReorderingAllowed(false);

        // Column widths
        equipmentTable.getColumnModel().getColumn(0).setPreferredWidth(70);   // ID
        equipmentTable.getColumnModel().getColumn(1).setPreferredWidth(120);  // Type
        equipmentTable.getColumnModel().getColumn(2).setPreferredWidth(120);  // Serial
        equipmentTable.getColumnModel().getColumn(3).setPreferredWidth(150);  // Hospital
        equipmentTable.getColumnModel().getColumn(4).setPreferredWidth(90);   // Hours Used
        equipmentTable.getColumnModel().getColumn(5).setPreferredWidth(80);   // Safe Limit
        equipmentTable.getColumnModel().getColumn(6).setPreferredWidth(80);   // Usage %
        equipmentTable.getColumnModel().getColumn(7).setPreferredWidth(120);  // Status

        JScrollPane scrollPane = new JScrollPane(equipmentTable);
        add(scrollPane, BorderLayout.CENTER);

        // ── Bottom panel: buttons + status bar ──
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

        refreshButton = new JButton("\uD83D\uDD04 Refresh");
        refreshButton.addActionListener(e -> refreshData());
        buttonPanel.add(refreshButton);

        scanButton = new JButton("\uD83D\uDD27 Run Maintenance Scan");
        scanButton.addActionListener(e -> runMaintenanceScan());
        buttonPanel.add(scanButton);

        repairButton = new JButton("\u2705 Complete Repair");
        repairButton.addActionListener(e -> completeRepair());
        buttonPanel.add(repairButton);

        bottomPanel.add(buttonPanel, BorderLayout.CENTER);

        statusBar = new JLabel("Ready.");
        statusBar.setFont(new Font("SansSerif", Font.ITALIC, 11));
        statusBar.setBorder(new EmptyBorder(3, 5, 3, 5));
        bottomPanel.add(statusBar, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    // ──────────────────────── Action Handlers ────────────────────────────

    /**
     * Refreshes the equipment table from the service. Runs on a background
     * thread to keep the UI responsive.
     */
    public void refreshData() {
        setControlsEnabled(false);
        statusBar.setText("Loading equipment data...");

        new SwingWorker<List<MedicalMachine>, Void>() {
            @Override
            protected List<MedicalMachine> doInBackground() {
                // Load all machines — empty hospital name loads all
                return maintenanceService.getEquipmentStatus("");
            }

            @Override
            protected void done() {
                try {
                    List<MedicalMachine> machines = get();
                    tableModel.setMachines(machines);
                    statusBar.setText("Loaded " + machines.size() + " machines. Last refresh: " +
                                      LocalDateTime.now().format(TIME_FMT));
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Failed to refresh equipment data", e);
                    statusBar.setText("Error loading data: " + e.getMessage());
                } finally {
                    setControlsEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * Triggers a maintenance scan and displays the report.
     */
    private void runMaintenanceScan() {
        setControlsEnabled(false);
        statusBar.setText("Running maintenance scan...");

        new SwingWorker<MaintenanceReport, Void>() {
            @Override
            protected MaintenanceReport doInBackground() {
                return maintenanceService.runMaintenanceScan();
            }

            @Override
            protected void done() {
                try {
                    MaintenanceReport report = get();
                    String msg = String.format(
                            "Scan complete — Scanned: %d | Warnings: %d | Flagged: %d | Already maintaining: %d",
                            report.totalScanned(), report.warningsIssued(),
                            report.flaggedForMaintenance(), report.alreadyUnderMaintenance());
                    statusBar.setText(msg);

                    JOptionPane.showMessageDialog(EquipmentManagementPanel.this,
                            "Maintenance Scan Results\n\n" +
                            "Total Scanned: " + report.totalScanned() + "\n" +
                            "Warnings Issued: " + report.warningsIssued() + "\n" +
                            "Flagged for Maintenance: " + report.flaggedForMaintenance() + "\n" +
                            "Already Under Maintenance: " + report.alreadyUnderMaintenance() + "\n\n" +
                            "Scan Time: " + report.scanTimestamp().format(TIME_FMT),
                            "Maintenance Scan Report",
                            JOptionPane.INFORMATION_MESSAGE);

                    // Refresh table to show updated statuses
                    refreshData();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Maintenance scan failed", e);
                    statusBar.setText("Scan error: " + e.getMessage());
                } finally {
                    setControlsEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * Repairs the currently selected machine (resets its operating hours).
     */
    private void completeRepair() {
        int selectedRow = equipmentTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this,
                    "Please select a machine from the table first.",
                    "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        MedicalMachine machine = tableModel.getMachineAt(selectedRow);
        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Complete repair for %s (ID: %d, S/N: %s)?\n" +
                              "This will reset operating hours to 0.",
                        machine.getEquipmentType().getDisplayName(),
                        machine.getMachineId(), machine.getSerialNumber()),
                "Confirm Repair", JOptionPane.YES_NO_OPTION);

        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        setControlsEnabled(false);
        statusBar.setText("Repairing machine id=" + machine.getMachineId() + "...");

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                maintenanceService.repairMachine(machine.getMachineId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // check for exceptions
                    statusBar.setText("Machine id=" + machine.getMachineId() +
                                      " repaired successfully.");
                    refreshData();
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Repair failed", e);
                    statusBar.setText("Repair error: " + e.getMessage());
                    setControlsEnabled(true);
                }
            }
        }.execute();
    }

    /**
     * Enables or disables all interactive controls.
     */
    private void setControlsEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
        scanButton.setEnabled(enabled);
        repairButton.setEnabled(enabled);
        equipmentTable.setEnabled(enabled);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Inner class: EquipmentTableModel
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Table model backing the equipment {@link JTable}.
     */
    static class EquipmentTableModel extends AbstractTableModel {

        private static final String[] COLUMN_NAMES = {
                "Machine ID", "Type", "Serial Number", "Hospital",
                "Hours Used", "Safe Limit", "Usage %", "Status"
        };

        private List<MedicalMachine> machines = new ArrayList<>();

        public void setMachines(List<MedicalMachine> machines) {
            this.machines = machines != null ? new ArrayList<>(machines) : new ArrayList<>();
            fireTableDataChanged();
        }

        public MedicalMachine getMachineAt(int row) {
            return machines.get(row);
        }

        @Override
        public int getRowCount() {
            return machines.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMN_NAMES.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMN_NAMES[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            MedicalMachine m = machines.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> m.getMachineId();
                case 1 -> m.getEquipmentType().getDisplayName();
                case 2 -> m.getSerialNumber();
                case 3 -> m.getHospitalName();
                case 4 -> String.format("%.1f", m.getOperatingHoursUsed());
                case 5 -> m.getSafeOperatingHourLimit();
                case 6 -> String.format("%.1f%%", m.getUsagePercentage());
                case 7 -> m.isUnderMaintenance() ? "🔧 MAINTENANCE" :
                           m.getUsagePercentage() >= 100 ? "⚠️ EXCEEDED" :
                           m.getUsagePercentage() >= 90 ? "⚠️ WARNING" : "✅ OPERATIONAL";
                default -> "";
            };
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Inner class: UsageCellRenderer
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Custom cell renderer that colour-codes rows by usage percentage.
     */
    private class UsageCellRenderer extends DefaultTableCellRenderer {

        private static final Color GREEN  = new Color(220, 255, 220);
        private static final Color YELLOW = new Color(255, 255, 200);
        private static final Color RED    = new Color(255, 210, 210);
        private static final Color DARK_RED = new Color(200, 150, 150);

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                        boolean isSelected, boolean hasFocus,
                                                        int row, int column) {
            Component cell = super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);

            if (!isSelected && row < tableModel.machines.size()) {
                MedicalMachine machine = tableModel.getMachineAt(row);
                if (machine.isUnderMaintenance()) {
                    cell.setBackground(DARK_RED);
                } else {
                    double usage = machine.getUsagePercentage();
                    if (usage >= 90) {
                        cell.setBackground(RED);
                    } else if (usage >= 70) {
                        cell.setBackground(YELLOW);
                    } else {
                        cell.setBackground(GREEN);
                    }
                }
            } else if (isSelected) {
                cell.setBackground(table.getSelectionBackground());
            }

            return cell;
        }
    }
}
