package com.group15.hospital.ui;

import com.group15.hospital.dao.AuditDAO;
import com.group15.hospital.dao.UserDAO;
import com.group15.hospital.model.hierarchy.User;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

// login window for staff authentication
public class LoginFrame extends JFrame {

    private final UserDAO userDAO;
    private final AuditDAO auditDAO;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JLabel statusLabel;

    public LoginFrame(UserDAO userDAO, AuditDAO auditDAO) {
        this.userDAO = (userDAO != null) ? userDAO : new UserDAO();
        this.auditDAO = (auditDAO != null) ? auditDAO : new AuditDAO();

        setTitle("Hospital Network — Staff Authentication");
        setSize(480, 460);
        setResizable(false);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setLayout(new BorderLayout());

        // Header Panel
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(15, 23, 42));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Emergency Bed Network");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 20));
        titleLabel.setForeground(Color.WHITE);

        JLabel subtitleLabel = new JLabel("Sign in with your staff account credentials");
        subtitleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        subtitleLabel.setForeground(new Color(148, 163, 184));

        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(6));
        headerPanel.add(subtitleLabel);

        add(headerPanel, BorderLayout.NORTH);

        // Center Form
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 30, 10, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        usernameField = new JTextField("admin", 15);
        passwordField = new JPasswordField("admin123", 15);

        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Username:"), gbc);
        gbc.gridx = 1;
        formPanel.add(usernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel("Password:"), gbc);
        gbc.gridx = 1;
        formPanel.add(passwordField, gbc);

        // Status Feedback
        statusLabel = new JLabel(" ");
        statusLabel.setForeground(Color.RED);
        statusLabel.setHorizontalAlignment(SwingConstants.CENTER);
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 2;
        formPanel.add(statusLabel, gbc);

        // Login Button
        JButton loginBtn = new JButton("Sign In");
        loginBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        loginBtn.setBackground(new Color(37, 99, 235));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setFocusPainted(false);
        loginBtn.addActionListener(e -> attemptLogin());

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        formPanel.add(loginBtn, gbc);

        add(formPanel, BorderLayout.CENTER);

        // Bottom: Demo credentials cheatsheet
        JPanel cheatsheetPanel = new JPanel();
        cheatsheetPanel.setBackground(new Color(248, 250, 252));
        cheatsheetPanel.setBorder(BorderFactory.createTitledBorder("Quick Demo Logins (Username / Password)"));

        JLabel creds = new JLabel("<html>" +
                "<b>Admin:</b> admin / admin123 &nbsp;|&nbsp; <b>Doctor:</b> doctor / doc123<br>" +
                "<b>Nurse:</b> nurse / nurse123 &nbsp;|&nbsp; <b>Paramedic:</b> paramedic / para123<br>" +
                "<b>Cleaning:</b> cleaner / clean123" +
                "</html>");
        creds.setFont(new Font("SansSerif", Font.PLAIN, 11));
        cheatsheetPanel.add(creds);

        add(cheatsheetPanel, BorderLayout.SOUTH);

        // Enter key shortcut
        getRootPane().setDefaultButton(loginBtn);
    }

    public LoginFrame() {
        this(new UserDAO(), new AuditDAO());
    }

    private void attemptLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setText("Please enter both username and password.");
            return;
        }

        Optional<User> userOpt = userDAO.authenticate(username, password);

        if (userOpt.isPresent()) {
            User user = userOpt.get();

            // Log successful login
            auditDAO.log(user.getUsername(), "USER_LOGIN", String.valueOf(user.getUserId()),
                    "User signed in with role: " + user.getRole().name());

            // Open Main Dashboard and close Login screen
            dispose();
            SwingUtilities.invokeLater(() -> {
                MainDashboardFrame dashboard = new MainDashboardFrame(user);
                dashboard.setVisible(true);
            });

        } else {
            statusLabel.setText("Invalid username or password. Please try again.");
            passwordField.setText("");
        }
    }
}
