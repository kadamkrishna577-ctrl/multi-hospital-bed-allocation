package com.group15.hospital.ui.clinical;

import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.service.preemption.EmergencyPreemptionService;
import com.group15.hospital.service.preemption.EmergencyPreemptionService.PreemptionResult;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Swing dialog for initiating <strong>emergency bed pre-emption</strong>.
 * <p>
 * Allows authorised staff to forcibly assign a critical patient to an
 * occupied bed, displacing the current lower-acuity occupant to the waitlist.
 * All operations execute on a {@link SwingWorker} background thread to keep
 * the UI responsive.
 * </p>
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>Patient Name — free text</li>
 *   <li>Patient ID — numeric</li>
 *   <li>Triage Level — dropdown (defaults to LEVEL_1 / CRITICAL)</li>
 *   <li>Target Bed ID — numeric</li>
 * </ul>
 *
 * @author Group 15
 * @see EmergencyPreemptionService
 */
public class EmergencyOverrideDialog extends JDialog {

    private static final Logger LOGGER = Logger.getLogger(EmergencyOverrideDialog.class.getName());

    // ── Service ──
    private final EmergencyPreemptionService preemptionService;

    // ── Input fields ──
    private final JTextField patientNameField;
    private final JTextField patientIdField;
    private final JComboBox<TriageLevel> triageLevelCombo;
    private final JTextField bedIdField;

    // ── Output / Status ──
    private final JTextArea statusArea;
    private final JButton executeButton;
    private final JButton cancelButton;

    // ──────────────────────────── Constructor ─────────────────────────────

    /**
     * Creates the emergency override dialog.
     *
     * @param owner              the parent frame (may be {@code null})
     * @param preemptionService  the service that performs the pre-emption
     */
    public EmergencyOverrideDialog(Frame owner, EmergencyPreemptionService preemptionService) {
        super(owner, "Emergency Override", true);
        this.preemptionService = preemptionService;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setPreferredSize(new Dimension(500, 420));
        setResizable(false);

        // ── Main panel ──
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // ── Title ──
        JLabel titleLabel = new JLabel("\uD83D\uDEA8 Emergency Override", SwingConstants.CENTER);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(new Color(200, 0, 0));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // ── Form panel (GridBagLayout) ──
        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int row = 0;

        // Patient Name
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        formPanel.add(new JLabel("Patient Name:"), gbc);
        patientNameField = new JTextField(20);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(patientNameField, gbc);

        // Patient ID
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        formPanel.add(new JLabel("Patient ID:"), gbc);
        patientIdField = new JTextField(20);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(patientIdField, gbc);

        // Triage Level
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        formPanel.add(new JLabel("Triage Level:"), gbc);
        triageLevelCombo = new JComboBox<>(TriageLevel.values());
        triageLevelCombo.setSelectedItem(TriageLevel.LEVEL_1);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(triageLevelCombo, gbc);

        // Target Bed ID
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0;
        formPanel.add(new JLabel("Target Bed ID:"), gbc);
        bedIdField = new JTextField(20);
        gbc.gridx = 1; gbc.weightx = 1.0;
        formPanel.add(bedIdField, gbc);

        // Status area
        row++;
        gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        statusArea = new JTextArea(5, 30);
        statusArea.setEditable(false);
        statusArea.setLineWrap(true);
        statusArea.setWrapStyleWord(true);
        statusArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        statusArea.setBackground(new Color(245, 245, 245));
        statusArea.setText("Ready. Fill in the fields above and click 'Execute Override'.");
        JScrollPane scrollPane = new JScrollPane(statusArea);
        formPanel.add(scrollPane, gbc);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // ── Button panel ──
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));

        executeButton = new JButton("Execute Override");
        executeButton.setBackground(new Color(200, 0, 0));
        executeButton.setForeground(Color.WHITE);
        executeButton.setFocusPainted(false);
        executeButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        executeButton.addActionListener(this::onExecuteOverride);
        buttonPanel.add(executeButton);

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(cancelButton);

        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(owner);
    }

    // ──────────────────────── Action Handler ──────────────────────────────

    /**
     * Handles the "Execute Override" button click.
     * Validates input, then runs the pre-emption on a background thread.
     */
    private void onExecuteOverride(ActionEvent event) {
        // ── Validation ──
        String patientName = patientNameField.getText().trim();
        if (patientName.isEmpty()) {
            showValidationError("Patient Name is required.");
            return;
        }

        long patientId;
        try {
            patientId = Long.parseLong(patientIdField.getText().trim());
        } catch (NumberFormatException e) {
            showValidationError("Patient ID must be a valid number.");
            return;
        }

        long bedId;
        try {
            bedId = Long.parseLong(bedIdField.getText().trim());
        } catch (NumberFormatException e) {
            showValidationError("Target Bed ID must be a valid number.");
            return;
        }

        TriageLevel triageLevel = (TriageLevel) triageLevelCombo.getSelectedItem();
        if (triageLevel == null) {
            showValidationError("Please select a triage level.");
            return;
        }

        // ── Disable controls during operation ──
        setControlsEnabled(false);
        statusArea.setText("Executing emergency override...\n");

        // ── Background execution ──
        SwingWorker<PreemptionResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PreemptionResult doInBackground() {
                return preemptionService.preemptBed(bedId, patientId, patientName, triageLevel);
            }

            @Override
            protected void done() {
                try {
                    PreemptionResult result = get();
                    if (result.success()) {
                        statusArea.setText("✅ SUCCESS\n\n" + result.message());
                        if (result.displacedPatient() != null) {
                            statusArea.append("\n\nDisplaced patient: " + result.displacedPatient());
                        }
                    } else {
                        statusArea.setText("❌ FAILED\n\n" + result.message());
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Emergency override failed", e);
                    statusArea.setText("❌ ERROR\n\n" + e.getMessage());
                } finally {
                    setControlsEnabled(true);
                }
            }
        };
        worker.execute();
    }

    // ──────────────────────── Helper Methods ──────────────────────────────

    /**
     * Shows a validation error in the status area and an alert dialog.
     */
    private void showValidationError(String message) {
        statusArea.setText("⚠️ Validation Error: " + message);
        JOptionPane.showMessageDialog(this, message, "Validation Error", JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Enables or disables all input controls.
     */
    private void setControlsEnabled(boolean enabled) {
        patientNameField.setEnabled(enabled);
        patientIdField.setEnabled(enabled);
        triageLevelCombo.setEnabled(enabled);
        bedIdField.setEnabled(enabled);
        executeButton.setEnabled(enabled);
    }
}
