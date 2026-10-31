package com.group15.hospital.ui.admin;

import com.group15.hospital.dao.PatientDAO;
import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.Patient;
import com.group15.hospital.service.strategy.BedAllocationStrategy;
import com.group15.hospital.service.strategy.BedSearchEngine;
import com.group15.hospital.service.strategy.BestEquippedBedStrategy;
import com.group15.hospital.service.strategy.ClosestBedStrategy;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Optional;

// triage registration panel for admitting emergency patients
public class TriageRegistrationPanel extends JPanel {

    private final BedSearchEngine searchEngine;
    private final PatientDAO patientDAO;

    private JTextField nameField;
    private JTextField ageField;
    private JComboBox<String> genderBox;
    private JComboBox<TriageLevel> triageBox;
    private JComboBox<DietType> dietBox;
    private JComboBox<String> strategyBox;
    private JTextField latField;
    private JTextField lonField;

    private JTextArea resultArea;
    private DefaultTableModel patientTableModel;

    public TriageRegistrationPanel(BedSearchEngine searchEngine, PatientDAO patientDAO) {
        this.searchEngine = (searchEngine != null) ? searchEngine : new BedSearchEngine();
        this.patientDAO = (patientDAO != null) ? patientDAO : new PatientDAO();

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Left Side: Registration & Strategy Input Form
        JPanel formContainer = createRegistrationForm();

        // Right Side: Results & Active Patient Table
        JPanel rightContainer = createRightPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, formContainer, rightContainer);
        splitPane.setDividerLocation(420);
        add(splitPane, BorderLayout.CENTER);

        refreshPatientTable();
    }

    public TriageRegistrationPanel() {
        this(new BedSearchEngine(), new PatientDAO());
    }

    private JPanel createRegistrationForm() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Emergency Triage & Bed Allocation"));

        JPanel fieldsPanel = new JPanel(new GridLayout(8, 2, 8, 8));
        fieldsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        nameField = new JTextField();
        ageField = new JTextField("35");
        genderBox = new JComboBox<>(new String[]{"Male", "Female", "Other"});
        triageBox = new JComboBox<>(TriageLevel.values());
        dietBox = new JComboBox<>(DietType.values());

        strategyBox = new JComboBox<>(new String[]{
                ClosestBedStrategy.STRATEGY_NAME,
                BestEquippedBedStrategy.STRATEGY_NAME
        });

        latField = new JTextField("12.9716");
        lonField = new JTextField("77.5946");

        fieldsPanel.add(new JLabel("Patient Full Name:"));
        fieldsPanel.add(nameField);

        fieldsPanel.add(new JLabel("Age:"));
        fieldsPanel.add(ageField);

        fieldsPanel.add(new JLabel("Gender:"));
        fieldsPanel.add(genderBox);

        fieldsPanel.add(new JLabel("Triage Urgency:"));
        fieldsPanel.add(triageBox);

        fieldsPanel.add(new JLabel("Nutritional Diet:"));
        fieldsPanel.add(dietBox);

        fieldsPanel.add(new JLabel("Allocation Strategy:"));
        fieldsPanel.add(strategyBox);

        fieldsPanel.add(new JLabel("Patient Latitude:"));
        fieldsPanel.add(latField);

        fieldsPanel.add(new JLabel("Patient Longitude:"));
        fieldsPanel.add(lonField);

        panel.add(fieldsPanel, BorderLayout.NORTH);

        // Action Button
        JButton allocateBtn = new JButton("⚡ Auto-Allocate Bed & Admit Patient");
        allocateBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        allocateBtn.setBackground(new Color(24, 119, 242));
        allocateBtn.setForeground(Color.WHITE);
        allocateBtn.setFocusPainted(false);
        allocateBtn.addActionListener(e -> executeAllocation());

        panel.add(allocateBtn, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createRightPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));

        // Result display box
        resultArea = new JTextArea(4, 30);
        resultArea.setEditable(false);
        resultArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        resultArea.setText("Ready for triage intake. Fill patient details and click Auto-Allocate.");
        JScrollPane resultScroll = new JScrollPane(resultArea);
        resultScroll.setBorder(BorderFactory.createTitledBorder("Allocation Decision"));

        panel.add(resultScroll, BorderLayout.NORTH);

        // Table of currently admitted/active patients
        String[] columns = {"ID", "Name", "Age", "Triage", "Bed ID", "Diet", "Admitted At"};
        patientTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int col) {
                return false;
            }
        };
        JTable table = new JTable(patientTableModel);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createTitledBorder("Active Admitted Patients"));

        panel.add(tableScroll, BorderLayout.CENTER);
        return panel;
    }

    private void executeAllocation() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter patient name.", "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            int age = Integer.parseInt(ageField.getText().trim());
            if (age < 0 || age > 130) {
                JOptionPane.showMessageDialog(this, "Please enter a valid age between 0 and 130.", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            double lat = Double.parseDouble(latField.getText().trim());
            double lon = Double.parseDouble(lonField.getText().trim());
            if (lat < -90.0 || lat > 90.0 || lon < -180.0 || lon > 180.0) {
                JOptionPane.showMessageDialog(this, "Please enter valid GPS coordinates (Latitude: -90 to 90, Longitude: -180 to 180).", "Validation Error", JOptionPane.WARNING_MESSAGE);
                return;
            }

            String gender = (String) genderBox.getSelectedItem();
            TriageLevel triage = (TriageLevel) triageBox.getSelectedItem();
            DietType diet = (DietType) dietBox.getSelectedItem();

            // 1. Select the runtime strategy based on dropdown
            String chosenStrategy = (String) strategyBox.getSelectedItem();
            if (BestEquippedBedStrategy.STRATEGY_NAME.equals(chosenStrategy)) {
                searchEngine.setStrategy(new BestEquippedBedStrategy());
            } else {
                searchEngine.setStrategy(new ClosestBedStrategy());
            }

            // 2. Create the patient model
            Patient patient = new Patient(name, age, gender, triage, diet);

            // 3. Run algorithmic allocation
            Optional<BedAllocationStrategy.BedAllocationResult> allocOpt =
                    searchEngine.allocateBed(patient, lat, lon);

            if (allocOpt.isPresent()) {
                BedAllocationStrategy.BedAllocationResult res = allocOpt.get();
                patient.assignBed(res.getBedId());

                // 4. Save to database
                patientDAO.save(patient);

                // 5. Update UI
                String feedback = String.format(
                        "✓ ALLOCATION SUCCESSFUL:\n" +
                        "• Hospital: %s (%.2f km away)\n" +
                        "• Bed Assigned: Bed #%d [%s]\n" +
                        "• Strategy Applied: %s\n" +
                        "• Details: %s",
                        res.getHospital().getName(),
                        res.getDistanceKm(),
                        res.getBedId(),
                        res.getWardName(),
                        res.getStrategyUsed(),
                        res.getRationale()
                );
                resultArea.setText(feedback);
                resultArea.setForeground(new Color(0, 120, 0));

                nameField.setText("");
                refreshPatientTable();

            } else {
                resultArea.setText("⚠ No available beds found matching criteria across network.");
                resultArea.setForeground(Color.RED);
            }

        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(this, "Please enter valid numbers for Age, Latitude, and Longitude.", "Input Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void refreshPatientTable() {
        patientTableModel.setRowCount(0);
        List<Patient> patients = patientDAO.findAdmittedPatients();
        for (Patient p : patients) {
            patientTableModel.addRow(new Object[]{
                    p.getPatientId(),
                    p.getFullName(),
                    p.getAge(),
                    p.getTriageLevel().name(),
                    (p.getAssignedBedId() != null ? "#" + p.getAssignedBedId() : "Queue"),
                    p.getDietType().name(),
                    (p.getAdmissionTime() != null ? p.getAdmissionTime().toLocalDate().toString() : "Today")
            });
        }
    }
}
