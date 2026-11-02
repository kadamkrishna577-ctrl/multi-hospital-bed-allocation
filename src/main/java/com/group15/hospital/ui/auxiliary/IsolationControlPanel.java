package com.group15.hospital.ui.auxiliary;

import com.group15.hospital.dao.BedDAO;
import com.group15.hospital.dao.PatientDAO;
import com.group15.hospital.dao.WardTopologyDAO;
import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.bed.*;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.Patient;
import com.group15.hospital.model.isolation.WardTopologyGraph;
import com.group15.hospital.service.containment.IsolationContainmentService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Ward Infection Containment &amp; Bed Adjacency Control Panel (SRS F12 &amp; Use Case U4).
 * <p>
 * Provides live visualization of ward topology, automatic containment buffer locking
 * for neighbor and shared-air beds, cross-contamination exposure warnings for occupied
 * adjacent beds, and a patient ward transfer simulator that triggers live updates across
 * Billing and Kitchen services.
 * </p>
 *
 * @author Jatin (Module P3)
 * @see IsolationContainmentService
 * @see WardTopologyDAO
 */
public class IsolationControlPanel extends JPanel {

    private final IsolationContainmentService containmentService;
    private final WardTopologyDAO wardTopologyDAO;
    private final BedDAO bedDAO;
    private final PatientDAO patientDAO;

    // Topology Table
    private DefaultTableModel topologyTableModel;
    private JTable topologyTable;

    // Topology Link Inputs
    private JComboBox<Long> linkBed1Combo;
    private JComboBox<Long> linkBed2Combo;
    private JComboBox<String> linkTypeCombo;

    // Containment Inputs
    private JComboBox<Long> isolationBedCombo;

    // Transfer Simulator Inputs
    private JTextField transferPatientIdField;
    private JComboBox<Long> transferFromBedCombo;
    private JComboBox<Long> transferToBedCombo;
    private JComboBox<String> transferWardCombo;
    private JSpinner transferHoursSpinner;
    private JCheckBox ventCheckBox;
    private JCheckBox oxyCheckBox;
    private JCheckBox dialCheckBox;
    private JCheckBox monCheckBox;
    private JCheckBox contagiousCheckBox;

    /** Guard flag preventing recursive feedback loops during two-way selection sync. */
    private boolean isUpdatingSelection = false;

    public IsolationControlPanel(IsolationContainmentService containmentService,
                                 WardTopologyDAO wardTopologyDAO,
                                 BedDAO bedDAO,
                                 PatientDAO patientDAO) {
        this.containmentService = Objects.requireNonNull(containmentService, "containmentService must not be null");
        this.wardTopologyDAO = Objects.requireNonNull(wardTopologyDAO, "wardTopologyDAO must not be null");
        this.bedDAO = Objects.requireNonNull(bedDAO, "bedDAO must not be null");
        this.patientDAO = Objects.requireNonNull(patientDAO, "patientDAO must not be null");

        setLayout(new BorderLayout(10, 10));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // 1. Top Header Banner
        add(createHeaderBanner(), BorderLayout.NORTH);

        // 2. Center Content Split: Top Table (60%), Bottom Controls (40%)
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createTopologyGridPanel(),
                createControlsTabbedPanel());
        splitPane.setResizeWeight(0.55);
        splitPane.setBorder(null);
        add(splitPane, BorderLayout.CENTER);

        // 3. Pre-seed demo beds and edges if empty so offline mode works out of the box
        initDemoDataIfEmpty();

        // 4. Initial table population
        refreshTopology();
    }

    private JPanel createHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(15, 118, 110)); // Deep Teal
        banner.setBorder(new EmptyBorder(12, 15, 12, 15));

        JLabel title = new JLabel("[ISO] INFECTION CONTAINMENT & WARD TOPOLOGY CONTROL");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Module P3: Dynamic Buffer Locking • Shared-Air Circulation • Cross-Contamination Alert");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(204, 251, 241));

        banner.add(title, BorderLayout.WEST);
        banner.add(subtitle, BorderLayout.EAST);
        return banner;
    }

    // ──────────────────────────── Grid Table ────────────────────────────

    private JPanel createTopologyGridPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(203, 213, 225)), "Live Ward Beds & Infection Safety Buffer Status",
                        TitledBorder.LEADING, TitledBorder.TOP, new Font("Segoe UI", Font.BOLD, 13)),
                new EmptyBorder(6, 6, 6, 6)));

        String[] columns = {
                "Bed ID", "Ward Name", "Status", "Direct Neighbors (NEIGHBOR)",
                "Shared HVAC (SHARED_AIR)", "Contagious Source"
        };

        topologyTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };

        topologyTable = new JTable(topologyTableModel);
        topologyTable.setRowHeight(26);
        topologyTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        topologyTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        topologyTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Two-way selection sync: Table row click -> Target Bed combo & Isolation Bed combo
        topologyTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isUpdatingSelection) {
                isUpdatingSelection = true;
                try {
                    int selectedRow = topologyTable.getSelectedRow();
                    if (selectedRow >= 0 && selectedRow < topologyTableModel.getRowCount()) {
                        Long bedId = (Long) topologyTableModel.getValueAt(selectedRow, 0);
                        if (bedId != null) {
                            if (transferToBedCombo != null) {
                                transferToBedCombo.setSelectedItem(bedId);
                            }
                            if (isolationBedCombo != null) {
                                isolationBedCombo.setSelectedItem(bedId);
                            }
                        }
                    }
                } finally {
                    isUpdatingSelection = false;
                }
            }
        });

        // Status badge renderer
        topologyTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String st = value.toString();
                    setFont(getFont().deriveFont(Font.BOLD));
                    if (st.contains("RESTRICTED_BUFFER")) {
                        setForeground(new Color(217, 119, 6)); // Amber / Orange
                    } else if (st.contains("VACANT")) {
                        setForeground(new Color(22, 101, 52)); // Green
                    } else if (st.contains("OCCUPIED")) {
                        setForeground(new Color(29, 78, 216)); // Blue
                    } else if (st.contains("REQUIRES_SANITIZATION")) {
                        setForeground(new Color(185, 28, 28)); // Red
                    } else {
                        setForeground(Color.DARK_GRAY);
                    }
                }
                if (isSelected) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground());
                }
                return c;
            }
        });

        // Contagious source & exposure warning renderer
        topologyTable.getColumnModel().getColumn(5).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String val = value.toString();
                    setFont(getFont().deriveFont(Font.BOLD));
                    if (val.contains("CONTAGIOUS")) {
                        setForeground(new Color(220, 38, 38)); // Crimson Red
                    } else if (val.contains("EXPOSED")) {
                        setForeground(new Color(234, 88, 12)); // Orange-Red Hazard
                    } else if (val.contains("PENDING CLEAN")) {
                        setForeground(new Color(185, 28, 28)); // Red Alert
                    } else if (val.contains("BUFFER")) {
                        setForeground(new Color(217, 119, 6)); // Amber
                    } else {
                        setForeground(Color.GRAY);
                        setFont(getFont().deriveFont(Font.PLAIN));
                    }
                }
                if (isSelected) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground());
                }
                return c;
            }
        });

        topologyTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        topologyTable.getColumnModel().getColumn(1).setPreferredWidth(160);
        topologyTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        topologyTable.getColumnModel().getColumn(3).setPreferredWidth(180);
        topologyTable.getColumnModel().getColumn(4).setPreferredWidth(180);
        topologyTable.getColumnModel().getColumn(5).setPreferredWidth(170);

        panel.add(new JScrollPane(topologyTable), BorderLayout.CENTER);
        return panel;
    }

    // ──────────────────────────── Controls Tabs ────────────────────────────

    private JTabbedPane createControlsTabbedPanel() {
        JTabbedPane tabbed = new JTabbedPane();
        tabbed.setFont(new Font("Segoe UI", Font.BOLD, 12));

        tabbed.addTab("[ISO] Contagious Containment Controls", createContainmentControlsPanel());
        tabbed.addTab("[TRANSFER] Ward Transfer Simulator (Fires Observers)", createTransferSimulatorPanel());
        tabbed.addTab("[LINK] Add Ward Adjacency Link", createTopologyLinkPanel());

        return tabbed;
    }

    private JPanel createContainmentControlsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(12, 16, 12, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8, 8, 8, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Bed Selector
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Target Bed ID:"), gbc);

        isolationBedCombo = new JComboBox<>();
        isolationBedCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        isolationBedCombo.addActionListener(e -> {
            if (!isUpdatingSelection) {
                isUpdatingSelection = true;
                try {
                    Long bedId = (Long) isolationBedCombo.getSelectedItem();
                    if (bedId != null) {
                        syncTableRowSelection(bedId);
                        if (transferToBedCombo != null) {
                            transferToBedCombo.setSelectedItem(bedId);
                        }
                    }
                } finally {
                    isUpdatingSelection = false;
                }
            }
        });

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 1.0;
        panel.add(isolationBedCombo, gbc);

        // Activate Isolation Button
        JButton activateBtn = new JButton("[!] Activate Contagious Isolation (Lock Buffers)");
        activateBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        activateBtn.setBackground(new Color(220, 38, 38)); // Crimson Red
        activateBtn.setForeground(Color.WHITE);
        activateBtn.setFocusPainted(false);
        activateBtn.addActionListener(e -> handleActivateIsolation());

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2;
        panel.add(activateBtn, gbc);

        // Release Isolation Button
        JButton releaseBtn = new JButton("[OK] Release Isolation (Restore Buffers to VACANT)");
        releaseBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        releaseBtn.setBackground(new Color(22, 101, 52)); // Forest Green
        releaseBtn.setForeground(Color.WHITE);
        releaseBtn.setFocusPainted(false);
        releaseBtn.addActionListener(e -> handleReleaseIsolation());

        gbc.gridy = 2;
        panel.add(releaseBtn, gbc);

        // Explanatory note
        JLabel note = new JLabel("<html><span style='color:#64748b;'>Activating isolation locks all adjacent (NEIGHBOR) and HVAC-sharing (SHARED_AIR) vacant beds into RESTRICTED_BUFFER. If an adjacent bed is occupied, it is flagged as EXPOSED (EVACUATE). Overlapping safety zones are preserved.</span></html>");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        gbc.gridy = 3;
        panel.add(note, gbc);

        return panel;
    }

    private JPanel createTransferSimulatorPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(10, 14, 10, 14));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Row 1: Patient ID & From Bed
        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Patient ID:"), gbc);

        transferPatientIdField = new JTextField("5001", 8);
        gbc.gridx = 1; gbc.gridy = 0;
        panel.add(transferPatientIdField, gbc);

        gbc.gridx = 2; gbc.gridy = 0;
        panel.add(new JLabel("From Bed:"), gbc);

        transferFromBedCombo = new JComboBox<>();
        gbc.gridx = 3; gbc.gridy = 0;
        panel.add(transferFromBedCombo, gbc);

        // Row 2: Target Bed & Target Ward
        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Target Bed:"), gbc);

        transferToBedCombo = new JComboBox<>();
        gbc.gridx = 1; gbc.gridy = 1;
        panel.add(transferToBedCombo, gbc);

        gbc.gridx = 2; gbc.gridy = 1;
        panel.add(new JLabel("Target Ward:"), gbc);

        transferWardCombo = new JComboBox<>(new String[]{
                "Emergency ICU Ward A",
                "General Medicine Ward B",
                "Infectious Isolation Ward C",
                "Cardiology Intensive Care",
                "Trauma ICU"
        });
        gbc.gridx = 3; gbc.gridy = 1;
        panel.add(transferWardCombo, gbc);

        // Row 3: Equipment Decorators Checkboxes
        JPanel equipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        equipPanel.setBorder(BorderFactory.createTitledBorder("Decorate Target Bed with Medical Equipment"));
        ventCheckBox = new JCheckBox("Ventilator ($150)");
        oxyCheckBox = new JCheckBox("Oxygen ($40)");
        dialCheckBox = new JCheckBox("Dialysis ($200)");
        monCheckBox = new JCheckBox("Cardiac Monitor ($60)");
        equipPanel.add(ventCheckBox);
        equipPanel.add(oxyCheckBox);
        equipPanel.add(dialCheckBox);
        equipPanel.add(monCheckBox);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4;
        panel.add(equipPanel, gbc);

        // Row 4: Hours Stayed & Contagious Flag
        JPanel optionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        optionsPanel.add(new JLabel("Hours Stayed in Source Bed:"));
        transferHoursSpinner = new JSpinner(new SpinnerNumberModel(4, 1, 168, 1));
        optionsPanel.add(transferHoursSpinner);

        contagiousCheckBox = new JCheckBox("[!] Patient is Contagious / Requires Isolation");
        contagiousCheckBox.setForeground(new Color(185, 28, 28));
        contagiousCheckBox.setFont(new Font("Segoe UI", Font.BOLD, 12));
        optionsPanel.add(contagiousCheckBox);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 4;
        panel.add(optionsPanel, gbc);

        // Row 5: Action Button
        JButton transferBtn = new JButton("[TRANSFER] Execute Ward Transfer & Notify Observers");
        transferBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        transferBtn.setBackground(new Color(37, 99, 235)); // Primary Blue
        transferBtn.setForeground(Color.WHITE);
        transferBtn.setFocusPainted(false);
        transferBtn.addActionListener(e -> handleExecuteTransfer());

        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 4;
        gbc.insets = new Insets(8, 6, 4, 6);
        panel.add(transferBtn, gbc);

        return panel;
    }

    private JPanel createTopologyLinkPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(14, 16, 14, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(new JLabel("Bed 1 ID:"), gbc);

        linkBed1Combo = new JComboBox<>();
        gbc.gridx = 1; gbc.gridy = 0;
        panel.add(linkBed1Combo, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        panel.add(new JLabel("Bed 2 ID:"), gbc);

        linkBed2Combo = new JComboBox<>();
        gbc.gridx = 1; gbc.gridy = 1;
        panel.add(linkBed2Combo, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        panel.add(new JLabel("Relationship:"), gbc);

        linkTypeCombo = new JComboBox<>(new String[]{
                "NEIGHBOR (Physical Wall Adjacency)",
                "SHARED_AIR (HVAC Ventilation Flow)"
        });
        gbc.gridx = 1; gbc.gridy = 2;
        panel.add(linkTypeCombo, gbc);

        JButton addLinkBtn = new JButton("[+] Add Adjacency Link");
        addLinkBtn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        addLinkBtn.setFocusPainted(false);
        addLinkBtn.addActionListener(e -> handleAddTopologyLink());

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 6, 6, 6);
        panel.add(addLinkBtn, gbc);

        return panel;
    }

    // ──────────────────────────── Action Handlers ────────────────────────────

    private void handleActivateIsolation() {
        int row = topologyTable.getSelectedRow();
        Long selectedBed = null;
        if (row >= 0 && row < topologyTableModel.getRowCount()) {
            selectedBed = (Long) topologyTableModel.getValueAt(row, 0);
        }
        if (selectedBed == null) {
            selectedBed = (Long) isolationBedCombo.getSelectedItem();
        }

        if (selectedBed == null) {
            JOptionPane.showMessageDialog(this, "Please select a bed to isolate.", "No Bed Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Keep synced with combo box
        isolationBedCombo.setSelectedItem(selectedBed);
        if (transferToBedCombo != null) {
            transferToBedCombo.setSelectedItem(selectedBed);
        }

        try {
            Set<Long> lockedBuffers = containmentService.activateIsolation(selectedBed, "CLINICAL_STAFF");
            Set<Long> exposedOccupied = containmentService.getExposedOccupiedBeds(selectedBed);

            refreshTopology();

            // 3. Occupied Neighbor Exposure Warning
            if (!exposedOccupied.isEmpty()) {
                String warnMsg = String.format(
                        "<html><b style='color:#dc2626; font-size:13px;'>[!] CROSS-CONTAMINATION HAZARD DETECTED!</b><br><br>" +
                        "Contagious isolation on Bed #%d exposes adjacent occupied bed(s):<br>" +
                        "<b style='color:#b91c1c; font-size:12px;'>• Occupied Bed(s) at Risk: %s</b><br><br>" +
                        "Status in grid marked as: <b style='color:#ea580c;'>[!] EXPOSED (EVACUATE)</b><br>" +
                        "Cross-contamination risk alert: Immediate patient transfer / ward evacuation required!</html>",
                        selectedBed, exposedOccupied);

                JOptionPane.showMessageDialog(this, warnMsg, "Cross-Contamination Warning", JOptionPane.WARNING_MESSAGE);
            }

            String msg = String.format("Contagious isolation ACTIVATED on Bed #%d.\n" +
                            "Safety Buffers Locked (%d beds): %s\n" +
                            "Exposed Occupied Bed(s) Requiring Transfer (%d beds): %s\n\n" +
                            "Vacant buffer beds are held in RESTRICTED_BUFFER and cannot receive patients.",
                    selectedBed, lockedBuffers.size(), lockedBuffers.isEmpty() ? "None (no vacant adjacent beds)" : lockedBuffers,
                    exposedOccupied.size(), exposedOccupied.isEmpty() ? "None" : exposedOccupied);
            JOptionPane.showMessageDialog(this, msg, "Isolation Active", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalBedStateException ex) {
            JOptionPane.showMessageDialog(this, "Isolation Activation Blocked:\n" + ex.getMessage(),
                    "Invalid Isolation Target", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleReleaseIsolation() {
        int row = topologyTable.getSelectedRow();
        Long selectedBed = null;
        if (row >= 0 && row < topologyTableModel.getRowCount()) {
            selectedBed = (Long) topologyTableModel.getValueAt(row, 0);
        }
        if (selectedBed == null) {
            selectedBed = (Long) isolationBedCombo.getSelectedItem();
        }

        if (selectedBed == null) {
            JOptionPane.showMessageDialog(this, "Please select a bed to release.", "No Bed Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Keep synced with combo box
        isolationBedCombo.setSelectedItem(selectedBed);

        if (!containmentService.isBedIsolated(selectedBed)) {
            JOptionPane.showMessageDialog(this, "Bed #" + selectedBed + " is not currently under isolation.", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Set<Long> released = containmentService.releaseIsolation(selectedBed, "CLINICAL_STAFF");
        refreshTopology();

        String msg = String.format("Contagious isolation RELEASED for Bed #%d.\n" +
                        "Freed Buffers Restored to VACANT (%d beds): %s",
                selectedBed, released.size(), released.isEmpty() ? "None (other contagious sources still expose buffers)" : released);
        JOptionPane.showMessageDialog(this, msg, "Isolation Released", JOptionPane.INFORMATION_MESSAGE);
    }

    private void handleAddTopologyLink() {
        Long bed1 = (Long) linkBed1Combo.getSelectedItem();
        Long bed2 = (Long) linkBed2Combo.getSelectedItem();

        if (bed1 == null || bed2 == null) {
            JOptionPane.showMessageDialog(this, "Please select two beds to link.", "Selection Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (Objects.equals(bed1, bed2)) {
            JOptionPane.showMessageDialog(this, "Cannot link a bed to itself.", "Invalid Link", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String selectedItem = (String) linkTypeCombo.getSelectedItem();
        String type = (selectedItem != null && selectedItem.contains("SHARED_AIR"))
                ? WardTopologyGraph.RELATION_SHARED_AIR
                : WardTopologyGraph.RELATION_NEIGHBOR;

        try {
            wardTopologyDAO.insertEdge(bed1, bed2, type);
            containmentService.onTopologyEdgeAdded(bed1, bed2, type);
            refreshTopology();

            JOptionPane.showMessageDialog(this,
                    String.format("Added bidirectional %s edge between Bed #%d and Bed #%d.", type, bed1, bed2),
                    "Link Created", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Invalid Link", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void handleExecuteTransfer() {
        Long fromBedId = (Long) transferFromBedCombo.getSelectedItem();
        Long toBedId = (Long) transferToBedCombo.getSelectedItem();

        if (fromBedId == null || toBedId == null) {
            JOptionPane.showMessageDialog(this, "Please select both source and destination beds.", "Invalid Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (Objects.equals(fromBedId, toBedId)) {
            JOptionPane.showMessageDialog(this, "Source and destination beds cannot be identical.", "Invalid Transfer", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long patientId;
        try {
            patientId = Long.parseLong(transferPatientIdField.getText().trim());
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Invalid Patient ID format.", "Format Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int hours = (Integer) transferHoursSpinner.getValue();
        boolean contagious = contagiousCheckBox.isSelected();
        String targetWard = (String) transferWardCombo.getSelectedItem();

        // 1. Resolve Patient
        Patient patient = patientDAO.findById(patientId)
                .orElse(new Patient(patientId, "Patient #" + patientId, 45, "Unknown", TriageLevel.LEVEL_3,
                        fromBedId, DietType.REGULAR, null, null, false));

        // 2. Resolve From Bed
        BedComponent fromBed = containmentService.getBed(fromBedId);
        if (fromBed == null) {
            fromBed = new BaseBed(fromBedId, "General Ward", "City Central Hospital", BedStatus.OCCUPIED);
            containmentService.registerBed(fromBed);
        }

        // 3. Construct Decorated Target Bed
        BaseBed baseTarget = new BaseBed(toBedId, targetWard != null ? targetWard : "ICU Ward", "City Central Hospital", BedStatus.VACANT);
        BedComponent decoratedTarget = baseTarget;

        if (ventCheckBox.isSelected()) decoratedTarget = new VentilatorDecorator(decoratedTarget);
        if (oxyCheckBox.isSelected()) decoratedTarget = new OxygenCylinderDecorator(decoratedTarget);
        if (dialCheckBox.isSelected()) decoratedTarget = new DialysisDecorator(decoratedTarget);
        if (monCheckBox.isSelected()) decoratedTarget = new CardiacMonitorDecorator(decoratedTarget);

        // 4. Execute Transfer
        try {
            containmentService.transferPatient(patient, fromBed, decoratedTarget, hours, contagious, "SIMULATOR_USER");
            refreshTopology();

            double sourceCharge = Math.max(1, hours) * fromBed.getHourlyCost();
            String msg = String.format("<html><b>Transfer Successfully Executed!</b><br><br>" +
                            "• Patient: <b>%s</b> (ID: %d)<br>" +
                            "• Source Bed #%d transitioned to <b>REQUIRES_SANITIZATION</b><br>" +
                            "• Bill Itemized: <b>$%.2f</b> (%d hrs @ $%.2f/hr)<br>" +
                            "• Target Bed #%d occupied in <b>%s</b> (Hourly rate: $%.2f/hr)<br>" +
                            "• Prescribed Diet updated to: <b>%s</b><br>" +
                            "• Contagious Isolation: <b>%s</b><br><br>" +
                            "<span style='color:green;'>BillingService & KitchenService observers fired live!</span></html>",
                    patient.getFullName(), patientId, fromBedId, sourceCharge, hours, fromBed.getHourlyCost(),
                    toBedId, decoratedTarget.getWardName(), decoratedTarget.getHourlyCost(),
                    patient.getDietType(), contagious ? "ACTIVE" : "NONE");

            JOptionPane.showMessageDialog(this, msg, "Transfer Complete", JOptionPane.INFORMATION_MESSAGE);

        } catch (IllegalBedStateException ex) {
            JOptionPane.showMessageDialog(this, "Transfer Rejected: " + ex.getMessage(),
                    "Bed State Guard", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Transfer Failed: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ──────────────────────────── Data Sync & Refresh ────────────────────────────

    /**
     * Alias for {@link #refreshTopology()} to maintain backwards compatibility with dashboard tab listeners.
     */
    public void refreshTable() {
        refreshTopology();
    }

    /**
     * Refreshes the topology table and drop-down selectors while strictly preserving
     * currently active bed selections so that dropdowns never reset back to bed 101.
     */
    public void refreshTopology() {
        // Save current selections before updating
        Long savedIsoBedId = (isolationBedCombo != null) ? (Long) isolationBedCombo.getSelectedItem() : null;
        Long savedToBedId = (transferToBedCombo != null) ? (Long) transferToBedCombo.getSelectedItem() : null;
        Long savedFromBedId = (transferFromBedCombo != null) ? (Long) transferFromBedCombo.getSelectedItem() : null;

        int selectedRow = (topologyTable != null) ? topologyTable.getSelectedRow() : -1;
        Long savedTableBedId = (selectedRow >= 0 && selectedRow < topologyTableModel.getRowCount())
                ? (Long) topologyTableModel.getValueAt(selectedRow, 0)
                : null;

        Long preferredBedId = (savedTableBedId != null) ? savedTableBedId : savedIsoBedId;

        topologyTableModel.setRowCount(0);

        WardTopologyGraph graph = wardTopologyDAO.loadTopologyGraph();
        Set<Long> allBeds = new TreeSet<>(graph.getAllBeds());

        // Also add any registered and isolated beds from containment service
        allBeds.addAll(containmentService.getRegisteredBeds().keySet());
        allBeds.addAll(containmentService.getActiveIsolatedBeds());

        // Update dropdown choices
        DefaultComboBoxModel<Long> comboModel1 = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<Long> comboModel2 = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<Long> isoModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<Long> fromModel = new DefaultComboBoxModel<>();
        DefaultComboBoxModel<Long> toModel = new DefaultComboBoxModel<>();

        for (Long bedId : allBeds) {
            comboModel1.addElement(bedId);
            comboModel2.addElement(bedId);
            isoModel.addElement(bedId);
            fromModel.addElement(bedId);
            toModel.addElement(bedId);

            BedComponent bed = containmentService.getBed(bedId);
            String ward = (bed != null) ? bed.getWardName() : "General Ward";
            BedStatus status = (bed != null) ? bed.getBedStatus() : BedStatus.VACANT;

            Set<Long> neighbors = graph.getDirectNeighbors(bedId);
            Set<Long> sharedAir = graph.getSharedAirBeds(bedId);
            String contagiousStatus = containmentService.getContagiousSourceStatus(bedId);

            topologyTableModel.addRow(new Object[]{
                    bedId,
                    ward,
                    status.name(),
                    neighbors.isEmpty() ? "—" : neighbors.toString(),
                    sharedAir.isEmpty() ? "—" : sharedAir.toString(),
                    contagiousStatus
            });
        }

        isUpdatingSelection = true;
        try {
            linkBed1Combo.setModel(comboModel1);
            linkBed2Combo.setModel(comboModel2);
            isolationBedCombo.setModel(isoModel);
            transferFromBedCombo.setModel(fromModel);
            transferToBedCombo.setModel(toModel);

            // Restore isolationBedCombo
            if (preferredBedId != null && containsElement(isoModel, preferredBedId)) {
                isolationBedCombo.setSelectedItem(preferredBedId);
            } else if (isoModel.getSize() > 0) {
                isolationBedCombo.setSelectedIndex(0);
            }

            // Restore transferToBedCombo
            Long toRestore = (savedToBedId != null) ? savedToBedId : preferredBedId;
            if (toRestore != null && containsElement(toModel, toRestore)) {
                transferToBedCombo.setSelectedItem(toRestore);
            } else if (toModel.getSize() > 1) {
                transferToBedCombo.setSelectedIndex(1);
            } else if (toModel.getSize() > 0) {
                transferToBedCombo.setSelectedIndex(0);
            }

            // Restore transferFromBedCombo
            if (savedFromBedId != null && containsElement(fromModel, savedFromBedId)) {
                transferFromBedCombo.setSelectedItem(savedFromBedId);
            } else if (fromModel.getSize() > 0) {
                transferFromBedCombo.setSelectedIndex(0);
            }

            // Restore table row selection
            Long bedToSelectInTable = (preferredBedId != null) ? preferredBedId : (Long) isolationBedCombo.getSelectedItem();
            if (bedToSelectInTable != null) {
                syncTableRowSelection(bedToSelectInTable);
            }
        } finally {
            isUpdatingSelection = false;
        }
    }

    private boolean containsElement(DefaultComboBoxModel<Long> model, Long item) {
        if (model == null || item == null) return false;
        for (int i = 0; i < model.getSize(); i++) {
            if (item.equals(model.getElementAt(i))) return true;
        }
        return false;
    }

    private void syncTableRowSelection(Long bedId) {
        if (bedId == null || topologyTable == null || topologyTableModel == null) return;
        for (int i = 0; i < topologyTableModel.getRowCount(); i++) {
            Object val = topologyTableModel.getValueAt(i, 0);
            if (bedId.equals(val)) {
                topologyTable.setRowSelectionInterval(i, i);
                break;
            }
        }
    }

    public void initDemoDataIfEmpty() {
        // Ensure all beds from BedDAO are registered in containmentService
        for (BaseBed b : bedDAO.findAllBeds()) {
            containmentService.registerBed(b);
        }

        // Ensure in-memory demo beds are registered in containmentService if still empty
        if (containmentService.getRegisteredBeds().isEmpty()) {
            BaseBed b101 = new BaseBed(101L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
            BaseBed b102 = new BaseBed(102L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
            BaseBed b103 = new BaseBed(103L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
            BaseBed b104 = new BaseBed(104L, "General Medicine Ward B", "City Central Hospital", BedStatus.VACANT);
            BaseBed b105 = new BaseBed(105L, "General Medicine Ward B", "City Central Hospital", BedStatus.VACANT);
            BaseBed b106 = new BaseBed(106L, "Infectious Isolation Ward C", "City Central Hospital", BedStatus.VACANT);

            b102.assignPatient(5001L, "Robert Chen", TriageLevel.LEVEL_3);

            containmentService.registerBed(b101);
            containmentService.registerBed(b102);
            containmentService.registerBed(b103);
            containmentService.registerBed(b104);
            containmentService.registerBed(b105);
            containmentService.registerBed(b106);
        }

        WardTopologyGraph existing = wardTopologyDAO.loadTopologyGraph();
        if (existing.getAllBeds().isEmpty()) {
            // Seed adjacency edges
            wardTopologyDAO.insertEdge(101L, 102L, WardTopologyGraph.RELATION_NEIGHBOR);
            wardTopologyDAO.insertEdge(102L, 103L, WardTopologyGraph.RELATION_NEIGHBOR);
            wardTopologyDAO.insertEdge(101L, 103L, WardTopologyGraph.RELATION_SHARED_AIR);
            wardTopologyDAO.insertEdge(104L, 105L, WardTopologyGraph.RELATION_NEIGHBOR);
            wardTopologyDAO.insertEdge(105L, 106L, WardTopologyGraph.RELATION_SHARED_AIR);
        }
    }
}
