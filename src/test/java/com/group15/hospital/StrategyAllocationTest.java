package com.group15.hospital;

import com.group15.hospital.dao.AuditDAO;
import com.group15.hospital.dao.HospitalDAO;
import com.group15.hospital.model.enums.DietType;
import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.Hospital;
import com.group15.hospital.model.hierarchy.Patient;
import com.group15.hospital.service.strategy.BedAllocationStrategy;
import com.group15.hospital.service.strategy.BedSearchEngine;
import com.group15.hospital.service.strategy.BestEquippedBedStrategy;
import com.group15.hospital.service.strategy.ClosestBedStrategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

// unit tests for bed allocation strategies
public class StrategyAllocationTest {

    private Hospital centralHospital;
    private Hospital memorialHospital;
    private Hospital metroHospital;
    private List<Hospital> hospitalList;

    @BeforeEach
    public void setUp() {
        // Sample test hospitals at distinct geographic coordinates
        centralHospital = new Hospital(1, "Central Hospital", "Central Ave", 12.9716, 77.5946, "111", 50);
        memorialHospital = new Hospital(2, "Memorial Hospital", "South Ave", 12.9352, 77.6245, "222", 150);
        metroHospital = new Hospital(3, "Metro Hospital", "North Ave", 13.0358, 77.5970, "333", 80);

        hospitalList = new ArrayList<>();
        hospitalList.add(centralHospital);
        hospitalList.add(memorialHospital);
        hospitalList.add(metroHospital);
    }

    @Test
    @DisplayName("Haversine Distance: Correctly computes zero distance for same coordinates")
    public void testDistanceCalculationIdenticalCoordinates() {
        double dist = centralHospital.calculateDistanceTo(12.9716, 77.5946);
        assertEquals(0.0, dist, 0.01, "Distance to self should be 0 km");
    }

    @Test
    @DisplayName("ClosestBedStrategy: Selects the nearest hospital to patient GPS coordinates")
    public void testClosestBedStrategySelection() {
        ClosestBedStrategy strategy = new ClosestBedStrategy();
        Patient patient = new Patient("Patient A", 40, "Male", TriageLevel.LEVEL_3, DietType.REGULAR);

        // Coordinates very close to Central Hospital (12.9716, 77.5946)
        double patientLat = 12.9720;
        double patientLon = 77.5950;

        Optional<BedAllocationStrategy.BedAllocationResult> resultOpt =
                strategy.allocateBed(patient, patientLat, patientLon, hospitalList);

        assertTrue(resultOpt.isPresent(), "Allocation result should be present");
        BedAllocationStrategy.BedAllocationResult result = resultOpt.get();

        assertEquals(centralHospital.getHospitalId(), result.getHospital().getHospitalId(),
                "Closest strategy should pick Central Hospital");
        assertTrue(result.getDistanceKm() < 1.0, "Patient should be under 1 km away from Central Hospital");
    }

    @Test
    @DisplayName("BestEquippedBedStrategy: Prioritizes hospital with highest capacity for Critical Level 1 patients")
    public void testBestEquippedStrategyForCriticalPatient() {
        BestEquippedBedStrategy strategy = new BestEquippedBedStrategy();
        Patient criticalPatient = new Patient("Critical Patient", 65, "Female", TriageLevel.LEVEL_1, DietType.IV_FLUIDS);

        // Incident located halfway between Central (50 beds) and Memorial (150 beds)
        double patientLat = 12.9500;
        double patientLon = 77.6100;

        Optional<BedAllocationStrategy.BedAllocationResult> resultOpt =
                strategy.allocateBed(criticalPatient, patientLat, patientLon, hospitalList);

        assertTrue(resultOpt.isPresent(), "Allocation result should be present");
        BedAllocationStrategy.BedAllocationResult result = resultOpt.get();

        assertEquals(memorialHospital.getHospitalId(), result.getHospital().getHospitalId(),
                "BestEquipped strategy should select Memorial Hospital with 150 beds for Level 1 critical patient");
        assertTrue(result.getWardName().contains("ICU"), "Ward assigned should be an Intensive Care Unit");
    }

    @Test
    @DisplayName("BedSearchEngine: Supports dynamic runtime strategy swapping")
    public void testDynamicStrategySwitching() {
        BedSearchEngine engine = new BedSearchEngine();

        // 1. Initially defaults to ClosestBedStrategy
        assertEquals(ClosestBedStrategy.STRATEGY_NAME, engine.getActiveStrategy().getStrategyName());

        // 2. Switch to BestEquippedBedStrategy
        engine.setStrategy(new BestEquippedBedStrategy());
        assertEquals(BestEquippedBedStrategy.STRATEGY_NAME, engine.getActiveStrategy().getStrategyName());

        // 3. Switch back to ClosestBedStrategy
        engine.setStrategy(new ClosestBedStrategy());
        assertEquals(ClosestBedStrategy.STRATEGY_NAME, engine.getActiveStrategy().getStrategyName());
    }

    @Test
    @DisplayName("Edge Case: Gracefully handles empty hospital candidate list without throwing exception")
    public void testEmptyHospitalListHandling() {
        ClosestBedStrategy strategy = new ClosestBedStrategy();
        Patient patient = new Patient("John Doe", 30, "Male", TriageLevel.LEVEL_4, DietType.REGULAR);

        Optional<BedAllocationStrategy.BedAllocationResult> result =
                strategy.allocateBed(patient, 12.9716, 77.5946, new ArrayList<>());

        assertFalse(result.isPresent(), "Result should be empty when no hospitals are available");
    }

    @Test
    @DisplayName("Patient Lifecycle: Bed assignment and discharge lifecycle")
    public void testPatientLifecycle() {
        Patient p = new Patient("Test Patient", 28, "Female", TriageLevel.LEVEL_2, DietType.LIQUID);
        assertFalse(p.isAdmitted(), "Patient should initially not be admitted");

        p.assignBed(205L);
        assertTrue(p.isAdmitted(), "Patient should be admitted after bed assignment");
        assertEquals(205L, p.getAssignedBedId());

        p.discharge();
        assertTrue(p.isDischarged(), "Patient should be discharged");
        assertFalse(p.isAdmitted(), "Patient should not be admitted after discharge");
        assertNull(p.getAssignedBedId(), "Assigned bed should be cleared on discharge");
        assertNotNull(p.getDischargeTime(), "Discharge time should be stamped");
    }
}
