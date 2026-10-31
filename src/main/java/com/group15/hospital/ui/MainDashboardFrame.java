package com.group15.hospital.ui;

import com.group15.hospital.dao.*;
import com.group15.hospital.service.dispatch.AlertNotificationManager;
import com.group15.hospital.service.dispatch.AmbulanceDispatchService;
import com.group15.hospital.service.dispatch.SanitizationService;
import com.group15.hospital.service.preemption.EmergencyPreemptionService;
import com.group15.hospital.service.preemption.EquipmentMaintenanceService;
import com.group15.hospital.service.preemption.WaitlistManager;
import com.group15.hospital.ui.clinical.EmergencyOverrideDialog;
import com.group15.hospital.ui.clinical.EquipmentManagementPanel;
import com.group15.hospital.ui.operations.AmbulanceTerminalPanel;
import com.group15.hospital.ui.operations.SanitizationStaffPanel;
import com.group15.hospital.ui.operations.TriageAlertPopupHandler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Main host container window for the Multi-Hospital Bed Allocation System.
 * <p>
 * Aggregates all operational Swing panels across clinical, dispatch, sanitization,
 * and emergency pre-emption services inside a responsive {@link JTabbedPane}.
 * </p>
 *
 * @author Group 15 [Shared]
 */
public class MainDashboardFrame extends JFrame {

    private final JTabbedPane tabbedPane;

    // DAOs
    private final BedDAO bedDAO;
    private final EquipmentDAO equipmentDAO;
    private final WaitlistDAO waitlistDAO;
    private final AmbulanceDAO ambulanceDAO;
    private final AlertDAO alertDAO;

    // Services
    private final WaitlistManager waitlistManager;
    private final EmergencyPreemptionService preemptionService;
    private final EquipmentMaintenanceService maintenanceService;
    private final AlertNotificationManager alertManager;
    private final AmbulanceDispatchService ambulanceDispatchService;
    private final SanitizationService sanitizationService;

    public MainDashboardFrame() {
        super("Multi-Hospital Bed Allocation & Emergency Dispatch System (Group 15)");

        // 1. Initialize DAOs
        this.bedDAO = new BedDAO();
        this.equipmentDAO = new EquipmentDAO();
        this.waitlistDAO = new WaitlistDAO();
        this.ambulanceDAO = new AmbulanceDAO();
        this.alertDAO = new AlertDAO();

        // 2. Initialize Services
        this.waitlistManager = new WaitlistManager(waitlistDAO, true);
        this.preemptionService = new EmergencyPreemptionService(bedDAO, waitlistManager);
        this.maintenanceService = new EquipmentMaintenanceService(equipmentDAO);
        this.alertManager = AlertNotificationManager.getInstance(alertDAO);
        this.ambulanceDispatchService = new AmbulanceDispatchService(ambulanceDAO, alertDAO, alertManager);
        this.sanitizationService = new SanitizationService(alertDAO, alertManager, waitlistManager);

        // 3. Register Global Triage Alert Observer for popups
        TriageAlertPopupHandler popupHandler = new TriageAlertPopupHandler(alertDAO, this);
        this.alertManager.registerObserver(popupHandler);

        // 4. Configure Frame Layout
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1100, 750));
        setMinimumSize(new Dimension(900, 600));

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top Header & Action Bar
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Panel for Modules
        this.tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 13));

        // Tab 1: Equipment Management (Krishna - P2)
        EquipmentManagementPanel equipmentPanel = new EquipmentManagementPanel(maintenanceService);
        tabbedPane.addTab("⚙️ Equipment Management", equipmentPanel);

        // Tab 2: Ambulance Dispatch & Reservations (Siddhant - P4)
        AmbulanceTerminalPanel ambulancePanel = new AmbulanceTerminalPanel(ambulanceDAO, ambulanceDispatchService);
        tabbedPane.addTab("🚑 Ambulance Terminal", ambulancePanel);

        // Tab 3: Sanitization Staff Terminal (Siddhant - P4)
        SanitizationStaffPanel sanitizationPanel = new SanitizationStaffPanel(sanitizationService);
        tabbedPane.addTab("🧹 Sanitization Staff", sanitizationPanel);

        // Tab 4: Hospital Admin & Triage (Placeholder for Saksham - P1)
        tabbedPane.addTab("🏥 Admin & Triage (P1)", createPlaceholderPanel("Admin & Triage Registration Panel (P1 - Saksham)"));

        // Tab 5: Isolation & Billing (Placeholder for Jatin - P3)
        tabbedPane.addTab("💳 Billing & Isolation (P3)", createPlaceholderPanel("Ward Topology Isolation & Live Billing Panel (P3 - Jatin)"));

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Bottom Status Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 4, 4, 4));
        JLabel statusLabel = new JLabel("System Online | All services running in real-time | Active Hospital: City Central Hospital");
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 10));
        header.setBorder(new EmptyBorder(5, 5, 8, 5));

        JLabel title = new JLabel("🏥 Multi-Hospital Real-Time Bed Allocation Network");
        title.setFont(new Font("SansSerif", Font.BOLD, 18));
        header.add(title, BorderLayout.WEST);

        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

        JButton overrideBtn = new JButton("🚨 Emergency Override Dialog");
        overrideBtn.setFont(new Font("SansSerif", Font.BOLD, 13));
        overrideBtn.setBackground(new Color(180, 20, 20));
        overrideBtn.setForeground(Color.WHITE);
        overrideBtn.setFocusPainted(false);
        overrideBtn.addActionListener(e -> {
            EmergencyOverrideDialog dialog = new EmergencyOverrideDialog(this, preemptionService);
            dialog.setVisible(true);
        });
        actionPanel.add(overrideBtn);

        header.add(actionPanel, BorderLayout.EAST);
        return header;
    }

    private JPanel createPlaceholderPanel(String moduleName) {
        JPanel p = new JPanel(new GridBagLayout());
        JLabel label = new JLabel("⏳ Module " + moduleName + " pending completion by assigned team member.");
        label.setFont(new Font("SansSerif", Font.ITALIC, 14));
        label.setForeground(Color.GRAY);
        p.add(label);
        return p;
    }

    /**
     * Standalone main method to launch the dashboard.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new MainDashboardFrame().setVisible(true);
        });
    }
}
