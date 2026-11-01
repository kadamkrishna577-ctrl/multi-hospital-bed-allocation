package com.group15.hospital.ui;

import com.group15.hospital.dao.*;
import com.group15.hospital.model.enums.UserRole;
import com.group15.hospital.model.hierarchy.User;
import com.group15.hospital.service.auxiliary.BillingService;
import com.group15.hospital.service.auxiliary.KitchenService;
import com.group15.hospital.service.containment.IsolationContainmentService;
import com.group15.hospital.service.dispatch.AlertNotificationManager;
import com.group15.hospital.service.dispatch.AmbulanceDispatchService;
import com.group15.hospital.service.dispatch.SanitizationService;
import com.group15.hospital.service.preemption.EmergencyPreemptionService;
import com.group15.hospital.service.preemption.EquipmentMaintenanceService;
import com.group15.hospital.service.preemption.WaitlistManager;
import com.group15.hospital.ui.admin.AdminDashboardPanel;
import com.group15.hospital.ui.admin.TriageRegistrationPanel;
import com.group15.hospital.ui.auxiliary.BillingAndKitchenPanel;
import com.group15.hospital.ui.auxiliary.IsolationControlPanel;
import com.group15.hospital.ui.clinical.EmergencyOverrideDialog;
import com.group15.hospital.ui.clinical.EquipmentManagementPanel;
import com.group15.hospital.ui.operations.AmbulanceTerminalPanel;
import com.group15.hospital.ui.operations.SanitizationStaffPanel;
import com.group15.hospital.ui.operations.TriageAlertPopupHandler;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Main dashboard container for the Multi-Hospital Real-Time Bed Allocation Network.
 * <p>
 * Aggregates all operational Swing panels across clinical, dispatch, sanitization,
 * admin triage, emergency pre-emption services, and auxiliary billing &amp; infection containment
 * inside a role-aware {@link JTabbedPane}.
 * </p>
 *
 * @author Group 15 [Shared: Saksham, Krishna, Jatin, Siddhant]
 */
public class MainDashboardFrame extends JFrame {

    private final User currentUser;
    private final JTabbedPane tabbedPane;

    // DAOs
    private final BedDAO bedDAO;
    private final EquipmentDAO equipmentDAO;
    private final WaitlistDAO waitlistDAO;
    private final AmbulanceDAO ambulanceDAO;
    private final AlertDAO alertDAO;
    private final WardTopologyDAO wardTopologyDAO;
    private final BillingDAO billingDAO;
    private final KitchenOrderDAO kitchenOrderDAO;
    private final PatientDAO patientDAO;

    // Services
    private final WaitlistManager waitlistManager;
    private final EmergencyPreemptionService preemptionService;
    private final EquipmentMaintenanceService maintenanceService;
    private final AlertNotificationManager alertManager;
    private final AmbulanceDispatchService ambulanceDispatchService;
    private final SanitizationService sanitizationService;
    private final BillingService billingService;
    private final KitchenService kitchenService;
    private final IsolationContainmentService isolationService;

    public MainDashboardFrame() {
        this(new User("admin", "admin123", "Chief Administrator", UserRole.ADMIN, 1));
    }

    public MainDashboardFrame(User user) {
        super("Multi-Hospital Bed Allocation & Emergency Dispatch Network (Group 15)");
        this.currentUser = (user != null) ? user : new User("admin", "admin123", "Chief Administrator", UserRole.ADMIN, 1);

        // 1. Initialize DAOs
        this.bedDAO = new BedDAO();
        this.equipmentDAO = new EquipmentDAO();
        this.waitlistDAO = new WaitlistDAO();
        this.ambulanceDAO = new AmbulanceDAO();
        this.alertDAO = new AlertDAO();
        this.wardTopologyDAO = new WardTopologyDAO();
        this.billingDAO = new BillingDAO();
        this.kitchenOrderDAO = new KitchenOrderDAO();
        this.patientDAO = new PatientDAO();

        // 2. Initialize Services
        this.waitlistManager = new WaitlistManager(waitlistDAO, true);
        this.preemptionService = new EmergencyPreemptionService(bedDAO, waitlistManager);
        this.maintenanceService = new EquipmentMaintenanceService(equipmentDAO);
        this.alertManager = AlertNotificationManager.getInstance(alertDAO);
        this.ambulanceDispatchService = new AmbulanceDispatchService(ambulanceDAO, alertDAO, alertManager);
        this.sanitizationService = new SanitizationService(alertDAO, alertManager, waitlistManager);

        // Phase 3 Auxiliary & Isolation Services (Jatin - P3)
        AuditDAO auditDAO = new AuditDAO();
        this.billingService = new BillingService(billingDAO, auditDAO);
        this.kitchenService = new KitchenService(kitchenOrderDAO, auditDAO);
        this.isolationService = new IsolationContainmentService(wardTopologyDAO, bedDAO, patientDAO, auditDAO);

        // Wire Phase 3 Observers: Billing and Kitchen observe patient transfers on IsolationContainmentService
        this.isolationService.registerListener(this.billingService);
        this.isolationService.registerListener(this.kitchenService);

        // 3. Register Global Triage Alert Observer for Popups
        TriageAlertPopupHandler popupHandler = new TriageAlertPopupHandler(alertDAO, this);
        this.alertManager.registerObserver(popupHandler);

        // 4. Configure Window Layout
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setPreferredSize(new Dimension(1150, 780));
        setMinimumSize(new Dimension(900, 650));

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top Header
        JPanel headerPanel = createHeaderPanel();
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Tabbed Module Container
        this.tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.BOLD, 13));

        // Tab 1: Triage Registration & Bed Search (Saksham - P1)
        tabbedPane.addTab("🏥 Triage & Allocation", new TriageRegistrationPanel());

        // Tab 2: Equipment Management & Maintenance (Krishna - P2)
        EquipmentManagementPanel equipmentPanel = new EquipmentManagementPanel(maintenanceService);
        tabbedPane.addTab("⚙️ Equipment Management", equipmentPanel);

        // Tab 3: Ambulance Terminal & Reservations (Siddhant - P4)
        AmbulanceTerminalPanel ambulancePanel = new AmbulanceTerminalPanel(ambulanceDAO, ambulanceDispatchService);
        tabbedPane.addTab("🚑 Ambulance Terminal", ambulancePanel);

        // Tab 4: Sanitization Staff Terminal (Siddhant - P4)
        SanitizationStaffPanel sanitizationPanel = new SanitizationStaffPanel(sanitizationService);
        tabbedPane.addTab("🧹 Sanitization Staff", sanitizationPanel);

        // Tab 5: Hospital Administration (Saksham - P1)
        if (currentUser.hasAdminPrivileges()) {
            tabbedPane.addTab("⚙ Hospital Administration", new AdminDashboardPanel());
        }

        // Tab 6: Ward Adjacency & Infection Containment (Jatin - P3)
        IsolationControlPanel isolationPanel = new IsolationControlPanel(
                isolationService, wardTopologyDAO, bedDAO, patientDAO);
        tabbedPane.addTab("🛡️ Ward Isolation (P3)", isolationPanel);

        // Tab 7: Auxiliary Billing & Kitchen Dispatch (Jatin - P3)
        BillingAndKitchenPanel billingKitchenPanel = new BillingAndKitchenPanel(
                billingService, kitchenService);
        tabbedPane.addTab("💳 Billing & Kitchen (P3)", billingKitchenPanel);

        // Listen for tab switching to trigger dynamic refresh
        tabbedPane.addChangeListener(e -> {
            Component selected = tabbedPane.getSelectedComponent();
            if (selected == isolationPanel) {
                isolationPanel.refreshTable();
            } else if (selected == billingKitchenPanel) {
                billingKitchenPanel.refreshData();
            }
        });

        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        // Bottom Status Bar
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 4, 4, 4));
        String statusText = String.format("System Online | All services running in real-time | Active Hospital ID: %s | User: %s (%s)",
                currentUser.getHospitalId() != null ? currentUser.getHospitalId() : "Network Central",
                currentUser.getFullName(),
                currentUser.getRole().getDisplayName());
        JLabel statusLabel = new JLabel(statusText);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusBar.add(statusLabel, BorderLayout.WEST);
        mainPanel.add(statusBar, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout(10, 10));
        header.setBackground(new Color(30, 41, 59));
        header.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        // System Title
        JLabel titleLabel = new JLabel("🏥 Multi-Hospital Real-Time Emergency Bed Network");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);
        header.add(titleLabel, BorderLayout.WEST);

        // Action Panel (Emergency Override Dialog + User Info + Logout)
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);

        JButton overrideBtn = new JButton("🚨 Emergency Override");
        overrideBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        overrideBtn.setBackground(new Color(220, 38, 38));
        overrideBtn.setForeground(Color.WHITE);
        overrideBtn.setFocusPainted(false);
        overrideBtn.addActionListener(e -> {
            EmergencyOverrideDialog dialog = new EmergencyOverrideDialog(this, preemptionService);
            dialog.setVisible(true);
        });
        actionPanel.add(overrideBtn);

        String userDesc = String.format("<html><span style='color: #cbd5e1;'>Logged in:</span> <b style='color: white;'>%s</b> <span style='color: #94a3b8;'>[%s]</span></html>",
                currentUser.getFullName(), currentUser.getRole().name());
        JLabel userLabel = new JLabel(userDesc);
        userLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        actionPanel.add(userLabel);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        });
        actionPanel.add(logoutBtn);

        header.add(actionPanel, BorderLayout.EAST);
        return header;
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
