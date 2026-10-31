package com.group15.hospital.service.strategy;

import com.group15.hospital.dao.AuditDAO;
import com.group15.hospital.dao.HospitalDAO;
import com.group15.hospital.model.hierarchy.AuditLogEntry;
import com.group15.hospital.model.hierarchy.Hospital;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

// service for hospital admin operations
public class HospitalAdminService {

    private static final Logger LOGGER = Logger.getLogger(HospitalAdminService.class.getName());

    private final HospitalDAO hospitalDAO;
    private final AuditDAO auditDAO;

    public HospitalAdminService(HospitalDAO hospitalDAO, AuditDAO auditDAO) {
        this.hospitalDAO = Objects.requireNonNull(hospitalDAO, "HospitalDAO cannot be null");
        this.auditDAO = Objects.requireNonNull(auditDAO, "AuditDAO cannot be null");
    }

    public HospitalAdminService() {
        this(new HospitalDAO(), new AuditDAO());
    }

    // registers a new hospital
    public Hospital registerHospital(String adminUser, String name, String address,
                                     double lat, double lon, String phone, int totalBeds) {
        Hospital h = new Hospital(name, address, lat, lon, phone, totalBeds);
        Hospital saved = hospitalDAO.save(h);

        auditDAO.log(
                (adminUser != null) ? adminUser : "admin",
                "HOSPITAL_REGISTERED",
                String.valueOf(saved.getHospitalId()),
                "Hospital registered: " + saved.getName() + " with " + totalBeds + " beds."
        );

        LOGGER.info("[HospitalAdminService] Registered hospital: " + saved.getName());
        return saved;
    }

    // updates hospital details
    public boolean updateHospitalDetails(String adminUser, Hospital hospital) {
        if (hospital == null) {
            return false;
        }

        boolean success = hospitalDAO.update(hospital);
        if (success) {
            auditDAO.log(
                    (adminUser != null) ? adminUser : "admin",
                    "HOSPITAL_UPDATED",
                    String.valueOf(hospital.getHospitalId()),
                    "Updated details for: " + hospital.getName()
            );
        }
        return success;
    }

    // removes a hospital
    public boolean removeHospital(String adminUser, int hospitalId) {
        Optional<Hospital> target = hospitalDAO.findById(hospitalId);
        String name = target.map(Hospital::getName).orElse("Unknown");

        boolean success = hospitalDAO.delete(hospitalId);
        if (success) {
            auditDAO.log(
                    (adminUser != null) ? adminUser : "admin",
                    "HOSPITAL_DELETED",
                    String.valueOf(hospitalId),
                    "Removed hospital from network: " + name
            );
        }
        return success;
    }

    // returns all hospitals
    public List<Hospital> getAllHospitals() {
        return hospitalDAO.findAll();
    }

    // returns audit logs
    public List<AuditLogEntry> getAuditTrail() {
        return auditDAO.findAll();
    }

    // calculates total bed capacity across network
    public int getTotalNetworkBedCapacity() {
        int sum = 0;
        for (Hospital h : hospitalDAO.findAll()) {
            if (h != null) {
                sum += h.getTotalBeds();
            }
        }
        return sum;
    }
}
