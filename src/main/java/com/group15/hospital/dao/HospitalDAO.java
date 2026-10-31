package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.hierarchy.Hospital;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

// hospital dao class
public class HospitalDAO {

    private static final Logger LOGGER = Logger.getLogger(HospitalDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // in-memory store for fallback
    private final ConcurrentHashMap<Integer, Hospital> inMemoryStore = new ConcurrentHashMap<>();
    private final AtomicInteger idSequence = new AtomicInteger(100);

    // sql queries
    private static final String SQL_INSERT =
            "INSERT INTO hospitals (name, address, latitude, longitude, contact_phone, total_beds) " +
            "VALUES (?, ?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_BY_ID =
            "SELECT hospital_id, name, address, latitude, longitude, contact_phone, total_beds " +
            "FROM hospitals WHERE hospital_id = ?";

    private static final String SQL_SELECT_BY_NAME =
            "SELECT hospital_id, name, address, latitude, longitude, contact_phone, total_beds " +
            "FROM hospitals WHERE name = ?";

    private static final String SQL_SELECT_ALL =
            "SELECT hospital_id, name, address, latitude, longitude, contact_phone, total_beds " +
            "FROM hospitals ORDER BY hospital_id ASC";

    private static final String SQL_UPDATE =
            "UPDATE hospitals SET name = ?, address = ?, latitude = ?, longitude = ?, " +
            "contact_phone = ?, total_beds = ? WHERE hospital_id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM hospitals WHERE hospital_id = ?";

    public HospitalDAO() {
        // seed sample hospitals for fallback
        seedDefaultHospital(1, "City Central Hospital", "123 Main St, Bangalore", 12.9716, 77.5946, "080-22221111", 120);
        seedDefaultHospital(2, "Memorial Care Hospital", "456 Health Ave, Bangalore", 12.9352, 77.6245, "080-33332222", 85);
        seedDefaultHospital(3, "Metro General Hospital", "789 North Blvd, Bangalore", 13.0358, 77.5970, "080-44443333", 150);
    }

    private void seedDefaultHospital(int id, String name, String addr, double lat, double lon, String phone, int beds) {
        Hospital h = new Hospital(id, name, addr, lat, lon, phone, beds);
        inMemoryStore.put(id, h);
    }

    // saves hospital to database or in-memory
    public Hospital save(Hospital hospital) {
        if (hospital == null) {
            throw new IllegalArgumentException("Hospital cannot be null");
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, (hospital.getName() != null) ? hospital.getName() : "Unnamed Hospital");
            ps.setString(2, (hospital.getAddress() != null) ? hospital.getAddress() : "Address Unspecified");
            ps.setDouble(3, hospital.getLatitude());
            ps.setDouble(4, hospital.getLongitude());
            ps.setString(5, (hospital.getContactPhone() != null) ? hospital.getContactPhone() : "");
            ps.setInt(6, hospital.getTotalBeds());

            int affected = ps.executeUpdate();
            if (affected > 0) {
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        int generatedId = rs.getInt(1);
                        hospital.setHospitalId(generatedId);
                    }
                }
            }
            // Keep in-memory store synchronized
            inMemoryStore.put(hospital.getHospitalId(), hospital);
            return hospital;

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to insert hospital into DB. Using in-memory fallback: " + e.getMessage());
            int newId = (hospital.getHospitalId() > 0) ? hospital.getHospitalId() : idSequence.incrementAndGet();
            hospital.setHospitalId(newId);
            inMemoryStore.put(newId, hospital);
            return hospital;
        }
    }

    // finds hospital by id
    public Optional<Hospital> findById(int hospitalId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, hospitalId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Hospital hospital = mapRowToHospital(rs);
                    inMemoryStore.put(hospital.getHospitalId(), hospital);
                    return Optional.of(hospital);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for hospitalId=" + hospitalId + ", reading from fallback: " + e.getMessage());
        }

        // Fallback to in-memory store
        return Optional.ofNullable(inMemoryStore.get(hospitalId));
    }

    // finds hospital by name
    public Optional<Hospital> findByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_NAME)) {

            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Hospital hospital = mapRowToHospital(rs);
                    inMemoryStore.put(hospital.getHospitalId(), hospital);
                    return Optional.of(hospital);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for hospital name=" + name + ", checking fallback: " + e.getMessage());
        }

        // Fallback search
        for (Hospital h : inMemoryStore.values()) {
            if (h.getName() != null && h.getName().equalsIgnoreCase(name.trim())) {
                return Optional.of(h);
            }
        }
        return Optional.empty();
    }

    // gets all hospitals
    public List<Hospital> findAll() {
        List<Hospital> list = new ArrayList<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Hospital hospital = mapRowToHospital(rs);
                list.add(hospital);
                inMemoryStore.put(hospital.getHospitalId(), hospital);
            }
            if (!list.isEmpty()) {
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB query failed for findAll hospitals. Using fallback: " + e.getMessage());
        }

        return new ArrayList<>(inMemoryStore.values());
    }

    // updates hospital details
    public boolean update(Hospital hospital) {
        if (hospital == null || hospital.getHospitalId() <= 0) {
            return false;
        }

        boolean updatedInDb = false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE)) {

            ps.setString(1, (hospital.getName() != null) ? hospital.getName() : "Unnamed Hospital");
            ps.setString(2, (hospital.getAddress() != null) ? hospital.getAddress() : "Address Unspecified");
            ps.setDouble(3, hospital.getLatitude());
            ps.setDouble(4, hospital.getLongitude());
            ps.setString(5, (hospital.getContactPhone() != null) ? hospital.getContactPhone() : "");
            ps.setInt(6, hospital.getTotalBeds());
            ps.setInt(7, hospital.getHospitalId());

            updatedInDb = (ps.executeUpdate() > 0);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB update failed for hospital " + hospital.getHospitalId() + ": " + e.getMessage());
        }

        inMemoryStore.put(hospital.getHospitalId(), hospital);
        return updatedInDb || inMemoryStore.containsKey(hospital.getHospitalId());
    }

    // deletes hospital by id
    public boolean delete(int hospitalId) {
        boolean deletedInDb = false;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setInt(1, hospitalId);
            deletedInDb = (ps.executeUpdate() > 0);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "DB delete failed for hospital " + hospitalId + ": " + e.getMessage());
        }

        Hospital removed = inMemoryStore.remove(hospitalId);
        return deletedInDb || (removed != null);
    }

    private Hospital mapRowToHospital(ResultSet rs) throws SQLException {
        int id = rs.getInt("hospital_id");
        String name = rs.getString("name");
        String address = rs.getString("address");
        double lat = rs.getDouble("latitude");
        double lon = rs.getDouble("longitude");
        String phone = rs.getString("contact_phone");
        int beds = rs.getInt("total_beds");

        return new Hospital(id, name, address, lat, lon, phone, beds);
    }
}
