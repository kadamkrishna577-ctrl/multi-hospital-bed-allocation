package com.group15.hospital.service.auxiliary;

import com.group15.hospital.dao.AuditDAO;
import com.group15.hospital.dao.BillingDAO;
import com.group15.hospital.model.bed.BedComponent;
import com.group15.hospital.model.billing.BillingLineItem;
import com.group15.hospital.model.billing.BillingRecord;
import com.group15.hospital.model.hierarchy.Patient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * Service managing patient billing, itemized hourly bed charges, and invoice settlement.
 * <p>
 * Implements {@link PatientTransferListener} to automatically charge hourly base rates and
 * attached decorator equipment fees upon bed transfers and patient discharge (SRS F14 &amp; Use Case U7).
 * </p>
 *
 * @author Jatin (Module P3)
 * @see PatientTransferListener
 * @see BillingDAO
 * @see AuditDAO
 */
public class BillingService implements PatientTransferListener {

    private static final Logger LOGGER = Logger.getLogger(BillingService.class.getName());

    private final BillingDAO billingDAO;
    private final AuditDAO auditDAO;

    public BillingService(BillingDAO billingDAO, AuditDAO auditDAO) {
        this.billingDAO = Objects.requireNonNull(billingDAO, "billingDAO must not be null");
        this.auditDAO = Objects.requireNonNull(auditDAO, "auditDAO must not be null");
    }

    public BillingService() {
        this(new BillingDAO(), new AuditDAO());
    }

    // ────────────────────── PatientTransferListener Callbacks ──────────────────────

    @Override
    public void onPatientAdmitted(Patient patient, BedComponent assignedBed) {
        if (patient == null) return;

        long patientId = patient.getPatientId();
        BillingRecord bill = getOrCreateActiveBill(patientId);

        auditDAO.log(
                "BILLING_SERVICE",
                "BILLING_STARTED",
                String.valueOf(patientId),
                String.format("Billing initiated for patient '%s' (ID=%d) on bed #%d [%s]. Bill ID: %d",
                        patient.getFullName(), patientId,
                        (assignedBed != null ? assignedBed.getBedId() : 0L),
                        (assignedBed != null ? assignedBed.getWardName() : "General"),
                        bill.getBillId())
        );

        LOGGER.info(String.format("[BillingService] Admitted patient %d, active bill #%d",
                patientId, bill.getBillId()));
    }

    @Override
    public void onPatientTransferred(Patient patient, BedComponent fromBed, BedComponent toBed, long hoursStayedInFromBed) {
        if (patient == null || fromBed == null) return;

        long patientId = patient.getPatientId();
        BillingRecord bill = getOrCreateActiveBill(patientId);

        long billableHours = Math.max(1L, hoursStayedInFromBed);
        double hourlyRate = fromBed.getHourlyCost();
        double chargeAmount = billableHours * hourlyRate;

        String description = String.format("Bed Stay [%s — %s] for %d hour(s) @ $%.2f/hr",
                fromBed.getWardName(), fromBed.getDescription(), billableHours, hourlyRate);

        BillingLineItem item = billingDAO.addLineItem(bill.getBillId(), description, chargeAmount);

        auditDAO.log(
                "BILLING_SERVICE",
                "BED_TRANSFER_CHARGE",
                String.valueOf(patientId),
                String.format("Added transfer charge $%.2f (%d hrs on bed #%d [%s] -> transferred to bed #%d). Line item #%d",
                        chargeAmount, billableHours, fromBed.getBedId(), fromBed.getWardName(),
                        (toBed != null ? toBed.getBedId() : 0L), item.getItemId())
        );

        LOGGER.info(String.format("[BillingService] Transfer charge $%.2f added to bill #%d for patient %d",
                chargeAmount, bill.getBillId(), patientId));
    }

    @Override
    public void onPatientDischarged(Patient patient, BedComponent releasedBed, long hoursStayedInLastBed) {
        if (patient == null || releasedBed == null) return;

        long patientId = patient.getPatientId();
        BillingRecord bill = getOrCreateActiveBill(patientId);

        long billableHours = Math.max(1L, hoursStayedInLastBed);
        double hourlyRate = releasedBed.getHourlyCost();
        double chargeAmount = billableHours * hourlyRate;

        String description = String.format("Final Bed Stay [%s — %s] for %d hour(s) @ $%.2f/hr",
                releasedBed.getWardName(), releasedBed.getDescription(), billableHours, hourlyRate);

        BillingLineItem item = billingDAO.addLineItem(bill.getBillId(), description, chargeAmount);

        auditDAO.log(
                "BILLING_SERVICE",
                "PATIENT_DISCHARGE_CHARGE",
                String.valueOf(patientId),
                String.format("Added final discharge stay charge $%.2f (%d hrs on bed #%d [%s]). Total bill is now $%.2f",
                        chargeAmount, billableHours, releasedBed.getBedId(), releasedBed.getWardName(),
                        bill.getTotalAmount() + chargeAmount)
        );

        LOGGER.info(String.format("[BillingService] Final discharge charge $%.2f recorded on bill #%d for patient %d",
                chargeAmount, bill.getBillId(), patientId));
    }

    // ──────────────────────────── Service Methods ────────────────────────────

    /**
     * Appends a manual medical or auxiliary charge to a patient's active bill.
     *
     * @param patientId   the patient ID
     * @param description description of the line item
     * @param amount      monetary charge amount
     * @return the created line item
     */
    public BillingLineItem addManualCharge(long patientId, String description, double amount) {
        if (amount <= 0.0) {
            throw new IllegalArgumentException("Manual charge amount must be strictly greater than zero. Provided: " + amount);
        }
        BillingRecord bill = getOrCreateActiveBill(patientId);
        BillingLineItem item = billingDAO.addLineItem(bill.getBillId(), description, amount);

        auditDAO.log(
                "BILLING_SERVICE",
                "MANUAL_CHARGE_ADDED",
                String.valueOf(patientId),
                String.format("Manual charge '%s' ($%.2f) appended to bill #%d", description, amount, bill.getBillId())
        );
        return item;
    }

    /**
     * Retrieves the currently active (unpaid) bill for a patient.
     *
     * @param patientId patient identifier
     * @return optional containing the active bill with itemized line items
     */
    public Optional<BillingRecord> getActiveBill(long patientId) {
        return billingDAO.findActiveBillByPatientId(patientId);
    }

    /**
     * Returns all billing records across the hospital system.
     *
     * @return list of billing records
     */
    public List<BillingRecord> getAllBills() {
        return billingDAO.findAllBills();
    }

    /**
     * Settles and closes a patient's bill.
     *
     * @param billId the bill to settle
     * @param actor  the clerk or administrator processing payment
     * @return {@code true} if successfully marked paid
     */
    public boolean settleBill(long billId, String actor) {
        Optional<BillingRecord> existingOpt = billingDAO.findBillById(billId);
        if (existingOpt.isPresent() && existingOpt.get().isPaid()) {
            LOGGER.warning("[BillingService] Bill #" + billId + " is already marked PAID. Rejecting redundant settlement.");
            return false;
        }

        boolean success = billingDAO.markBillPaid(billId);
        if (success) {
            auditDAO.log(
                    (actor != null && !actor.trim().isEmpty()) ? actor.trim() : "BILLING_ADMIN",
                    "BILL_SETTLED",
                    String.valueOf(billId),
                    "Bill #" + billId + " successfully settled and marked PAID."
            );
        }
        return success;
    }

    // ──────────────────────────── Helper ────────────────────────────

    private BillingRecord getOrCreateActiveBill(long patientId) {
        Optional<BillingRecord> activeOpt = billingDAO.findActiveBillByPatientId(patientId);
        if (activeOpt.isPresent() && !activeOpt.get().isPaid()) {
            return activeOpt.get();
        }
        return billingDAO.createBillingRecord(patientId);
    }
}
