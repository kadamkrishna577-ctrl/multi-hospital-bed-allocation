package com.group15.hospital.ui;

import com.group15.hospital.model.hierarchy.User;
import com.group15.hospital.ui.admin.AdminDashboardPanel;
import com.group15.hospital.ui.admin.TriageRegistrationPanel;

import javax.swing.*;
import java.awt.*;

// main dashboard window with tabs based on user role
public class MainDashboardFrame extends JFrame {

    private final User currentUser;

    public MainDashboardFrame(User user) {
        this.currentUser = (user != null) ? user : new User();

        setTitle("Multi-Hospital Emergency Bed Allocation System");
        setSize(1100, 750);
        setMinimumSize(new Dimension(850, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        // Top Status Header Bar
        add(createHeaderPanel(), BorderLayout.NORTH);

        // Center Tabbed Pane
        JTabbedPane tabbedPane = new JTabbedPane();

        // 1. Triage & Allocation (Always visible)
        tabbedPane.addTab("🏥 Triage & Bed Allocation", new TriageRegistrationPanel());

        // 2. Administration (Gated by admin privileges)
        if (currentUser.hasAdminPrivileges()) {
            tabbedPane.addTab("⚙ Hospital Administration", new AdminDashboardPanel());
        }

        // 3. Ambulance Terminal (Siddhant's P4 Panel if on classpath)
        addAmbulancePanelIfAvailable(tabbedPane);

        // 4. Sanitization Staff Panel (Siddhant's P4 Panel if on classpath)
        addSanitizationPanelIfAvailable(tabbedPane);

        add(tabbedPane, BorderLayout.CENTER);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(30, 41, 59));
        header.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        // System Title
        JLabel titleLabel = new JLabel("Multi-Hospital Bed Network");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        titleLabel.setForeground(Color.WHITE);

        // User info & Logout
        JPanel userPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        userPanel.setOpaque(false);

        String info = String.format("Logged in: %s [%s]", currentUser.getFullName(), currentUser.getRole().name());
        JLabel userLabel = new JLabel(info);
        userLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        userLabel.setForeground(new Color(203, 213, 225));

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        });

        userPanel.add(userLabel);
        userPanel.add(logoutBtn);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(userPanel, BorderLayout.EAST);
        return header;
    }

    private void addAmbulancePanelIfAvailable(JTabbedPane tabbedPane) {
        try {
            Class<?> clazz = Class.forName("com.group15.hospital.ui.operations.AmbulanceTerminalPanel");
            JPanel panel = (JPanel) clazz.getDeclaredConstructor().newInstance();
            tabbedPane.addTab("🚑 Ambulance Terminal", panel);
        } catch (Throwable t) {
            // Panel not loaded or independent module
        }
    }

    private void addSanitizationPanelIfAvailable(JTabbedPane tabbedPane) {
        try {
            Class<?> clazz = Class.forName("com.group15.hospital.ui.operations.SanitizationStaffPanel");
            JPanel panel = (JPanel) clazz.getDeclaredConstructor().newInstance();
            tabbedPane.addTab("🧹 Sanitization Staff", panel);
        } catch (Throwable t) {
            // Panel not loaded or independent module
        }
    }
}
