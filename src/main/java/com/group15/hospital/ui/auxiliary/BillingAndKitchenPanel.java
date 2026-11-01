package com.group15.hospital.ui.auxiliary;

import com.group15.hospital.model.billing.BillingLineItem;
import com.group15.hospital.model.billing.BillingRecord;
import com.group15.hospital.model.billing.KitchenMealOrder;
import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.service.auxiliary.BillingService;
import com.group15.hospital.service.auxiliary.KitchenService;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Auxiliary Services Control Panel: Live Patient Billing &amp; Kitchen Dietary Queue.
 * <p>
 * Implements Phase 3 (Jatin) auxiliary requirements (SRS F11, F14 &amp; Use Cases U3, U7).
 * Connects directly to {@link BillingService} and {@link KitchenService} (observers of bed transfers
 * and patient admissions/discharges), providing:
 * <ul>
 *   <li><b>Patient Billing:</b> Master/Detail invoice inspection with itemized equipment and hourly rate breakdown,
 *       manual medical charge logging, and invoice settlement.</li>
 *   <li><b>Kitchen Dietary Queue:</b> Real-time meal dispatch tickets reflecting clinical ward rules (e.g. ICU IV fluids,
 *       post-ICU liquid step-downs), manual order placement, and fulfillment workflow transitions.</li>
 * </ul>
 * </p>
 *
 * @author Jatin (Module P3)
 * @see BillingService
 * @see KitchenService
 */
public class BillingAndKitchenPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final BillingService billingService;
    private final KitchenService kitchenService;

    // Billing UI Components
    private DefaultTableModel billsMasterModel;
    private JTable billsMasterTable;
    private DefaultTableModel lineItemsDetailModel;
    private JTable lineItemsDetailTable;
    private JLabel selectedBillSummaryLabel;

    // Kitchen UI Components
    private DefaultTableModel kitchenQueueModel;
    private JTable kitchenQueueTable;
    private JComboBox<String> kitchenFilterCombo;

    public BillingAndKitchenPanel(BillingService billingService, KitchenService kitchenService) {
        this.billingService = Objects.requireNonNull(billingService, "billingService must not be null");
        this.kitchenService = Objects.requireNonNull(kitchenService, "kitchenService must not be null");

        setLayout(new BorderLayout(8, 8));
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // 1. Header Banner
        add(createHeaderBanner(), BorderLayout.NORTH);

        // 2. Central Split Pane (Left: Billing, Right: Kitchen)
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createBillingSection(),
                createKitchenSection());
        mainSplitPane.setResizeWeight(0.52);
        mainSplitPane.setDividerLocation(580);
        mainSplitPane.setBorder(null);
        add(mainSplitPane, BorderLayout.CENTER);

        // 3. Populate Demo Data if Empty
        initDemoDataIfEmpty();

        // 4. Initial Load
        refreshData();
    }

    public BillingAndKitchenPanel() {
        this(new BillingService(), new KitchenService());
    }

    // ──────────────────────────── Header Banner ────────────────────────────

    private JPanel createHeaderBanner() {
        JPanel banner = new JPanel(new BorderLayout());
        banner.setBackground(new Color(30, 58, 138)); // Deep Indigo / Royal Navy
        banner.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel title = new JLabel("💳 AUXILIARY SERVICES: LIVE BILLING & KITCHEN DIETARY DISPATCH");
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        title.setForeground(Color.WHITE);

        JLabel subtitle = new JLabel("Module P3: Observer Pattern (U7) • Auto Bed/Equipment Accrual • Clinical Diet Regimen");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(new Color(191, 219, 254));

        banner.add(title, BorderLayout.WEST);
        banner.add(subtitle, BorderLayout.EAST);
        return banner;
    }

    // ──────────────────────────── Section 1: Billing ────────────────────────────

    private JPanel createBillingSection() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(147, 197, 253), 1, true),
                        "Patient Billing & Invoicing (BillingService)",
                        TitledBorder.LEADING, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13), new Color(30, 58, 138)),
                new EmptyBorder(6, 6, 6, 6)));

        // Split Master (Bills) and Detail (Line Items)
        JSplitPane billingSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createMasterBillsPanel(),
                createDetailLineItemsPanel());
        billingSplit.setResizeWeight(0.55);
        billingSplit.setBorder(null);

        panel.add(billingSplit, BorderLayout.CENTER);
        panel.add(createBillingActionBar(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createMasterBillsPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));

        String[] cols = {"Bill ID", "Patient ID", "Total ($)", "Status", "Generated At"};
        billsMasterModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        billsMasterTable = new JTable(billsMasterModel);
        billsMasterTable.setRowHeight(24);
        billsMasterTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        billsMasterTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        billsMasterTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Payment status badge renderer
        billsMasterTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(table, val, isSel, hasFocus, r, c);
                if (val != null) {
                    String text = val.toString();
                    setFont(getFont().deriveFont(Font.BOLD));
                    setHorizontalAlignment(CENTER);
                    if ("PAID".equalsIgnoreCase(text)) {
                        setBackground(new Color(220, 252, 231));
                        setForeground(new Color(22, 101, 52));
                    } else {
                        setBackground(new Color(254, 243, 199));
                        setForeground(new Color(180, 83, 9));
                    }
                }
                if (isSel) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground());
                }
                return comp;
            }
        });

        // Row selection listener to populate detail table
        billsMasterTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                loadSelectedBillLineItems();
            }
        });

        JScrollPane sp = new JScrollPane(billsMasterTable);
        p.add(new JLabel("Master Invoices:"), BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel createDetailLineItemsPanel() {
        JPanel p = new JPanel(new BorderLayout(4, 4));

        selectedBillSummaryLabel = new JLabel("Itemized Bed & Equipment Line Items (Select a Bill above):");
        selectedBillSummaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        selectedBillSummaryLabel.setForeground(new Color(51, 65, 85));

        String[] cols = {"Item ID", "Description", "Amount ($)", "Recorded At"};
        lineItemsDetailModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        lineItemsDetailTable = new JTable(lineItemsDetailModel);
        lineItemsDetailTable.setRowHeight(22);
        lineItemsDetailTable.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lineItemsDetailTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 11));

        // Format column widths
        lineItemsDetailTable.getColumnModel().getColumn(0).setPreferredWidth(55);
        lineItemsDetailTable.getColumnModel().getColumn(1).setPreferredWidth(260);
        lineItemsDetailTable.getColumnModel().getColumn(2).setPreferredWidth(70);
        lineItemsDetailTable.getColumnModel().getColumn(3).setPreferredWidth(110);

        JScrollPane sp = new JScrollPane(lineItemsDetailTable);
        p.add(selectedBillSummaryLabel, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        return p;
    }

    private JPanel createBillingActionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        bar.setBackground(new Color(241, 245, 249));
        bar.setBorder(new LineBorder(new Color(226, 232, 240)));

        JButton addChargeBtn = new JButton("➕ Add Manual Charge");
        addChargeBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        addChargeBtn.setBackground(new Color(37, 99, 235));
        addChargeBtn.setForeground(Color.WHITE);
        addChargeBtn.setFocusPainted(false);
        addChargeBtn.addActionListener(e -> showAddChargeDialog());

        JButton settleBillBtn = new JButton("💳 Settle / Mark Paid");
        settleBillBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        settleBillBtn.setBackground(new Color(22, 163, 74));
        settleBillBtn.setForeground(Color.WHITE);
        settleBillBtn.setFocusPainted(false);
        settleBillBtn.addActionListener(e -> handleSettleBill());

        JButton refreshBillsBtn = new JButton("🔄 Refresh Bills");
        refreshBillsBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        refreshBillsBtn.setFocusPainted(false);
        refreshBillsBtn.addActionListener(e -> refreshBills());

        bar.add(addChargeBtn);
        bar.add(settleBillBtn);
        bar.add(refreshBillsBtn);
        return bar;
    }

    // ──────────────────────────── Section 2: Kitchen ────────────────────────────

    private JPanel createKitchenSection() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(new CompoundBorder(
                new TitledBorder(new LineBorder(new Color(251, 191, 36), 1, true),
                        "Kitchen Dietary Dispatch Queue (KitchenService)",
                        TitledBorder.LEADING, TitledBorder.TOP,
                        new Font("Segoe UI", Font.BOLD, 13), new Color(180, 83, 9)),
                new EmptyBorder(6, 6, 6, 6)));

        // Kitchen Table
        String[] cols = {"Order ID", "Patient ID", "Diet Type", "Status", "Ordered At"};
        kitchenQueueModel = new DefaultTableModel(cols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };

        kitchenQueueTable = new JTable(kitchenQueueModel);
        kitchenQueueTable.setRowHeight(25);
        kitchenQueueTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        kitchenQueueTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        kitchenQueueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Diet Type badge renderer
        kitchenQueueTable.getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(table, val, isSel, hasFocus, r, c);
                if (val != null) {
                    String dt = val.toString();
                    setFont(getFont().deriveFont(Font.BOLD));
                    setHorizontalAlignment(CENTER);
                    if ("IV_FLUIDS".equalsIgnoreCase(dt)) {
                        setBackground(new Color(243, 232, 255));
                        setForeground(new Color(126, 34, 206));
                    } else if ("LIQUID".equalsIgnoreCase(dt)) {
                        setBackground(new Color(204, 251, 241));
                        setForeground(new Color(15, 118, 110));
                    } else {
                        setBackground(new Color(241, 245, 249));
                        setForeground(new Color(51, 65, 85));
                    }
                }
                if (isSel) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground());
                }
                return comp;
            }
        });

        // Status badge renderer
        kitchenQueueTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFocus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(table, val, isSel, hasFocus, r, c);
                if (val != null) {
                    String status = val.toString();
                    setFont(getFont().deriveFont(Font.BOLD));
                    setHorizontalAlignment(CENTER);
                    switch (status.toUpperCase()) {
                        case "PENDING" -> {
                            setBackground(new Color(254, 243, 199));
                            setForeground(new Color(180, 83, 9));
                        }
                        case "PREPARING" -> {
                            setBackground(new Color(219, 234, 254));
                            setForeground(new Color(29, 78, 216));
                        }
                        case "DELIVERED" -> {
                            setBackground(new Color(220, 252, 231));
                            setForeground(new Color(22, 101, 52));
                        }
                        case "CANCELLED" -> {
                            setBackground(new Color(241, 245, 249));
                            setForeground(new Color(100, 116, 139));
                        }
                    }
                }
                if (isSel) {
                    setBackground(table.getSelectionBackground());
                    setForeground(table.getSelectionForeground());
                }
                return comp;
            }
        });

        // Top Filter Bar
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        filterPanel.add(new JLabel("Queue Filter:"));
        kitchenFilterCombo = new JComboBox<>(new String[]{"All Orders", "Active Only (PENDING/PREPARING)"});
        kitchenFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        kitchenFilterCombo.addActionListener(e -> refreshKitchenOrders());
        filterPanel.add(kitchenFilterCombo);

        panel.add(filterPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(kitchenQueueTable), BorderLayout.CENTER);
        panel.add(createKitchenActionBar(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createKitchenActionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bar.setBackground(new Color(241, 245, 249));
        bar.setBorder(new LineBorder(new Color(226, 232, 240)));

        JButton newOrderBtn = new JButton("🍽️ New Order");
        newOrderBtn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        newOrderBtn.setBackground(new Color(217, 119, 6));
        newOrderBtn.setForeground(Color.WHITE);
        newOrderBtn.setFocusPainted(false);
        newOrderBtn.addActionListener(e -> showNewOrderDialog());

        JButton prepBtn = new JButton("🍳 Start Preparing");
        prepBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        prepBtn.setFocusPainted(false);
        prepBtn.addActionListener(e -> updateSelectedOrderStatus(KitchenMealOrder.STATUS_PREPARING));

        JButton deliverBtn = new JButton("🚚 Deliver");
        deliverBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        deliverBtn.setFocusPainted(false);
        deliverBtn.addActionListener(e -> updateSelectedOrderStatus(KitchenMealOrder.STATUS_DELIVERED));

        JButton cancelBtn = new JButton("❌ Cancel");
        cancelBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        cancelBtn.setFocusPainted(false);
        cancelBtn.addActionListener(e -> updateSelectedOrderStatus(KitchenMealOrder.STATUS_CANCELLED));

        JButton refreshKitchenBtn = new JButton("🔄 Refresh");
        refreshKitchenBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        refreshKitchenBtn.setFocusPainted(false);
        refreshKitchenBtn.addActionListener(e -> refreshKitchenOrders());

        bar.add(newOrderBtn);
        bar.add(prepBtn);
        bar.add(deliverBtn);
        bar.add(cancelBtn);
        bar.add(refreshKitchenBtn);
        return bar;
    }

    // ──────────────────────────── Data Refresh Logic ────────────────────────────

    /**
     * Refreshes both billing records and kitchen orders in the UI.
     */
    public void refreshData() {
        refreshBills();
        refreshKitchenOrders();
    }

    public void refreshBills() {
        int selectedRow = billsMasterTable.getSelectedRow();
        Long selectedBillId = null;
        if (selectedRow >= 0 && selectedRow < billsMasterModel.getRowCount()) {
            selectedBillId = (Long) billsMasterModel.getValueAt(selectedRow, 0);
        }

        billsMasterModel.setRowCount(0);
        List<BillingRecord> bills = billingService.getAllBills();
        int reselectIndex = -1;

        for (int i = 0; i < bills.size(); i++) {
            BillingRecord b = bills.get(i);
            String status = b.isPaid() ? "PAID" : "ACTIVE / UNPAID";
            String genTime = (b.getGeneratedAt() != null) ? b.getGeneratedAt().format(TIME_FMT) : "—";

            billsMasterModel.addRow(new Object[]{
                    b.getBillId(),
                    b.getPatientId(),
                    String.format("%.2f", b.getTotalAmount()),
                    status,
                    genTime
            });

            if (selectedBillId != null && b.getBillId() == selectedBillId) {
                reselectIndex = i;
            }
        }

        if (reselectIndex >= 0) {
            billsMasterTable.setRowSelectionInterval(reselectIndex, reselectIndex);
        } else if (billsMasterModel.getRowCount() > 0) {
            billsMasterTable.setRowSelectionInterval(0, 0);
        } else {
            lineItemsDetailModel.setRowCount(0);
            selectedBillSummaryLabel.setText("Itemized Bed & Equipment Line Items (No bills found):");
        }
    }

    private void loadSelectedBillLineItems() {
        int row = billsMasterTable.getSelectedRow();
        if (row < 0) {
            lineItemsDetailModel.setRowCount(0);
            selectedBillSummaryLabel.setText("Itemized Bed & Equipment Line Items (Select a Bill above):");
            return;
        }

        long billId = (Long) billsMasterModel.getValueAt(row, 0);
        long patientId = (Long) billsMasterModel.getValueAt(row, 1);
        String total = (String) billsMasterModel.getValueAt(row, 2);
        String status = (String) billsMasterModel.getValueAt(row, 3);

        selectedBillSummaryLabel.setText(String.format("Itemized Line Items for Bill #%d (Patient #%d) • Total: $%s • Status: %s",
                billId, patientId, total, status));

        lineItemsDetailModel.setRowCount(0);

        List<BillingRecord> allBills = billingService.getAllBills();
        for (BillingRecord b : allBills) {
            if (b.getBillId() == billId) {
                for (BillingLineItem item : b.getLineItems()) {
                    String timeStr = (item.getCreatedAt() != null) ? item.getCreatedAt().format(TIME_FMT) : "—";
                    lineItemsDetailModel.addRow(new Object[]{
                            item.getItemId(),
                            item.getDescription(),
                            String.format("%.2f", item.getAmount()),
                            timeStr
                    });
                }
                break;
            }
        }
    }

    public void refreshKitchenOrders() {
        kitchenQueueModel.setRowCount(0);
        boolean activeOnly = kitchenFilterCombo != null && kitchenFilterCombo.getSelectedIndex() == 1;

        List<KitchenMealOrder> orders = activeOnly
                ? kitchenService.getAllActiveOrders()
                : kitchenService.getAllOrders();

        for (KitchenMealOrder o : orders) {
            String timeStr = (o.getOrderedAt() != null) ? o.getOrderedAt().format(TIME_FMT) : "—";
            kitchenQueueModel.addRow(new Object[]{
                    o.getOrderId(),
                    o.getPatientId(),
                    o.getDietType() != null ? o.getDietType().name() : "REGULAR",
                    o.getStatus(),
                    timeStr
            });
        }
    }

    // ──────────────────────────── User Actions ────────────────────────────

    private void showAddChargeDialog() {
        JPanel dialogPanel = new JPanel(new GridLayout(3, 2, 8, 8));
        JTextField patientIdField = new JTextField();
        JTextField descField = new JTextField();
        JTextField amountField = new JTextField("50.00");

        // Pre-fill patient ID if bill is selected
        int row = billsMasterTable.getSelectedRow();
        if (row >= 0) {
            patientIdField.setText(String.valueOf(billsMasterModel.getValueAt(row, 1)));
        }

        dialogPanel.add(new JLabel("Patient ID:"));
        dialogPanel.add(patientIdField);
        dialogPanel.add(new JLabel("Charge Description:"));
        dialogPanel.add(descField);
        dialogPanel.add(new JLabel("Amount ($):"));
        dialogPanel.add(amountField);

        int res = JOptionPane.showConfirmDialog(this, dialogPanel, "Add Manual Medical / Bed Charge",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            try {
                long patientId = Long.parseLong(patientIdField.getText().trim());
                String desc = descField.getText().trim();
                double amount = Double.parseDouble(amountField.getText().trim());

                if (desc.isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Description cannot be empty.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (amount <= 0.0) {
                    JOptionPane.showMessageDialog(this, "Amount must be greater than $0.00.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }

                billingService.addManualCharge(patientId, desc, amount);
                refreshBills();
                JOptionPane.showMessageDialog(this, String.format("Added charge of $%.2f to Patient #%d's invoice.", amount, patientId),
                        "Charge Added", JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number entered for Patient ID or Amount.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void handleSettleBill() {
        int row = billsMasterTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an invoice from the master table to settle.", "No Bill Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long billId = (Long) billsMasterModel.getValueAt(row, 0);
        String status = (String) billsMasterModel.getValueAt(row, 3);
        String total = (String) billsMasterModel.getValueAt(row, 2);

        if ("PAID".equalsIgnoreCase(status)) {
            JOptionPane.showMessageDialog(this, "Bill #" + billId + " is already marked as PAID.", "Already Settled", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                String.format("Confirm settlement of Bill #%d for $%s?\nThis will mark the invoice PAID and close charges.", billId, total),
                "Confirm Invoice Settlement", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            boolean success = billingService.settleBill(billId, "BILLING_DESK");
            if (success) {
                refreshBills();
                JOptionPane.showMessageDialog(this, "Bill #" + billId + " successfully marked PAID.", "Settlement Complete", JOptionPane.INFORMATION_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(this, "Failed to settle bill #" + billId + ". Please check database.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showNewOrderDialog() {
        JPanel dialogPanel = new JPanel(new GridLayout(2, 2, 8, 8));
        JTextField patientIdField = new JTextField();
        JComboBox<DietType> dietCombo = new JComboBox<>(DietType.values());

        dialogPanel.add(new JLabel("Patient ID:"));
        dialogPanel.add(patientIdField);
        dialogPanel.add(new JLabel("Prescribed Diet:"));
        dialogPanel.add(dietCombo);

        int res = JOptionPane.showConfirmDialog(this, dialogPanel, "Create Dietary Meal Order",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (res == JOptionPane.OK_OPTION) {
            try {
                long patientId = Long.parseLong(patientIdField.getText().trim());
                DietType diet = (DietType) dietCombo.getSelectedItem();
                kitchenService.createMealOrder(patientId, diet, KitchenMealOrder.STATUS_PENDING, "KITCHEN_UI");
                refreshKitchenOrders();
                JOptionPane.showMessageDialog(this, String.format("Meal order created for Patient #%d (Diet: %s).", patientId, diet),
                        "Order Placed", JOptionPane.INFORMATION_MESSAGE);

            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid Patient ID format.", "Input Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void updateSelectedOrderStatus(String newStatus) {
        int row = kitchenQueueTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select an order from the queue.", "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        long orderId = (Long) kitchenQueueModel.getValueAt(row, 0);
        boolean ok = kitchenService.updateOrderStatus(orderId, newStatus, "KITCHEN_STAFF");
        if (ok) {
            refreshKitchenOrders();
        } else {
            JOptionPane.showMessageDialog(this, "Failed to update order #" + orderId, "Update Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ──────────────────────────── Demo Data Seeding ────────────────────────────

    private void initDemoDataIfEmpty() {
        if (billingService.getAllBills().isEmpty()) {
            // Seed sample bills
            BillingRecord b1 = billingService.getActiveBill(5001L).orElseGet(() -> {
                billingService.addManualCharge(5001L, "Emergency ICU Bed Stay [Emergency ICU Ward A — Standard Base Bed + Ventilator] for 4 hour(s) @ $200.00/hr", 800.00);
                billingService.addManualCharge(5001L, "Cardiac Monitor Hookup (4 hrs @ $60.00/hr)", 240.00);
                return billingService.getActiveBill(5001L).orElse(null);
            });

            billingService.addManualCharge(5002L, "General Medicine Bed Stay [General Medicine Ward B — Standard Base Bed] for 24 hour(s) @ $45.00/hr", 1080.00);
            billingService.addManualCharge(5002L, "Dialysis Therapy Session (2 hrs @ $200.00/hr)", 400.00);

            billingService.addManualCharge(5003L, "Cardiology ICU Bed Stay [Cardiology Intensive Care — Standard Base Bed + Oxygen Cylinder] for 8 hour(s) @ $160.00/hr", 1280.00);
            billingService.getActiveBill(5003L).ifPresent(bill -> billingService.settleBill(bill.getBillId(), "DEMO_SEED"));
        }

        if (kitchenService.getAllOrders().isEmpty()) {
            // Seed sample orders
            kitchenService.createMealOrder(5001L, DietType.IV_FLUIDS, KitchenMealOrder.STATUS_PENDING, "DEMO_SEED");
            kitchenService.createMealOrder(5002L, DietType.LIQUID, KitchenMealOrder.STATUS_PREPARING, "DEMO_SEED");
            kitchenService.createMealOrder(5003L, DietType.REGULAR, KitchenMealOrder.STATUS_DELIVERED, "DEMO_SEED");
            kitchenService.createMealOrder(5005L, DietType.IV_FLUIDS, KitchenMealOrder.STATUS_PENDING, "DEMO_SEED");
        }
    }
}
