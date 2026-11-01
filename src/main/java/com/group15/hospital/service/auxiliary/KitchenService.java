package com.group15.hospital.service.auxiliary;

import com.group15.hospital.dao.AuditDAO;
import com.group15.hospital.dao.KitchenOrderDAO;
import com.group15.hospital.model.bed.BedComponent;
import com.group15.hospital.model.billing.KitchenMealOrder;
import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.Patient;

import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Service managing dietary orders, kitchen fulfillment workflows, and automatic diet adjustments.
 * <p>
 * Implements {@link PatientTransferListener} to dynamically enforce clinical dietary rules (SRS F11 &amp; Use Case U3):
 * <ul>
 *   <li>Patients admitted or transferred to an <b>ICU/Critical ward</b>, or presenting with <b>LEVEL_1 triage</b>,
 *       automatically receive {@link DietType#IV_FLUIDS}.</li>
 *   <li>Patients transferred out of an ICU ward to a standard ward have their diet stepped down
 *       from {@link DietType#IV_FLUIDS} to {@link DietType#LIQUID}.</li>
 *   <li>Upon bed transfers or discharge, obsolete pending kitchen orders are automatically cancelled.</li>
 * </ul>
 * </p>
 *
 * @author Jatin (Module P3)
 * @see PatientTransferListener
 * @see KitchenOrderDAO
 * @see DietType
 * @see AuditDAO
 */
public class KitchenService implements PatientTransferListener {

    private static final Logger LOGGER = Logger.getLogger(KitchenService.class.getName());

    private final KitchenOrderDAO kitchenOrderDAO;
    private final AuditDAO auditDAO;

    public KitchenService(KitchenOrderDAO kitchenOrderDAO, AuditDAO auditDAO) {
        this.kitchenOrderDAO = Objects.requireNonNull(kitchenOrderDAO, "kitchenOrderDAO must not be null");
        this.auditDAO = Objects.requireNonNull(auditDAO, "auditDAO must not be null");
    }

    public KitchenService() {
        this(new KitchenOrderDAO(), new AuditDAO());
    }

    // ────────────────────── PatientTransferListener Callbacks ──────────────────────

    @Override
    public void onPatientAdmitted(Patient patient, BedComponent assignedBed) {
        if (patient == null) return;

        long patientId = patient.getPatientId();

        // Clinical rule: Override to IV_FLUIDS if ICU or Level 1
        DietType targetDiet;
        if (isIcuOrCritical(assignedBed, patient)) {
            targetDiet = DietType.IV_FLUIDS;
        } else {
            targetDiet = (patient.getDietType() != null) ? patient.getDietType() : DietType.REGULAR;
        }

        patient.setDietType(targetDiet);
        KitchenMealOrder order = kitchenOrderDAO.createOrder(patientId, targetDiet, KitchenMealOrder.STATUS_PENDING);

        auditDAO.log(
                "KITCHEN_SERVICE",
                "MEAL_ORDER_CREATED",
                String.valueOf(patientId),
                String.format("Created pending meal order #%d for patient '%s' (Diet: %s) on bed #%d [%s]",
                        order.getOrderId(), patient.getFullName(), targetDiet,
                        (assignedBed != null ? assignedBed.getBedId() : 0L),
                        (assignedBed != null ? assignedBed.getWardName() : "General"))
        );

        LOGGER.info(String.format("[KitchenService] Admitted patient %d, created order #%d (%s)",
                patientId, order.getOrderId(), targetDiet));
    }

    @Override
    public void onPatientTransferred(Patient patient, BedComponent fromBed, BedComponent toBed, long hoursStayedInFromBed) {
        if (patient == null) return;

        long patientId = patient.getPatientId();

        // 1. Cancel previous pending meal orders
        int cancelled = kitchenOrderDAO.cancelPendingOrdersForPatient(patientId);

        // 2. Determine new diet requirement based on transfer route
        DietType newDiet = patient.getDietType();
        boolean toIcu = isIcuWard(toBed);
        boolean fromIcu = isIcuWard(fromBed);

        if (toIcu) {
            // Patient moving to ICU/Critical care -> must receive IV_FLUIDS
            newDiet = DietType.IV_FLUIDS;
        } else if (fromIcu && patient.getDietType() == DietType.IV_FLUIDS) {
            // Patient recovering and stepping down from ICU -> step down to LIQUID diet
            newDiet = DietType.LIQUID;
        } else if (newDiet == null) {
            newDiet = DietType.REGULAR;
        }

        patient.setDietType(newDiet);

        // 3. Place updated pending meal order
        KitchenMealOrder order = kitchenOrderDAO.createOrder(patientId, newDiet, KitchenMealOrder.STATUS_PENDING);

        auditDAO.log(
                "KITCHEN_SERVICE",
                "TRANSFER_DIET_UPDATE",
                String.valueOf(patientId),
                String.format("Transfer update: cancelled %d stale order(s). Created meal order #%d (Diet: %s) for bed transfer #%d -> #%d",
                        cancelled, order.getOrderId(), newDiet,
                        (fromBed != null ? fromBed.getBedId() : 0L),
                        (toBed != null ? toBed.getBedId() : 0L))
        );

        LOGGER.info(String.format("[KitchenService] Transferred patient %d, diet=%s, new order #%d",
                patientId, newDiet, order.getOrderId()));
    }

    @Override
    public void onPatientDischarged(Patient patient, BedComponent releasedBed, long hoursStayedInLastBed) {
        if (patient == null) return;

        long patientId = patient.getPatientId();
        int cancelled = kitchenOrderDAO.cancelPendingOrdersForPatient(patientId);

        auditDAO.log(
                "KITCHEN_SERVICE",
                "DISCHARGE_ORDERS_CANCELLED",
                String.valueOf(patientId),
                String.format("Cancelled %d pending kitchen order(s) for discharged patient '%s' (ID=%d)",
                        cancelled, patient.getFullName(), patientId)
        );

        LOGGER.info(String.format("[KitchenService] Discharged patient %d, cancelled %d orders",
                patientId, cancelled));
    }

    // ──────────────────────────── Service Methods ────────────────────────────

    /**
     * Retrieves all active (PENDING or PREPARING) meal orders for a patient.
     *
     * @param patientId patient identifier
     * @return list of active kitchen orders
     */
    public List<KitchenMealOrder> getActiveOrdersForPatient(long patientId) {
        return kitchenOrderDAO.findActiveOrdersByPatientId(patientId);
    }

    /**
     * Returns all active kitchen orders across all hospital wards.
     *
     * @return list of active orders
     */
    public List<KitchenMealOrder> getAllActiveOrders() {
        return kitchenOrderDAO.findAllActiveOrders();
    }

    /**
     * Returns all kitchen orders across all hospital wards (active, delivered, cancelled).
     *
     * @return list of all orders
     */
    public List<KitchenMealOrder> getAllOrders() {
        return kitchenOrderDAO.findAllOrders();
    }

    /**
     * Creates and records a manual meal order.
     *
     * @param patientId the patient ID
     * @param dietType  prescribed diet
     * @param status    order status (defaults to PENDING)
     * @param actor     staff member placing the order
     * @return the created kitchen meal order
     */
    public KitchenMealOrder createMealOrder(long patientId, DietType dietType, String status, String actor) {
        String cleanStatus = (status != null && !status.trim().isEmpty()) ? status.trim().toUpperCase() : KitchenMealOrder.STATUS_PENDING;
        DietType diet = (dietType != null) ? dietType : DietType.REGULAR;
        KitchenMealOrder order = kitchenOrderDAO.createOrder(patientId, diet, cleanStatus);

        auditDAO.log(
                (actor != null && !actor.trim().isEmpty()) ? actor.trim() : "KITCHEN_STAFF",
                "MANUAL_MEAL_ORDER",
                String.valueOf(patientId),
                String.format("Manual order #%d (Diet: %s, Status: %s) placed for patient ID=%d",
                        order.getOrderId(), diet, cleanStatus, patientId)
        );
        return order;
    }

    /**
     * Updates an order's fulfillment status (e.g. PREPARING -> DELIVERED).
     *
     * @param orderId   the order ID
     * @param newStatus new status string
     * @param actor     kitchen staff or nurse updating the order
     * @return {@code true} if updated
     */
    public boolean updateOrderStatus(long orderId, String newStatus, String actor) {
        boolean success = kitchenOrderDAO.updateOrderStatus(orderId, newStatus);
        if (success) {
            auditDAO.log(
                    (actor != null && !actor.trim().isEmpty()) ? actor.trim() : "KITCHEN_STAFF",
                    "MEAL_STATUS_UPDATE",
                    String.valueOf(orderId),
                    String.format("Order #%d transitioned to %s", orderId, newStatus)
            );
        }
        return success;
    }

    // ──────────────────────────── Clinical Rule Helpers ────────────────────────────

    private boolean isIcuOrCritical(BedComponent bed, Patient patient) {
        if (patient != null && patient.getTriageLevel() == TriageLevel.LEVEL_1) {
            return true;
        }
        return isIcuWard(bed);
    }

    private boolean isIcuWard(BedComponent bed) {
        if (bed == null || bed.getWardName() == null) {
            return false;
        }
        String ward = bed.getWardName().trim().toUpperCase();
        return ward.contains("ICU") || ward.contains("CRITICAL");
    }
}
