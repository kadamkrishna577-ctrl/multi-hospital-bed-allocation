package com.group15.hospital.service.strategy;

import com.group15.hospital.model.hierarchy.Hospital;
import com.group15.hospital.model.hierarchy.Patient;

import java.util.List;
import java.util.Optional;

// strategy to find the closest hospital bed based on coordinates
public class ClosestBedStrategy implements BedAllocationStrategy {

    public static final String STRATEGY_NAME = "Closest Bed (Proximity Priority)";

    @Override
    public String getStrategyName() {
        return STRATEGY_NAME;
    }

    @Override
    public Optional<BedAllocationResult> allocateBed(
            Patient patient,
            double patientLat,
            double patientLon,
            List<Hospital> hospitals
    ) {
        if (hospitals == null || hospitals.isEmpty()) {
            return Optional.empty();
        }

        if (Double.isNaN(patientLat) || Double.isNaN(patientLon)) {
            return Optional.empty();
        }

        Hospital closestHospital = null;
        double minDistanceKm = Double.MAX_VALUE;

        // find closest hospital with available beds
        for (Hospital hospital : hospitals) {
            if (hospital == null || hospital.getTotalBeds() <= 0) {
                continue;
            }

            double dist = hospital.calculateDistanceTo(patientLat, patientLon);
            if (Double.isNaN(dist) || Double.isInfinite(dist)) {
                continue;
            }

            if (dist < minDistanceKm) {
                minDistanceKm = dist;
                closestHospital = hospital;
            }
        }

        if (closestHospital == null) {
            return Optional.empty();
        }

        // assign to icu if patient is critical
        String wardName = "General Ward";
        if (patient != null && patient.isCritical()) {
            wardName = "Emergency ICU";
        }

        long simulatedBedId = (closestHospital.getHospitalId() * 100L) + 1L;

        String reason = String.format(
                "Hospital '%s' is closest (%.2f km away) with %d total capacity.",
                closestHospital.getName(), minDistanceKm, closestHospital.getTotalBeds()
        );

        BedAllocationResult result = new BedAllocationResult(
                closestHospital,
                simulatedBedId,
                wardName,
                minDistanceKm,
                getStrategyName(),
                reason
        );

        return Optional.of(result);
    }
}
