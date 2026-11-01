package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.exception.BedAlreadyBookedException;
import com.group15.hospital.exception.TransactionRollbackException;
import com.group15.hospital.model.ambulance.ALSAmbulance;
import com.group15.hospital.model.ambulance.Ambulance;
import com.group15.hospital.model.ambulance.AmbulanceReservation;
import com.group15.hospital.model.ambulance.BLSAmbulance;
import com.group15.hospital.model.enums.BedStatus;
import com.group15.hospital.model.enums.EquipmentType;
import com.group15.hospital.model.enums.TriageLevel;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Data Access Object managing persistence and atomic transactions for ambulances,
 * bed reservations, and emergency rerouting operations (SRS F5, F8, F9, F15 & Use Case U5).
 * <p>
 * Implements strict thread-safe locking and ACID transactions via {@link DatabaseConnection}.
 * Includes an in-memory synchronized fallback store (using {@link ConcurrentHashMap} and
 * per-bed {@link ReentrantLock} monitors) ensuring zero-failure operation during offline testing
 * and concurrency tests.
 * </p>
 *
 * @author Siddhant (Module P4)
 */
public class AmbulanceDAO {

    private static final Logger LOGGER = Logger.getLogger(AmbulanceDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    // ── In-Memory Fallback & Concurrency Monitors ──
    private final ConcurrentHashMap<String, Ambulance> ambulanceStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AmbulanceReservation> reservationStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, BedStatus> simulatedBedStore = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ReentrantLock> bedLocks = new ConcurrentHashMap<>();
    private final AtomicLong reservationIdSequence = new AtomicLong(1000);

    // ── SQL Constants ──
    private static final String SQL_INSERT_AMBULANCE =
            "INSERT INTO ambulances (ambulance_id, license_plate, ambulance_type, current_hospital_name, is_available) " +
            "VALUES (?, ?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE license_plate = VALUES(license_plate), " +
            "ambulance_type = VALUES(ambulance_type), " +
            "current_hospital_name = VALUES(current_hospital_name), " +
            "is_available = VALUES(is_available)";

    private static final String SQL_FIND_AMBULANCE_BY_ID =
            "SELECT ambulance_id, license_plate, ambulance_type, current_hospital_name, is_available FROM ambulances WHERE ambulance_id = ?";

    private static final String SQL_FIND_ALL_AMBULANCES =
            "SELECT ambulance_id, license_plate, ambulance_type, current_hospital_name, is_available FROM ambulances";

    private static final String SQL_UPDATE_AMB_AVAILABILITY =
            "UPDATE ambulances SET is_available = ?, current_hospital_name = ? WHERE ambulance_id = ?";

    private static final String SQL_LOCK_BED_FOR_RESERVATION =
            "UPDATE beds SET status = 'RESERVED_EN_ROUTE' WHERE bed_id = ? AND status = 'VACANT'";

    private static final String SQL_RELEASE_BED_TO_VACANT =
            "UPDATE beds SET status = 'VACANT' WHERE bed_id = ? AND status = 'RESERVED_EN_ROUTE'";

    private static final String SQL_OCCUPY_RESERVED_BED =
            "UPDATE beds SET status = 'OCCUPIED', patient_name = ?, patient_triage = ? WHERE bed_id = ? AND status = 'RESERVED_EN_ROUTE'";

    private static final String SQL_INSERT_RESERVATION =
            "INSERT INTO ambulance_reservations " +
            "(ambulance_id, ambulance_type, patient_name, patient_triage, required_equipment, hospital_name, ward_name, assigned_bed_id, reserved_at, expires_at, status, rerouted_from_hospital) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_UPDATE_RESERVATION_REROUTE =
            "UPDATE ambulance_reservations SET hospital_name = ?, ward_name = ?, assigned_bed_id = ?, expires_at = ?, status = ?, rerouted_from_hospital = ? WHERE reservation_id = ?";

    private static final String SQL_UPDATE_RESERVATION_STATUS =
            "UPDATE ambulance_reservations SET status = ? WHERE reservation_id = ?";

    private static final String SQL_EXTEND_RESERVATION =
            "UPDATE ambulance_reservations SET expires_at = ? WHERE reservation_id = ? AND status = 'RESERVED_EN_ROUTE'";

    private static final String SQL_FIND_RESERVATION_BY_ID =
            "SELECT reservation_id, ambulance_id, ambulance_type, patient_name, patient_triage, required_equipment, " +
            "hospital_name, ward_name, assigned_bed_id, reserved_at, expires_at, status, rerouted_from_hospital " +
            "FROM ambulance_reservations WHERE reservation_id = ?";

    private static final String SQL_FIND_ACTIVE_BY_HOSPITAL =
            "SELECT reservation_id, ambulance_id, ambulance_type, patient_name, patient_triage, required_equipment, " +
            "hospital_name, ward_name, assigned_bed_id, reserved_at, expires_at, status, rerouted_from_hospital " +
            "FROM ambulance_reservations WHERE hospital_name = ? AND status = 'RESERVED_EN_ROUTE'";

    private static final String SQL_FIND_ALL_ACTIVE =
            "SELECT reservation_id, ambulance_id, ambulance_type, patient_name, patient_triage, required_equipment, " +
            "hospital_name, ward_name, assigned_bed_id, reserved_at, expires_at, status, rerouted_from_hospital " +
            "FROM ambulance_reservations WHERE status = 'RESERVED_EN_ROUTE'";

    private static final String SQL_FIND_ALL_RESERVATIONS =
            "SELECT reservation_id, ambulance_id, ambulance_type, patient_name, patient_triage, required_equipment, " +
            "hospital_name, ward_name, assigned_bed_id, reserved_at, expires_at, status, rerouted_from_hospital " +
            "FROM ambulance_reservations ORDER BY reservation_id DESC";

    public AmbulanceDAO() {
        initDefaultFleet();
    }

    private void initDefaultFleet() {
        registerOrUpdateAmbulance(new ALSAmbulance("AMB-ALS-101", "KA-01-EQ-1001", "City Central Hospital", true));
        registerOrUpdateAmbulance(new ALSAmbulance("AMB-ALS-102", "KA-01-EQ-1002", "Memorial Care Hospital", true));
        registerOrUpdateAmbulance(new BLSAmbulance("AMB-BLS-201", "KA-02-TX-2001", "City Central Hospital", true));
        registerOrUpdateAmbulance(new BLSAmbulance("AMB-BLS-202", "KA-02-TX-2002", "Metro General Hospital", true));
    }

    // ──────────────────────────── Testing Helpers ────────────────────────────

    /**
     * Seeds or sets a simulated bed status in the in-memory store for isolated testing.
     */
    public void seedBedForTesting(long bedId, BedStatus status) {
        simulatedBedStore.put(bedId, status);
    }

    public void seedBedForTesting(long bedId, BedStatus status, String hospitalName) {
        simulatedBedStore.put(bedId, status);
    }

    public BedStatus getSimulatedBedStatus(long bedId) {
        return simulatedBedStore.getOrDefault(bedId, BedStatus.VACANT);
    }

    public BedStatus getBedStatus(long bedId) {
        return getSimulatedBedStatus(bedId);
    }

    // ──────────────────────── Ambulance Registry ──────────────────────────

    /**
     * Registers or updates an ambulance in the database and in-memory cache.
     */
    public void registerOrUpdateAmbulance(Ambulance ambulance) {
        Objects.requireNonNull(ambulance, "ambulance must not be null");
        ambulanceStore.put(ambulance.getAmbulanceId(), ambulance);

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT_AMBULANCE)) {
            ps.setString(1, ambulance.getAmbulanceId());
            ps.setString(2, ambulance.getLicensePlate());
            ps.setString(3, ambulance.getAmbulanceType());
            ps.setString(4, ambulance.getCurrentHospitalName());
            ps.setBoolean(5, ambulance.isAvailable());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL not reachable for ambulance registration, using in-memory store: " + e.getMessage());
        }
    }

    public Optional<Ambulance> findAmbulanceById(String ambulanceId) {
        if (ambulanceId == null) return Optional.empty();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_AMBULANCE_BY_ID)) {
            ps.setString(1, ambulanceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Ambulance amb = mapAmbulanceRow(rs);
                    ambulanceStore.put(amb.getAmbulanceId(), amb);
                    return Optional.of(amb);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL not reachable, querying in-memory store: " + e.getMessage());
        }

        return Optional.ofNullable(ambulanceStore.get(ambulanceId));
    }

    public List<Ambulance> findAllAmbulances() {
        List<Ambulance> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL_AMBULANCES);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapAmbulanceRow(rs));
            }
            if (!list.isEmpty()) {
                list.forEach(a -> ambulanceStore.put(a.getAmbulanceId(), a));
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL not reachable, returning in-memory fleet: " + e.getMessage());
        }
        return new ArrayList<>(ambulanceStore.values());
    }

    public List<Ambulance> findAvailableAmbulances(String ambulanceType) {
        return findAllAmbulances().stream()
                .filter(Ambulance::isAvailable)
                .filter(a -> ambulanceType == null || a.getAmbulanceType().equalsIgnoreCase(ambulanceType))
                .collect(Collectors.toList());
    }

    public void updateAmbulanceAvailability(String ambulanceId, boolean available, String currentHospitalName) {
        Ambulance amb = ambulanceStore.get(ambulanceId);
        if (amb != null) {
            amb.setAvailable(available);
            if (currentHospitalName != null) {
                amb.setCurrentHospitalName(currentHospitalName);
            }
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_AMB_AVAILABILITY)) {
            ps.setBoolean(1, available);
            ps.setString(2, currentHospitalName);
            ps.setString(3, ambulanceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL not reachable for availability update: " + e.getMessage());
        }
    }

    // ──────────────────────── Atomic Bed Reservations ─────────────────────

    /**
     * Atomically creates a bed reservation for an incoming ambulance (SRS F5, F8 & U5).
     * <p>
     * Protects against concurrent double-booking using lock synchronization and optimistic checking.
     * Transitions the bed to {@link BedStatus#RESERVED_EN_ROUTE}.
     * </p>
     *
     * @param reservation the reservation payload
     * @return the persisted reservation with generated ID
     * @throws BedAlreadyBookedException if the bed is already booked or reserved concurrently
     */
    public AmbulanceReservation createReservationAtomic(AmbulanceReservation reservation) {
        Objects.requireNonNull(reservation, "reservation must not be null");
        long bedId = reservation.getAssignedBedId();

        ReentrantLock lock = bedLocks.computeIfAbsent(bedId, k -> new ReentrantLock());
        lock.lock();
        try {
            // 1. Try relational ACID transaction if DB is reachable
            boolean dbSuccess = false;
            long genId = 0;

            try {
                genId = db.executeTransaction(conn -> {
                    // Atomic bed lock & status change
                    try (PreparedStatement lockPs = conn.prepareStatement(SQL_LOCK_BED_FOR_RESERVATION)) {
                        lockPs.setLong(1, bedId);
                        int updated = lockPs.executeUpdate();
                        if (updated == 0) {
                            throw new BedAlreadyBookedException(
                                    "Bed #" + bedId + " could not be reserved: bed is not VACANT.");
                        }
                    }

                    // Insert reservation record
                    try (PreparedStatement insertPs = conn.prepareStatement(SQL_INSERT_RESERVATION, Statement.RETURN_GENERATED_KEYS)) {
                        insertPs.setString(1, reservation.getAmbulanceId());
                        insertPs.setString(2, reservation.getAmbulanceType());
                        insertPs.setString(3, reservation.getPatientName());
                        insertPs.setString(4, reservation.getPatientTriage().name());
                        insertPs.setString(5, serializeEquipment(reservation.getRequiredEquipment()));
                        insertPs.setString(6, reservation.getHospitalName());
                        insertPs.setString(7, reservation.getWardName());
                        insertPs.setLong(8, bedId);
                        insertPs.setTimestamp(9, Timestamp.valueOf(reservation.getReservedAt()));
                        insertPs.setTimestamp(10, Timestamp.valueOf(reservation.getExpiresAt()));
                        insertPs.setString(11, reservation.getStatus());
                        insertPs.setString(12, reservation.getReroutedFromHospital());
                        insertPs.executeUpdate();

                        try (ResultSet rs = insertPs.getGeneratedKeys()) {
                            if (rs.next()) {
                                return rs.getLong(1);
                            }
                        }
                    }
                    return 0L;
                });
                dbSuccess = true;
            } catch (TransactionRollbackException tre) {
                if (tre.getCause() instanceof BedAlreadyBookedException bab) {
                    throw bab;
                }
                LOGGER.log(Level.FINE, "MySQL unavailable or transaction rolled back, attempting memory sync: " + tre.getMessage());
            }

            // 2. In-memory synchronization & fallback verification
            BedStatus currentStatus = simulatedBedStore.getOrDefault(bedId, BedStatus.VACANT);
            if (!dbSuccess && currentStatus != BedStatus.VACANT) {
                throw new BedAlreadyBookedException(
                        "Bed #" + bedId + " could not be reserved: current status is " + currentStatus);
            }

            // Update in-memory stores
            simulatedBedStore.put(bedId, BedStatus.RESERVED_EN_ROUTE);
            long finalId = (genId > 0) ? genId : reservationIdSequence.incrementAndGet();

            AmbulanceReservation saved = new AmbulanceReservation(
                    finalId,
                    reservation.getAmbulanceId(),
                    reservation.getAmbulanceType(),
                    reservation.getPatientName(),
                    reservation.getPatientTriage(),
                    reservation.getRequiredEquipment(),
                    reservation.getHospitalName(),
                    reservation.getWardName(),
                    bedId,
                    reservation.getReservedAt(),
                    reservation.getExpiresAt(),
                    AmbulanceReservation.STATUS_RESERVED,
                    reservation.getReroutedFromHospital()
            );

            reservationStore.put(finalId, saved);
            LOGGER.info("Created atomic reservation #" + finalId + " for bed #" + bedId + " @ " + reservation.getHospitalName());
            return saved;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Atomically reroutes an in-flight reservation to a partner hospital (SRS F9 & U5 Alt Flow 1).
     * Releases old bed and locks the partner bed in a single ACID operation.
     */
    public AmbulanceReservation rerouteReservationAtomic(
            long reservationId,
            String newHospitalName,
            String newWardName,
            long newBedId,
            int newEtaMinutes
    ) {
        AmbulanceReservation existing = findReservationById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found with ID: " + reservationId));

        long oldBedId = existing.getAssignedBedId();
        String oldHospital = existing.getHospitalName();
        LocalDateTime newExpiry = LocalDateTime.now().plusMinutes(newEtaMinutes > 0 ? newEtaMinutes : AmbulanceReservation.DEFAULT_WINDOW_MINUTES);

        // Lock both beds in consistent order to prevent deadlocks
        long firstLockId = Math.min(oldBedId, newBedId);
        long secondLockId = Math.max(oldBedId, newBedId);

        ReentrantLock lock1 = bedLocks.computeIfAbsent(firstLockId, k -> new ReentrantLock());
        ReentrantLock lock2 = bedLocks.computeIfAbsent(secondLockId, k -> new ReentrantLock());

        lock1.lock();
        lock2.lock();
        try {
            boolean dbSuccess = false;
            try {
                db.executeTransactionVoid(conn -> {
                    // Release old bed
                    try (PreparedStatement releasePs = conn.prepareStatement(SQL_RELEASE_BED_TO_VACANT)) {
                        releasePs.setLong(1, oldBedId);
                        releasePs.executeUpdate();
                    }

                    // Claim new bed
                    try (PreparedStatement claimPs = conn.prepareStatement(SQL_LOCK_BED_FOR_RESERVATION)) {
                        claimPs.setLong(1, newBedId);
                        int affected = claimPs.executeUpdate();
                        if (affected == 0) {
                            throw new BedAlreadyBookedException("Partner bed #" + newBedId + " is no longer VACANT for rerouting.");
                        }
                    }

                    // Update reservation record
                    try (PreparedStatement updatePs = conn.prepareStatement(SQL_UPDATE_RESERVATION_REROUTE)) {
                        updatePs.setString(1, newHospitalName);
                        updatePs.setString(2, newWardName);
                        updatePs.setLong(3, newBedId);
                        updatePs.setTimestamp(4, Timestamp.valueOf(newExpiry));
                        updatePs.setString(5, AmbulanceReservation.STATUS_RESERVED);
                        updatePs.setString(6, oldHospital);
                        updatePs.setLong(7, reservationId);
                        updatePs.executeUpdate();
                    }
                });
                dbSuccess = true;
            } catch (TransactionRollbackException tre) {
                if (tre.getCause() instanceof BedAlreadyBookedException bab) {
                    throw bab;
                }
                LOGGER.log(Level.FINE, "MySQL unavailable during reroute, updating in-memory store: " + tre.getMessage());
            }

            // In-memory verification & fallback
            if (!dbSuccess) {
                BedStatus newStatus = simulatedBedStore.getOrDefault(newBedId, BedStatus.VACANT);
                if (newStatus != BedStatus.VACANT) {
                    throw new BedAlreadyBookedException("Partner bed #" + newBedId + " is not VACANT: " + newStatus);
                }
            }

            // Update in-memory states
            simulatedBedStore.put(oldBedId, BedStatus.VACANT);
            simulatedBedStore.put(newBedId, BedStatus.RESERVED_EN_ROUTE);

            existing.setHospitalName(newHospitalName);
            existing.setWardName(newWardName);
            existing.setAssignedBedId(newBedId);
            existing.setExpiresAt(newExpiry);
            existing.setStatus(AmbulanceReservation.STATUS_RESERVED);
            existing.setReroutedFromHospital(oldHospital);

            reservationStore.put(reservationId, existing);
            LOGGER.info("Rerouted reservation #" + reservationId + " from " + oldHospital + " to " + newHospitalName + " bed #" + newBedId);
            return existing;
        } finally {
            lock2.unlock();
            lock1.unlock();
        }
    }

    /**
     * Confirms ambulance patient arrival, transitioning the bed to OCCUPIED and reservation to ARRIVED_OCCUPIED (U5 Step 5).
     */
    public void confirmArrivalAtomic(long reservationId) {
        AmbulanceReservation res = findReservationById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation #" + reservationId + " not found"));

        long bedId = res.getAssignedBedId();
        ReentrantLock lock = bedLocks.computeIfAbsent(bedId, k -> new ReentrantLock());
        lock.lock();
        try {
            try {
                db.executeTransactionVoid(conn -> {
                    try (PreparedStatement bedPs = conn.prepareStatement(SQL_OCCUPY_RESERVED_BED)) {
                        bedPs.setString(1, res.getPatientName());
                        bedPs.setString(2, res.getPatientTriage().name());
                        bedPs.setLong(3, bedId);
                        bedPs.executeUpdate();
                    }
                    try (PreparedStatement resPs = conn.prepareStatement(SQL_UPDATE_RESERVATION_STATUS)) {
                        resPs.setString(1, AmbulanceReservation.STATUS_ARRIVED);
                        resPs.setLong(2, reservationId);
                        resPs.executeUpdate();
                    }
                });
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "MySQL unavailable for arrival confirmation: " + e.getMessage());
            }

            simulatedBedStore.put(bedId, BedStatus.OCCUPIED);
            res.setStatus(AmbulanceReservation.STATUS_ARRIVED);
            reservationStore.put(reservationId, res);
            updateAmbulanceAvailability(res.getAmbulanceId(), true, res.getHospitalName());
            LOGGER.info("Confirmed patient arrival for reservation #" + reservationId + " into bed #" + bedId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Extends the reservation window when paramedic signals a delay (U5 Alt Flow 2).
     */
    public boolean extendReservationWindow(long reservationId, int extraMinutes) {
        AmbulanceReservation res = reservationStore.get(reservationId);
        if (res == null) {
            Optional<AmbulanceReservation> dbRes = findReservationById(reservationId);
            if (dbRes.isEmpty()) return false;
            res = dbRes.get();
        }

        if (!AmbulanceReservation.STATUS_RESERVED.equals(res.getStatus())) {
            return false;
        }

        res.extendWindow(extraMinutes);
        LocalDateTime newExpiry = res.getExpiresAt();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_EXTEND_RESERVATION)) {
            ps.setTimestamp(1, Timestamp.valueOf(newExpiry));
            ps.setLong(2, reservationId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for window extension: " + e.getMessage());
        }

        return true;
    }

    /**
     * Scans for reservations exceeding their 15-minute window, releases held beds back to VACANT,
     * and marks reservations EXPIRED (SRS F8 & U5 Exception 2).
     *
     * @param now current time reference
     * @return list of reservations that were expired during this sweep
     */
    public List<AmbulanceReservation> expireOverdueReservations(LocalDateTime now) {
        if (now == null) now = LocalDateTime.now();
        List<AmbulanceReservation> expired = new ArrayList<>();

        for (AmbulanceReservation res : findAllActiveReservations()) {
            if (res.isExpired(now)) {
                long bedId = res.getAssignedBedId();
                ReentrantLock lock = bedLocks.computeIfAbsent(bedId, k -> new ReentrantLock());
                lock.lock();
                try {
                    // Release bed in DB
                    try {
                        db.executeTransactionVoid(conn -> {
                            try (PreparedStatement psBed = conn.prepareStatement(SQL_RELEASE_BED_TO_VACANT)) {
                                psBed.setLong(1, bedId);
                                psBed.executeUpdate();
                            }
                            try (PreparedStatement psRes = conn.prepareStatement(SQL_UPDATE_RESERVATION_STATUS)) {
                                psRes.setString(1, AmbulanceReservation.STATUS_EXPIRED);
                                psRes.setLong(2, res.getReservationId());
                                psRes.executeUpdate();
                            }
                        });
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE, "MySQL unavailable for reservation expiry: " + e.getMessage());
                    }

                    simulatedBedStore.put(bedId, BedStatus.VACANT);
                    res.setStatus(AmbulanceReservation.STATUS_EXPIRED);
                    reservationStore.put(res.getReservationId(), res);
                    expired.add(res);
                    LOGGER.info("Expired reservation #" + res.getReservationId() + ", released bed #" + bedId);
                } finally {
                    lock.unlock();
                }
            }
        }

        return expired;
    }

    public Optional<AmbulanceReservation> findReservationById(long reservationId) {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_RESERVATION_BY_ID)) {
            ps.setLong(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    AmbulanceReservation res = mapReservationRow(rs);
                    reservationStore.put(res.getReservationId(), res);
                    return Optional.of(res);
                }
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for findReservationById: " + e.getMessage());
        }

        return Optional.ofNullable(reservationStore.get(reservationId));
    }

    public List<AmbulanceReservation> findActiveReservationsByHospital(String hospitalName) {
        List<AmbulanceReservation> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ACTIVE_BY_HOSPITAL)) {
            ps.setString(1, hospitalName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapReservationRow(rs));
                }
            }
            if (!list.isEmpty()) {
                list.forEach(r -> reservationStore.put(r.getReservationId(), r));
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for findActiveReservationsByHospital: " + e.getMessage());
        }

        return reservationStore.values().stream()
                .filter(r -> r.getHospitalName().equalsIgnoreCase(hospitalName))
                .filter(r -> AmbulanceReservation.STATUS_RESERVED.equals(r.getStatus()))
                .collect(Collectors.toList());
    }

    public List<AmbulanceReservation> findAllActiveReservations() {
        List<AmbulanceReservation> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL_ACTIVE);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapReservationRow(rs));
            }
            if (!list.isEmpty()) {
                list.forEach(r -> reservationStore.put(r.getReservationId(), r));
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for findAllActiveReservations: " + e.getMessage());
        }

        return reservationStore.values().stream()
                .filter(r -> AmbulanceReservation.STATUS_RESERVED.equals(r.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Returns all reservations (in-flight holds, confirmed arrivals, expired, and rerouted).
     */
    public List<AmbulanceReservation> findAllReservations() {
        List<AmbulanceReservation> list = new ArrayList<>();
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_ALL_RESERVATIONS);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapReservationRow(rs));
            }
            if (!list.isEmpty()) {
                list.forEach(r -> reservationStore.put(r.getReservationId(), r));
                return list;
            }
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "MySQL unavailable for findAllReservations: " + e.getMessage());
        }

        return reservationStore.values().stream()
                .sorted((a, b) -> Long.compare(b.getReservationId(), a.getReservationId()))
                .collect(Collectors.toList());
    }

    // ──────────────────────────── Row Mappers ─────────────────────────────

    private Ambulance mapAmbulanceRow(ResultSet rs) throws SQLException {
        String id = rs.getString("ambulance_id");
        String plate = rs.getString("license_plate");
        String type = rs.getString("ambulance_type");
        String hosp = rs.getString("current_hospital_name");
        boolean avail = rs.getBoolean("is_available");

        if ("ALS".equalsIgnoreCase(type)) {
            return new ALSAmbulance(id, plate, hosp, avail);
        } else {
            return new BLSAmbulance(id, plate, hosp, avail);
        }
    }

    private AmbulanceReservation mapReservationRow(ResultSet rs) throws SQLException {
        long id = rs.getLong("reservation_id");
        String ambId = rs.getString("ambulance_id");
        String ambType = rs.getString("ambulance_type");
        String patient = rs.getString("patient_name");
        TriageLevel triage = TriageLevel.parseDatabase(rs.getString("patient_triage"));
        Set<EquipmentType> eq = parseEquipment(rs.getString("required_equipment"));
        String hosp = rs.getString("hospital_name");
        String ward = rs.getString("ward_name");
        long bedId = rs.getLong("assigned_bed_id");
        LocalDateTime reserved = rs.getTimestamp("reserved_at").toLocalDateTime();
        LocalDateTime expires = rs.getTimestamp("expires_at").toLocalDateTime();
        String status = rs.getString("status");
        String reroutedFrom = rs.getString("rerouted_from_hospital");

        return new AmbulanceReservation(
                id, ambId, ambType, patient, triage, eq, hosp, ward, bedId, reserved, expires, status, reroutedFrom
        );
    }

    private String serializeEquipment(Set<EquipmentType> equipment) {
        if (equipment == null || equipment.isEmpty()) return "";
        return equipment.stream().map(EquipmentType::name).collect(Collectors.joining(","));
    }

    private Set<EquipmentType> parseEquipment(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptySet();
        Set<EquipmentType> set = new HashSet<>();
        for (String token : raw.split(",")) {
            if (!token.isBlank()) {
                try {
                    set.add(EquipmentType.parseDatabase(token.trim()));
                } catch (Exception ignored) {}
            }
        }
        return set;
    }
}
