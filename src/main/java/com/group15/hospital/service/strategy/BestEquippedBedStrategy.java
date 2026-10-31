package com.group15.hospital.service.strategy;

import com.group15.hospital.model.enums.TriageLevel;
import com.group15.hospital.model.hierarchy.Hospital;
import com.group15.hospital.model.hierarchy.Patient;

import java.util.List;
import java.util.Optional;

// strategy that prioritizes well equipped hospitals for critical patients
public class BestEquippedBedStrategy implements BedAllocationStrategy {

    public static final String STRATEGY_NAME = "Best-Equipped Bed (Acuity & Equipment Priority)";

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

        Hospital bestHospital = null;
        double bestScore = -Double.MAX_VALUE;
        double chosenDistanceKm = 0.0;

        boolean isHighAcuity = (patient != null) &&
                (patient.getTriageLevel() == TriageLevel.LEVEL_1 || patient.getTriageLevel() == TriageLevel.LEVEL_2);

        for (Hospital hospital : hospitals) {
            if (hospital == null || hospital.getTotalBeds() <= 0) {
                continue;
            }

            double dist = hospital.calculateDistanceTo(patientLat, patientLon);
            if (Double.isNaN(dist) || Double.isInfinite(dist)) {
                continue;
            }

            // score based on bed capacity and distance
            double clinicalCapacityWeight = isHighAcuity ? 2.5 : 1.0;
            double distancePenaltyWeight = isHighAcuity ? 1.0 : 3.0;

            double score = (hospital.getTotalBeds() * clinicalCapacityWeight) - (dist * distancePenaltyWeight);

            if (score > bestScore) {
                bestScore = score;
                bestHospital = hospital;
                chosenDistanceKm = dist;
            }
        }

        if (bestHospital == null) {
            return Optional.empty();
        }

        String wardName = isHighAcuity ? "Advanced ICU (Ventilator & Cardiac Monitoring)" : "Specialized Care Ward";
        long simulatedBedId = (bestHospital.getHospitalId() * 100L) + 5L;

        String reason = String.format(
                "Hospital '%s' selected with clinical score %.1f (%d total beds, %.2f km away) for %s patient.",
                bestHospital.getName(),
                bestScore,
                bestHospital.getTotalBeds(),
                chosenDistanceKm,
                (patient != null ? patient.getTriageLevel().getDescription() : "general")
        );

        BedAllocationResult result = new BedAllocationResult(
                bestHospital,
                simulatedBedId,
                wardName,
                chosenDistanceKm,
                getStrategyName(),
                reason
        );

        return Optional.of(result);
    }
}
