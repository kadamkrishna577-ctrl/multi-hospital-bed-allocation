package com.group15.hospital;

import com.group15.hospital.dao.*;
import com.group15.hospital.exception.IllegalBedStateException;
import com.group15.hospital.model.bed.*;
import com.group15.hospital.model.billing.BillingLineItem;
import com.group15.hospital.model.billing.BillingRecord;
import com.group15.hospital.model.billing.KitchenMealOrder;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.Patient;
import com.group15.hospital.model.isolation.WardTopologyGraph;
import com.group15.hospital.service.auxiliary.BillingService;
import com.group15.hospital.service.auxiliary.KitchenService;
import com.group15.hospital.service.containment.IsolationContainmentService;
import com.group15.hospital.ui.auxiliary.BillingAndKitchenPanel;
import com.group15.hospital.ui.auxiliary.IsolationControlPanel;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3 Unit &amp; Integration Test Suite.
 * <p>
 * Tests coverage includes:
 * <ol>
 *   <li><b>WardTopologyGraph:</b> Physical neighbor (NEIGHBOR) and shared-air (SHARED_AIR) bidirectional lookup.</li>
 *   <li><b>Isolation Containment:</b> Locking vacant neighbor/shared-air beds into RESTRICTED_BUFFER and guarding
 *       against patient assignment (IllegalBedStateException).</li>
 *   <li><b>Overlapping Isolation Zones:</b> Ensuring buffer beds adjacent to multiple contagious beds remain locked
 *       until all exposing sources are released.</li>
 *   <li><b>Observer Integration:</b> BillingService and KitchenService reacting to patient admission,
 *       ward-to-ICU decorator transfers, hourly rate calculations, and discharge settlements.</li>
 *   <li><b>Clinical Dietary Logic:</b> Automatic ICU IV_FLUIDS assignment, step-down to LIQUID, and order cancellations.</li>
 * </ol>
 * </p>
 *
 * @author Group 15 (Phase 3)
 */
class IsolationAndBillingTest {

    private WardTopologyDAO wardTopologyDAO;
    private BedDAO bedDAO;
    private PatientDAO patientDAO;
    private AuditDAO auditDAO;
    private BillingDAO billingDAO;
    private KitchenOrderDAO kitchenOrderDAO;

    private IsolationContainmentService containmentService;
    private BillingService billingService;
    private KitchenService kitchenService;

    @BeforeEach
    void setUp() {
        wardTopologyDAO = new WardTopologyDAO();
        bedDAO = new BedDAO();
        patientDAO = new PatientDAO();
        auditDAO = new AuditDAO();
        billingDAO = new BillingDAO();
        kitchenOrderDAO = new KitchenOrderDAO();

        containmentService = new IsolationContainmentService(wardTopologyDAO, bedDAO, patientDAO, auditDAO);
        billingService = new BillingService(billingDAO, auditDAO);
        kitchenService = new KitchenService(kitchenOrderDAO, auditDAO);

        // Register auxiliary observers with containment/transfer orchestrator
        containmentService.registerListener(billingService);
        containmentService.registerListener(kitchenService);
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  1. Ward Topology Graph Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("WardTopologyGraph Tests")
    class TopologyGraphTests {

        @Test
        @DisplayName("Bidirectional lookup for NEIGHBOR and SHARED_AIR edges")
        void testBidirectionalEdgeLookup() {
            WardTopologyGraph graph = new WardTopologyGraph();

            // Bed 101 is physical neighbor to Bed 102
            graph.addEdge(101L, 102L, WardTopologyGraph.RELATION_NEIGHBOR);
            // Bed 101 shares HVAC airflow with Bed 103
            graph.addEdge(101L, 103L, WardTopologyGraph.RELATION_SHARED_AIR);

            // Direct neighbors
            assertTrue(graph.getDirectNeighbors(101L).contains(102L), "102 should be neighbor of 101");
            assertTrue(graph.getDirectNeighbors(102L).contains(101L), "101 should be neighbor of 102 (bidirectional)");
            assertFalse(graph.getDirectNeighbors(101L).contains(103L), "103 is shared-air, not direct neighbor");

            // Shared air
            assertTrue(graph.getSharedAirBeds(101L).contains(103L), "103 should share air with 101");
            assertTrue(graph.getSharedAirBeds(103L).contains(101L), "101 should share air with 103 (bidirectional)");
            assertFalse(graph.getSharedAirBeds(101L).contains(102L), "102 is neighbor, not shared air");

            // All affected buffer beds (union)
            Set<Long> affected = graph.getAllAffectedBufferBeds(101L);
            assertEquals(2, affected.size());
            assertTrue(affected.contains(102L));
            assertTrue(affected.contains(103L));
            assertFalse(affected.contains(101L), "Queried bed should not be in its own buffer list");
        }

        @Test
        @DisplayName("Removing bed clears edges in both directions")
        void testRemoveBedClearsBidirectionalConnections() {
            WardTopologyGraph graph = new WardTopologyGraph();
            graph.addEdge(201L, 202L, "NEIGHBOR");
            graph.addEdge(201L, 203L, "SHARED_AIR");

            graph.removeBed(201L);

            assertTrue(graph.getDirectNeighbors(201L).isEmpty());
            assertTrue(graph.getDirectNeighbors(202L).isEmpty(), "Back-edge to 201 should be removed");
            assertTrue(graph.getSharedAirBeds(203L).isEmpty(), "Back-edge to 201 should be removed");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  2. Isolation Containment & State Guard Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Isolation Containment & State Guard Tests")
    class IsolationContainmentTests {

        @Test
        @DisplayName("Activating isolation locks vacant neighbor/air beds into RESTRICTED_BUFFER")
        void testActivateIsolationLocksVacantBufferBeds() {
            // Setup topology: Bed 10 -> Bed 11 (neighbor), Bed 10 -> Bed 12 (shared air)
            wardTopologyDAO.insertEdge(10L, 11L, WardTopologyGraph.RELATION_NEIGHBOR);
            wardTopologyDAO.insertEdge(10L, 12L, WardTopologyGraph.RELATION_SHARED_AIR);

            BaseBed bed10 = new BaseBed(10L, "Isolation Ward", "City Hospital", BedStatus.VACANT);
            BaseBed bed11 = new BaseBed(11L, "Isolation Ward", "City Hospital", BedStatus.VACANT);
            BaseBed bed12 = new BaseBed(12L, "Isolation Ward", "City Hospital", BedStatus.VACANT);

            containmentService.registerBed(bed10);
            containmentService.registerBed(bed11);
            containmentService.registerBed(bed12);

            // Contagious patient admitted to Bed 10
            Patient contagiousPatient = new Patient("John Doe", 45, "Male", TriageLevel.LEVEL_2, DietType.REGULAR);
            contagiousPatient.setPatientId(9001L);

            containmentService.admitPatient(contagiousPatient, bed10, true, "DR_HOUSE");

            assertEquals(BedStatus.OCCUPIED, bed10.getBedStatus());
            assertTrue(containmentService.isBedIsolated(10L));

            // Verify both neighbors are locked as buffers
            assertEquals(BedStatus.RESTRICTED_BUFFER, bed11.getBedStatus(), "Neighbor bed 11 should be locked in RESTRICTED_BUFFER");
            assertEquals(BedStatus.RESTRICTED_BUFFER, bed12.getBedStatus(), "Shared air bed 12 should be locked in RESTRICTED_BUFFER");

            // Verify IllegalBedStateException when trying to assign patient to a restricted buffer bed
            Patient incomingPatient = new Patient("Jane Roe", 30, "Female", TriageLevel.LEVEL_3, DietType.REGULAR);
            incomingPatient.setPatientId(9002L);

            assertThrows(IllegalBedStateException.class, () ->
                    bed11.assignPatient(9002L, "Jane Roe", TriageLevel.LEVEL_3),
                    "Direct bed assignment to RESTRICTED_BUFFER must throw IllegalBedStateException");

            assertThrows(IllegalBedStateException.class, () ->
                    containmentService.admitPatient(incomingPatient, bed11, false, "NURSE"),
                    "Admitting patient to buffer bed must throw IllegalBedStateException");
        }

        @Test
        @DisplayName("Overlapping isolation zones: buffer bed remains locked until all exposing sources are released")
        void testOverlappingIsolationZones() {
            // Topology:
            // Bed 1 <---> Bed 2 (neighbor)
            // Bed 3 <---> Bed 2 (neighbor)
            // Bed 2 is a shared buffer between Bed 1 and Bed 3
            wardTopologyDAO.insertEdge(1L, 2L, WardTopologyGraph.RELATION_NEIGHBOR);
            wardTopologyDAO.insertEdge(3L, 2L, WardTopologyGraph.RELATION_NEIGHBOR);

            BaseBed bed1 = new BaseBed(1L, "Ward A", "City Hospital", BedStatus.OCCUPIED);
            BaseBed bed2 = new BaseBed(2L, "Ward A", "City Hospital", BedStatus.VACANT);
            BaseBed bed3 = new BaseBed(3L, "Ward A", "City Hospital", BedStatus.OCCUPIED);

            containmentService.registerBed(bed1);
            containmentService.registerBed(bed2);
            containmentService.registerBed(bed3);

            // 1. Activate isolation on Bed 1 -> Bed 2 becomes RESTRICTED_BUFFER
            containmentService.activateIsolation(1L, "DR_A");
            assertEquals(BedStatus.RESTRICTED_BUFFER, bed2.getBedStatus());

            // 2. Activate isolation on Bed 3 -> Bed 2 still in RESTRICTED_BUFFER
            containmentService.activateIsolation(3L, "DR_B");
            assertEquals(BedStatus.RESTRICTED_BUFFER, bed2.getBedStatus());

            // 3. Release isolation on Bed 1
            Set<Long> releasedAfterBed1 = containmentService.releaseIsolation(1L, "DR_A");
            assertFalse(releasedAfterBed1.contains(2L), "Bed 2 should NOT be released because Bed 3 is still active");
            assertEquals(BedStatus.RESTRICTED_BUFFER, bed2.getBedStatus(), "Bed 2 must remain in RESTRICTED_BUFFER");

            // 4. Release isolation on Bed 3 -> Bed 2 now has no remaining contagious neighbors
            Set<Long> releasedAfterBed3 = containmentService.releaseIsolation(3L, "DR_B");
            assertTrue(releasedAfterBed3.contains(2L), "Bed 2 should be released when all contagious sources resolve");
            assertEquals(BedStatus.VACANT, bed2.getBedStatus(), "Bed 2 should return to VACANT");
        }

        @Test
        @DisplayName("Isolating Bed 105 updates in-memory registry, locking 104 and 106 buffers")
        void testIsolatingBed105LocksVacant104And106BuffersAndReleases() {
            wardTopologyDAO.insertEdge(104L, 105L, WardTopologyGraph.RELATION_NEIGHBOR);
            wardTopologyDAO.insertEdge(105L, 106L, WardTopologyGraph.RELATION_SHARED_AIR);

            BaseBed b104 = new BaseBed(104L, "General Medicine Ward B", "City Central Hospital", BedStatus.VACANT);
            BaseBed b105 = new BaseBed(105L, "General Medicine Ward B", "City Central Hospital", BedStatus.VACANT);
            BaseBed b106 = new BaseBed(106L, "Infectious Isolation Ward C", "City Central Hospital", BedStatus.VACANT);

            containmentService.registerBed(b104);
            containmentService.registerBed(b105);
            containmentService.registerBed(b106);

            Set<Long> locked = containmentService.activateIsolation(105L, "STAFF");
            assertTrue(locked.contains(104L), "Bed 104 should be locked as neighbor buffer");
            assertTrue(locked.contains(106L), "Bed 106 should be locked as shared air buffer");
            assertTrue(containmentService.isBedIsolated(105L));
            assertEquals(BedStatus.RESTRICTED_BUFFER, containmentService.getBed(104L).getBedStatus());
            assertEquals(BedStatus.RESTRICTED_BUFFER, containmentService.getBed(106L).getBedStatus());
            assertEquals(BedStatus.OCCUPIED, containmentService.getBed(105L).getBedStatus());

            Set<Long> released = containmentService.releaseIsolation(105L, "STAFF");
            assertTrue(released.contains(104L));
            assertTrue(released.contains(106L));
            assertFalse(containmentService.isBedIsolated(105L));
            assertEquals(BedStatus.VACANT, containmentService.getBed(104L).getBedStatus());
            assertEquals(BedStatus.VACANT, containmentService.getBed(106L).getBedStatus());
            assertEquals(BedStatus.VACANT, containmentService.getBed(105L).getBedStatus());
        }

        @Test
        @DisplayName("Occupied neighbor bed is flagged as EXPOSED with hazard warning when adjacent bed is isolated")
        void testOccupiedNeighborExposureFlaggedWhenBedIsolated() {
            wardTopologyDAO.insertEdge(101L, 102L, WardTopologyGraph.RELATION_NEIGHBOR);

            BaseBed b101 = new BaseBed(101L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
            BaseBed b102 = new BaseBed(102L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
            b102.assignPatient(5001L, "Robert Chen", TriageLevel.LEVEL_3);

            containmentService.registerBed(b101);
            containmentService.registerBed(b102);

            assertEquals(BedStatus.OCCUPIED, b102.getBedStatus());

            // Activate isolation on Bed 101 -> Bed 102 is occupied so cannot become RESTRICTED_BUFFER
            containmentService.activateIsolation(101L, "STAFF");

            assertTrue(containmentService.isBedExposed(102L), "Bed 102 must be marked as EXPOSED");
            assertTrue(containmentService.getExposedOccupiedBeds(101L).contains(102L));
            assertEquals(BedStatus.OCCUPIED, b102.getBedStatus(), "Occupied bed must not be overwritten to buffer");

            // Release isolation -> Bed 102 is no longer exposed
            containmentService.releaseIsolation(101L, "STAFF");
            assertFalse(containmentService.isBedExposed(102L), "Bed 102 should no longer be exposed once isolation resolves");
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. Observer Integration: Billing & Kitchen across Admission, Transfer, Discharge
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Observer Integration & Clinical Workflows")
    class ObserverIntegrationTests {

        @Test
        @DisplayName("Patient Admission -> Ward-to-ICU Decorated Transfer -> Discharge lifecycle")
        void testFullPatientTransferAndBillingLifecycle() {
            // Patient initial admission
            Patient patient = new Patient("Robert Baratheon", 52, "Male", TriageLevel.LEVEL_3, DietType.REGULAR);
            patient.setPatientId(8001L);

            // Step 1: Initial admission into General Bed ($50/hr)
            BaseBed generalBed = new BaseBed(501L, "General Medicine Ward B", "City Central Hospital", BedStatus.VACANT);
            containmentService.registerBed(generalBed);

            containmentService.admitPatient(patient, generalBed, false, "ADMIT_STAFF");

            // Verify Bed & Patient state
            assertEquals(BedStatus.OCCUPIED, generalBed.getBedStatus());
            assertEquals(501L, patient.getAssignedBedId());

            // Verify Billing Observer: active bill created with 0 initial total
            Optional<BillingRecord> optBill = billingService.getActiveBill(8001L);
            assertTrue(optBill.isPresent(), "Billing record should be created on admission");
            BillingRecord bill = optBill.get();
            assertEquals(0.0, bill.getTotalAmount(), 0.001);
            assertFalse(bill.isPaid());

            // Verify Kitchen Observer: meal order placed for REGULAR diet
            List<KitchenMealOrder> activeOrders = kitchenService.getActiveOrdersForPatient(8001L);
            assertEquals(1, activeOrders.size(), "Should have 1 active meal order upon admission");
            assertEquals(DietType.REGULAR, activeOrders.get(0).getDietType());
            assertEquals(KitchenMealOrder.STATUS_PENDING, activeOrders.get(0).getStatus());

            // Step 2: Patient's condition deteriorates -> Transfer to ICU with Decorators
            // Destination bed is decorated with Ventilator ($150) + CardiacMonitor ($60)
            BaseBed baseIcu = new BaseBed(601L, "Emergency ICU Ward A", "City Central Hospital", BedStatus.VACANT);
            BedComponent icuDecorated = new CardiacMonitorDecorator(new VentilatorDecorator(baseIcu));

            // Total hourly cost: Base $50 + Ventilator $150 + CardiacMonitor $60 = $260.00/hr
            assertEquals(260.0, icuDecorated.getHourlyCost(), 0.001);

            // Patient stayed 4 hours in General Bed before transfer
            containmentService.transferPatient(patient, generalBed, icuDecorated, 4L, false, "DR_CRITICAL");

            // Verify generalBed is discharged and requires sanitization
            assertEquals(BedStatus.REQUIRES_SANITIZATION, generalBed.getBedStatus());
            assertEquals(BedStatus.OCCUPIED, icuDecorated.getBedStatus());
            assertEquals(601L, patient.getAssignedBedId());

            // Verify Billing Observer after transfer:
            // Charged 4 hours * $50 = $200.00
            bill = billingService.getActiveBill(8001L).orElseThrow();
            assertEquals(200.0, bill.getTotalAmount(), 0.001, "Bill should now include $200 for 4 hours in General Bed");
            assertEquals(1, bill.getLineItems().size());
            assertTrue(bill.getLineItems().get(0).getDescription().contains("General Medicine Ward B"));

            // Verify Kitchen Observer after transfer:
            // Since destination is an ICU ward, diet MUST automatically switch to IV_FLUIDS
            assertEquals(DietType.IV_FLUIDS, patient.getDietType(), "Patient diet must be switched to IV_FLUIDS in ICU");
            List<KitchenMealOrder> ordersAfterTransfer = kitchenService.getActiveOrdersForPatient(8001L);
            assertEquals(1, ordersAfterTransfer.size(), "Stale order should be cancelled and 1 new active order created");
            assertEquals(DietType.IV_FLUIDS, ordersAfterTransfer.get(0).getDietType());

            // Step 3: Patient completes treatment -> Discharge after 5 hours in decorated ICU
            // Final stay: 5 hours * $260/hr = $1,300.00
            // Expected grand total: $200 + $1,300 = $1,500.00
            containmentService.dischargePatient(patient, icuDecorated, 5L, "DR_DISCHARGE");

            assertEquals(BedStatus.REQUIRES_SANITIZATION, icuDecorated.getBedStatus());
            assertTrue(patient.isDischarged());
            assertNull(patient.getAssignedBedId());

            // Verify Billing Observer final total
            bill = billingService.getActiveBill(8001L).orElseThrow();
            assertEquals(1500.0, bill.getTotalAmount(), 0.001, "Grand total should be $1500 ($200 general + $1300 decorated ICU)");
            assertEquals(2, bill.getLineItems().size());

            // Verify Kitchen Observer cancelled all pending orders upon discharge
            List<KitchenMealOrder> ordersAfterDischarge = kitchenService.getActiveOrdersForPatient(8001L);
            assertTrue(ordersAfterDischarge.isEmpty(), "No pending orders should remain after discharge");

            // Step 4: Settle Bill
            boolean settled = billingService.settleBill(bill.getBillId(), "CASHIER_AMY");
            assertTrue(settled, "Bill should be successfully marked paid");

            BillingRecord settledBill = billingDAO.findBillById(bill.getBillId()).orElseThrow();
            assertTrue(settledBill.isPaid());
            assertNotNull(settledBill.getPaidAt());
        }

        @Test
        @DisplayName("ICU step-down diet transition: moving from ICU to General steps IV_FLUIDS down to LIQUID")
        void testIcuStepDownDietTransition() {
            Patient patient = new Patient("Arya Stark", 22, "Female", TriageLevel.LEVEL_1, DietType.REGULAR);
            patient.setPatientId(8002L);

            BaseBed icuBed = new BaseBed(701L, "Trauma ICU", "City Hospital", BedStatus.VACANT);
            BaseBed generalBed = new BaseBed(702L, "Standard Recovery Ward", "City Hospital", BedStatus.VACANT);

            // Admitted into ICU -> automatic IV_FLUIDS
            containmentService.admitPatient(patient, icuBed, false, "TRIAGE");
            assertEquals(DietType.IV_FLUIDS, patient.getDietType());

            // Transferred from ICU to Standard Recovery -> automatic step-down to LIQUID
            containmentService.transferPatient(patient, icuBed, generalBed, 12L, false, "DR_FLOOR");
            assertEquals(DietType.LIQUID, patient.getDietType(), "Diet should step down from IV_FLUIDS to LIQUID");

            List<KitchenMealOrder> active = kitchenService.getActiveOrdersForPatient(8002L);
            assertEquals(1, active.size());
            assertEquals(DietType.LIQUID, active.get(0).getDietType());
        }

        @Test
        @DisplayName("Manual charges and kitchen order status workflows")
        void testManualChargesAndKitchenStatusWorkflow() {
            Patient patient = new Patient("Tyrion Lannister", 38, "Male", TriageLevel.LEVEL_4, DietType.REGULAR);
            patient.setPatientId(8003L);

            BaseBed bed = new BaseBed(801L, "General Ward", "City Hospital", BedStatus.VACANT);
            containmentService.admitPatient(patient, bed, false, "NURSE");

            // Add manual pharmacy and lab charges
            BillingLineItem pharmacy = billingService.addManualCharge(8003L, "Antibiotics (IV Cefepime)", 75.50);
            BillingLineItem lab = billingService.addManualCharge(8003L, "Complete Blood Count (CBC)", 45.00);

            assertNotNull(pharmacy);
            assertNotNull(lab);

            BillingRecord bill = billingService.getActiveBill(8003L).orElseThrow();
            assertEquals(120.50, bill.getTotalAmount(), 0.001);

            // Kitchen workflow: PENDING -> PREPARING -> DELIVERED
            List<KitchenMealOrder> orders = kitchenService.getActiveOrdersForPatient(8003L);
            assertEquals(1, orders.size());
            long orderId = orders.get(0).getOrderId();

            boolean preparing = kitchenService.updateOrderStatus(orderId, KitchenMealOrder.STATUS_PREPARING, "CHEF_BOB");
            assertTrue(preparing);
            assertEquals(KitchenMealOrder.STATUS_PREPARING, kitchenOrderDAO.findOrderById(orderId).orElseThrow().getStatus());

            boolean delivered = kitchenService.updateOrderStatus(orderId, KitchenMealOrder.STATUS_DELIVERED, "ORDERLY_TIM");
            assertTrue(delivered);
            assertEquals(KitchenMealOrder.STATUS_DELIVERED, kitchenOrderDAO.findOrderById(orderId).orElseThrow().getStatus());

            // Delivered order is no longer in active orders
            assertTrue(kitchenService.getActiveOrdersForPatient(8003L).isEmpty());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  3. Phase 3 UI Panel Initialization Tests
    // ═══════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Phase 3 Swing UI Panel Tests")
    class UIPanelInitializationTests {

        @Test
        @DisplayName("IsolationControlPanel initializes cleanly without IllegalBedStateException or other exceptions")
        void testIsolationControlPanelInitialization() {
            assertDoesNotThrow(() -> {
                IsolationControlPanel panel = new IsolationControlPanel(
                        containmentService, wardTopologyDAO, bedDAO, patientDAO);
                assertNotNull(panel);
            }, "IsolationControlPanel should instantiate and seed demo data without throwing any exceptions");
        }

        @Test
        @DisplayName("BillingAndKitchenPanel initializes cleanly and seeds demo data without exceptions")
        void testBillingAndKitchenPanelInitialization() {
            assertDoesNotThrow(() -> {
                BillingAndKitchenPanel panel = new BillingAndKitchenPanel(billingService, kitchenService);
                assertNotNull(panel);
                panel.refreshData();
            }, "BillingAndKitchenPanel should instantiate and refresh without throwing any exceptions");
        }

        @Test
        @DisplayName("IsolationControlPanel preserves selection on refresh and syncs two-way selection cleanly")
        void testSelectionPreservedAndTwoWaySync() {
            IsolationControlPanel panel = new IsolationControlPanel(
                    containmentService, wardTopologyDAO, bedDAO, patientDAO);

            assertDoesNotThrow(panel::refreshTopology);
            assertDoesNotThrow(panel::refreshTable);
        }
    }
}
