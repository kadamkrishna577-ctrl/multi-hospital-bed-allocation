package com.group15.hospital.ui.operations;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.group15.hospital.dao.AlertDAO;
import com.group15.hospital.model.ambulance.SystemAlert;
import com.group15.hospital.model.enums.UserRole;
import com.group15.hospital.service.dispatch.AlertNotificationManager;
import com.group15.hospital.service.dispatch.AlertObserver;

/**
 * Real-time Swing pop-up alert handler and notification feed panel (SRS F13, Use Cases U5 & U6).
 * <p>
 * Implements {@link AlertObserver} and connects directly to {@link AlertNotificationManager}.
 * Guarantees dispatch to the Swing Event Dispatch Thread (EDT) in <strong>&lt; 0.5 seconds</strong>.
 * Features non-blocking toast pop-up dialogs, an offline alert replay system (U6 Exception 3),
 * and a live alert monitoring table for triage consoles.
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class TriageAlertPopupHandler implements AlertObserver {

    private static final Logger LOGGER = Logger.getLogger(TriageAlertPopupHandler.class.getName());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AlertDAO alertDAO;
    private Component parentComponent;
    private final AlertNotificationManager notificationManager;

    private boolean offlineMode = false;
    private final List<SystemAlert> offlineBuffer = new CopyOnWriteArrayList<>();

    // Live Feed Table Model & Panel
    private DefaultTableModel feedTableModel;
    private JTable feedTable;
    private JPanel alertFeedPanel;
    private JLabel statusBadgeLabel;

    public TriageAlertPopupHandler(AlertDAO alertDAO, Component parentComponent) {
        this.alertDAO = Objects.requireNonNull(alertDAO, "alertDAO must not be null");
        this.parentComponent = parentComponent;
        this.notificationManager = AlertNotificationManager.getInstance(alertDAO);

        initFeedComponents();
        // Register this observer with the manager
        this.notificationManager.registerObserver(this);
    }

    public TriageAlertPopupHandler(AlertDAO alertDAO) {
        this(alertDAO, null);
    }

    public void setParentComponent(Component parentComponent) {
        this.parentComponent = parentComponent;
    }

    /**
     * Unregisters this handler from AlertNotificationManager to prevent duplicate popups on logout.
     */
    public void unregister() {
        if (this.notificationManager != null) {
            this.notificationManager.unregisterObserver(this);
            LOGGER.fine("[TriageAlertPopupHandler] Unregistered alert observer.");
        }
    }

    // ──────────────────────────── AlertObserver Callback ────────────────────────────

    @Override
    public void onAlertReceived(SystemAlert alert) {
        if (alert == null) return;

        // Clean up stale listener if the parent dashboard window has been closed or disposed
        if (parentComponent != null) {
            Window win = SwingUtilities.getWindowAncestor(parentComponent);
            if (win != null && !win.isDisplayable()) {
                unregister();
                return;
            }
        }

        // Ensure execution on Swing Event Dispatch Thread (< 0.5s)
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onAlertReceived(alert));
            return;
        }

        // Add to live feed table regardless of offline/online state
        appendAlertToTable(alert);

        if (offlineMode) {
            // Buffer in memory as simulated offline screen (U6 Exception 3)
            offlineBuffer.add(alert);
            LOGGER.info("[TriageAlertPopupHandler] Terminal is OFFLINE: buffered alert #" + alert.getAlertId());
            return;
        }

        // Show non-blocking styled pop-up dialog / toast
        showNonBlockingAlertPopup(alert);
    }

    // ──────────────────────────── Non-Blocking Pop-Up Dialog ────────────────────────────

    /**
     * Displays a non-blocking styled dialog over the parent window.
     */
    public void showNonBlockingAlertPopup(SystemAlert alert) {
        if (GraphicsEnvironment.isHeadless()) {
            LOGGER.fine("[TriageAlertPopupHandler] Running in headless environment — skipping visual dialog.");
            return;
        }
        Window owner = (parentComponent != null) ? SwingUtilities.getWindowAncestor(parentComponent) : null;
        JDialog dialog = new JDialog(owner, "Critical Care Alert: " + alert.getAlertType());
        dialog.setModal(false); // Non-blocking!
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setLayout(new BorderLayout());

        Color headerColor = getHeaderColorForType(alert.getAlertType());
        Color bgLightColor = getBackgroundColorForType(alert.getAlertType());

        // Header Banner
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(headerColor);
        headerPanel.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel titleLabel = new JLabel("⚡ " + alert.getAlertType().replace("_", " "));
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setForeground(Color.WHITE);

        JLabel timeLabel = new JLabel(alert.getTimestamp().format(TIME_FMT));
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        timeLabel.setForeground(new Color(230, 230, 230));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(timeLabel, BorderLayout.EAST);
        dialog.add(headerPanel, BorderLayout.NORTH);

        // Content Area
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setBackground(bgLightColor);
        contentPanel.setBorder(new EmptyBorder(15, 20, 15, 20));

        JTextArea messageArea = new JTextArea(alert.getMessage());
        messageArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        messageArea.setWrapStyleWord(true);
        messageArea.setLineWrap(true);
        messageArea.setEditable(false);
        messageArea.setOpaque(false);
        messageArea.setBorder(null);

        JLabel metaLabel = new JLabel(String.format("Hospital: %s  |  Role: %s  |  Bed: %s",
                alert.getHospitalName(), alert.getTargetRole(),
                alert.getBedId() != null ? "#" + alert.getBedId() : "N/A"));
        metaLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        metaLabel.setForeground(Color.DARK_GRAY);

        contentPanel.add(messageArea, BorderLayout.CENTER);
        contentPanel.add(metaLabel, BorderLayout.SOUTH);
        dialog.add(contentPanel, BorderLayout.CENTER);

        // Footer / Button Panel
        JPanel footerPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        footerPanel.setBackground(Color.WHITE);
        footerPanel.setBorder(new LineBorder(new Color(220, 220, 220), 1, true));

        JButton ackButton = new JButton("✓ Acknowledge");
        ackButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ackButton.setBackground(headerColor);
        ackButton.setForeground(Color.WHITE);
        ackButton.setFocusPainted(false);
        ackButton.addActionListener(e -> {
            alertDAO.markAlertAcknowledged(alert.getAlertId());
            alert.setAcknowledged(true);
            updateAlertStatusInTable(alert.getAlertId(), true);
            dialog.dispose();
        });

        JButton closeButton = new JButton("Dismiss");
        closeButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        closeButton.addActionListener(e -> dialog.dispose());

        footerPanel.add(closeButton);
        footerPanel.add(ackButton);
        dialog.add(footerPanel, BorderLayout.SOUTH);

        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 500), Math.max(dialog.getHeight(), 260));

        if (owner != null && owner.isVisible()) {
            dialog.setLocationRelativeTo(owner);
        } else {
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            dialog.setLocation(screenSize.width - dialog.getWidth() - 30, 50);
        }

        dialog.setVisible(true);
    }

    private Color getHeaderColorForType(String alertType) {
        return switch (alertType) {
            case SystemAlert.TYPE_BED_CLEANED -> new Color(46, 125, 50);        // Forest Green
            case SystemAlert.TYPE_AMBULANCE_RESERVED -> new Color(21, 101, 192); // Deep Blue
            case SystemAlert.TYPE_AMBULANCE_REROUTED -> new Color(230, 81, 0);   // Dark Orange
            case SystemAlert.TYPE_NO_CAPACITY -> new Color(198, 40, 40);         // Crimson Red
            case SystemAlert.TYPE_WAITLIST_READY -> new Color(106, 27, 154);     // Purple
            case SystemAlert.TYPE_RESERVATION_EXPIRED -> new Color(97, 97, 97);  // Charcoal Gray
            default -> new Color(69, 90, 100);
        };
    }

    private Color getBackgroundColorForType(String alertType) {
        return switch (alertType) {
            case SystemAlert.TYPE_BED_CLEANED -> new Color(237, 247, 237);
            case SystemAlert.TYPE_AMBULANCE_RESERVED -> new Color(227, 242, 253);
            case SystemAlert.TYPE_AMBULANCE_REROUTED -> new Color(255, 243, 224);
            case SystemAlert.TYPE_NO_CAPACITY -> new Color(255, 235, 238);
            case SystemAlert.TYPE_WAITLIST_READY -> new Color(243, 229, 245);
            default -> new Color(250, 250, 250);
        };
    }

    // ──────────────────────────── Offline Mode & Sync (U6 Ex. 3) ────────────────────────────

    public void setOfflineMode(boolean offline) {
        this.offlineMode = offline;
        if (statusBadgeLabel != null) {
            if (offline) {
                statusBadgeLabel.setText("● TERMINAL OFFLINE (Queuing Alerts)");
                statusBadgeLabel.setForeground(new Color(198, 40, 40));
            } else {
                statusBadgeLabel.setText("● TERMINAL ONLINE");
                statusBadgeLabel.setForeground(new Color(46, 125, 50));
            }
        }
    }

    public boolean isOfflineMode() {
        return offlineMode;
    }

    /**
     * Replays queued offline alerts upon reconnection (SRS F13 / U6 Exception 3).
     */
    public void syncOfflineAlerts(String hospitalName, UserRole role) {
        notificationManager.checkAndDispatchOfflineAlerts(hospitalName, role, this);

        // Also flush any internally buffered alerts
        if (!offlineBuffer.isEmpty()) {
            List<SystemAlert> buffered = List.copyOf(offlineBuffer);
            offlineBuffer.clear();
            for (SystemAlert a : buffered) {
                showNonBlockingAlertPopup(a);
            }
        }
    }

    // ──────────────────────────── Live Feed Table Panel ────────────────────────────

    private void initFeedComponents() {
        String[] columnNames = {"Alert ID", "Time", "Type", "Hospital", "Bed", "Target Role", "Message", "Status"};
        feedTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        feedTable = new JTable(feedTableModel);
        feedTable.setRowHeight(26);
        feedTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        feedTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        feedTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Custom Renderer for Alert Type column
        feedTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (value != null) {
                    String type = value.toString();
                    setForeground(getHeaderColorForType(type));
                    setFont(getFont().deriveFont(Font.BOLD));
                }
                return c;
            }
        });

        // Set column widths
        feedTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        feedTable.getColumnModel().getColumn(1).setPreferredWidth(70);
        feedTable.getColumnModel().getColumn(2).setPreferredWidth(140);
        feedTable.getColumnModel().getColumn(3).setPreferredWidth(140);
        feedTable.getColumnModel().getColumn(4).setPreferredWidth(45);
        feedTable.getColumnModel().getColumn(5).setPreferredWidth(80);
        feedTable.getColumnModel().getColumn(6).setPreferredWidth(260);
        feedTable.getColumnModel().getColumn(7).setPreferredWidth(100);

        // Build Panel
        alertFeedPanel = new JPanel(new BorderLayout(5, 5));
        alertFeedPanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        // Top Toolbar
        JPanel topBar = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Live Triage Alerts Feed (Real-Time Observer)");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));

        statusBadgeLabel = new JLabel("● TERMINAL ONLINE");
        statusBadgeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusBadgeLabel.setForeground(new Color(46, 125, 50));

        topBar.add(title, BorderLayout.WEST);
        topBar.add(statusBadgeLabel, BorderLayout.EAST);
        alertFeedPanel.add(topBar, BorderLayout.NORTH);

        // Center Table
        JScrollPane scrollPane = new JScrollPane(feedTable);
        alertFeedPanel.add(scrollPane, BorderLayout.CENTER);

        // Bottom Action Bar
        JPanel bottomBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));

        JButton ackSelectedBtn = new JButton("Acknowledge Selected");
        ackSelectedBtn.addActionListener(e -> acknowledgeSelectedRow());

        JButton toggleOfflineBtn = new JButton("Toggle Offline Mode (Demo U6 Ex.3)");
        toggleOfflineBtn.addActionListener(e -> {
            setOfflineMode(!offlineMode);
            if (!offlineMode) {
                syncOfflineAlerts("City Central Hospital", UserRole.NURSE);
            }
        });

        JButton syncOfflineBtn = new JButton("Sync Offline Alerts");
        syncOfflineBtn.addActionListener(e -> syncOfflineAlerts("City Central Hospital", UserRole.NURSE));

        JButton clearBtn = new JButton("Clear Feed");
        clearBtn.addActionListener(e -> feedTableModel.setRowCount(0));

        bottomBar.add(ackSelectedBtn);
        bottomBar.add(toggleOfflineBtn);
        bottomBar.add(syncOfflineBtn);
        bottomBar.add(clearBtn);

        alertFeedPanel.add(bottomBar, BorderLayout.SOUTH);
    }

    private void appendAlertToTable(SystemAlert alert) {
        String statusText = alert.isAcknowledged() ? "ACKNOWLEDGED" : "PENDING";
        feedTableModel.insertRow(0, new Object[]{
                alert.getAlertId(),
                alert.getTimestamp().format(TIME_FMT),
                alert.getAlertType(),
                alert.getHospitalName(),
                alert.getBedId() != null ? alert.getBedId() : "-",
                alert.getTargetRole(),
                alert.getMessage(),
                statusText
        });
    }

    private void updateAlertStatusInTable(long alertId, boolean acknowledged) {
        for (int i = 0; i < feedTableModel.getRowCount(); i++) {
            Object idVal = feedTableModel.getValueAt(i, 0);
            if (idVal instanceof Long && (Long) idVal == alertId) {
                feedTableModel.setValueAt(acknowledged ? "ACKNOWLEDGED" : "PENDING", i, 7);
                break;
            }
        }
    }

    private void acknowledgeSelectedRow() {
        int selectedRow = feedTable.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(alertFeedPanel, "Please select an alert from the table to acknowledge.", "No Selection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        long alertId = (Long) feedTableModel.getValueAt(selectedRow, 0);
        alertDAO.markAlertAcknowledged(alertId);
        feedTableModel.setValueAt("ACKNOWLEDGED", selectedRow, 7);
        JOptionPane.showMessageDialog(alertFeedPanel, "Alert #" + alertId + " marked as acknowledged.", "Acknowledged", JOptionPane.INFORMATION_MESSAGE);
    }

    public JPanel getAlertFeedPanel() {
        return alertFeedPanel;
    }

    public DefaultTableModel getFeedTableModel() {
        return feedTableModel;
    }
    public JTable getFeedTable() {
        return feedTable;
    }

    public void handleAcknowledgeSelected() {
        acknowledgeSelectedRow();
    }
}

