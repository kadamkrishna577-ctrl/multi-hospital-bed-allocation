package com.group15.hospital.service.strategy;

import com.group15.hospital.model.hierarchy.Hospital;
import com.group15.hospital.model.hierarchy.Patient;

import java.util.List;
import java.util.Optional;

// strategy interface for bed allocation
public interface BedAllocationStrategy {

    // name of the strategy
    String getStrategyName();

    // executes allocation algorithm
    Optional<BedAllocationResult> allocateBed(
            Patient patient,
            double patientLat,
            double patientLon,
            List<Hospital> hospitals
    );

    // result holder class
    class BedAllocationResult {
        private final Hospital hospital;
        private final long bedId;
        private final String wardName;
        private final double distanceKm;
        private final String strategyUsed;
        private final String rationale;

        public BedAllocationResult(Hospital hospital, long bedId, String wardName,
                                   double distanceKm, String strategyUsed, String rationale) {
            this.hospital = hospital;
            this.bedId = bedId;
            this.wardName = wardName;
            this.distanceKm = distanceKm;
            this.strategyUsed = strategyUsed;
            this.rationale = rationale;
        }

        public Hospital getHospital() {
            return hospital;
        }

        public long getBedId() {
            return bedId;
        }

        public String getWardName() {
            return wardName;
        }

        public double getDistanceKm() {
            return distanceKm;
        }

        public String getStrategyUsed() {
            return strategyUsed;
        }

        public String getRationale() {
            return rationale;
        }

        @Override
        public String toString() {
            return String.format(
                    "Allocated Bed #%d at %s [%s] (%.2f km away) via '%s'. Reason: %s",
                    bedId, hospital.getName(), wardName, distanceKm, strategyUsed, rationale
            );
        }
    }
}
