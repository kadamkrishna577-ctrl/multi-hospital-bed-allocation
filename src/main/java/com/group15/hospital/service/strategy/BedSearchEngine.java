package com.group15.hospital.service.strategy;

import com.group15.hospital.dao.AuditDAO;
import com.group15.hospital.dao.HospitalDAO;
import com.group15.hospital.model.hierarchy.Hospital;
import com.group15.hospital.model.hierarchy.Patient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

// context class that coordinates bed allocation strategy
public class BedSearchEngine {

    private static final Logger LOGGER = Logger.getLogger(BedSearchEngine.class.getName());

    private final HospitalDAO hospitalDAO;
    private final AuditDAO auditDAO;

    // active strategy
    private BedAllocationStrategy activeStrategy;

    public BedSearchEngine(HospitalDAO hospitalDAO, AuditDAO auditDAO) {
        this.hospitalDAO = Objects.requireNonNull(hospitalDAO, "HospitalDAO cannot be null");
        this.auditDAO = Objects.requireNonNull(auditDAO, "AuditDAO cannot be null");
        this.activeStrategy = new ClosestBedStrategy();
    }

    public BedSearchEngine() {
        this(new HospitalDAO(), new AuditDAO());
    }

    // changes active strategy at runtime
    public void setStrategy(BedAllocationStrategy strategy) {
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy cannot be null");
        }
        this.activeStrategy = strategy;
        LOGGER.info("[BedSearchEngine] Switched active strategy to: " + strategy.getStrategyName());
    }

    public BedAllocationStrategy getActiveStrategy() {
        return activeStrategy;
    }

    // runs allocation using current strategy
    public Optional<BedAllocationStrategy.BedAllocationResult> allocateBed(
            Patient patient,
            double patientLat,
            double patientLon
    ) {
        if (patient == null) {
            return Optional.empty();
        }

        List<Hospital> hospitals = hospitalDAO.findAll();
        Optional<BedAllocationStrategy.BedAllocationResult> resultOpt =
                activeStrategy.allocateBed(patient, patientLat, patientLon, hospitals);

        if (resultOpt.isPresent()) {
            BedAllocationStrategy.BedAllocationResult res = resultOpt.get();

            // Log successful allocation to audit trail
            String details = String.format(
                    "Patient '%s' (Triage: %s) matched to Bed #%d at '%s' (%s) using strategy '%s'. %s",
                    patient.getFullName(),
                    patient.getTriageLevel().name(),
                    res.getBedId(),
                    res.getHospital().getName(),
                    res.getWardName(),
                    res.getStrategyUsed(),
                    res.getRationale()
            );

            auditDAO.log("SYSTEM_STRATEGY", "BED_ALLOCATED", String.valueOf(res.getBedId()), details);
        }

        return resultOpt;
    }
}
