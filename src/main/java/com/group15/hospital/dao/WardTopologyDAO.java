package com.group15.hospital.dao;

import com.group15.hospital.config.DatabaseConnection;
import com.group15.hospital.model.isolation.WardTopologyGraph;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for persisting and querying ward bed topology and adjacency relationships.
 * <p>
 * Matches database table {@code ward_topology}. Supports infection isolation containment (SRS F12)
 * by populating {@link WardTopologyGraph} instances from database records and providing
 * low-latency queries for neighboring and shared-air beds. Includes an in-memory fallback
 * for offline operations and test isolation.
 * </p>
 *
 * @author Jatin (Module P3)
 * @see WardTopologyGraph
 * @see DatabaseConnection
 */
public class WardTopologyDAO {

    private static final Logger LOGGER = Logger.getLogger(WardTopologyDAO.class.getName());

    private final DatabaseConnection db = DatabaseConnection.getInstance();

    /** In-memory graph fallback for offline execution and test isolation. */
    private final WardTopologyGraph inMemoryGraph = new WardTopologyGraph();

    // ──────────────────────────── SQL Constants ────────────────────────────

    private static final String TABLE = "ward_topology";

    private static final String SQL_INSERT =
            "INSERT INTO " + TABLE + " (bed_id_1, bed_id_2, relationship_type) VALUES (?, ?, ?)";

    private static final String SQL_LOAD_ALL =
            "SELECT topology_id, bed_id_1, bed_id_2, relationship_type FROM " + TABLE;

    private static final String SQL_FIND_CONNECTED =
            "SELECT bed_id_2 AS connected_bed FROM " + TABLE + " WHERE bed_id_1 = ? " +
            "UNION " +
            "SELECT bed_id_1 AS connected_bed FROM " + TABLE + " WHERE bed_id_2 = ?";

    private static final String SQL_DELETE_FOR_BED =
            "DELETE FROM " + TABLE + " WHERE bed_id_1 = ? OR bed_id_2 = ?";

    public WardTopologyDAO() {}

    /**
     * Inserts an adjacency or air-circulation edge between two beds into the database
     * and mirrors it in the in-memory fallback graph.
     *
     * @param bedId1           the first bed ID
     * @param bedId2           the second bed ID
     * @param relationshipType 'NEIGHBOR' or 'SHARED_AIR'
     * @return {@code true} if successfully persisted or registered
     */
    public boolean insertEdge(long bedId1, long bedId2, String relationshipType) {
        if (bedId1 == bedId2) {
            return false;
        }

        String type = (relationshipType != null && !relationshipType.trim().isEmpty())
                ? relationshipType.trim().toUpperCase() : WardTopologyGraph.RELATION_NEIGHBOR;

        boolean dbSuccess = false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            ps.setLong(1, bedId1);
            ps.setLong(2, bedId2);
            ps.setString(3, type);

            int affected = ps.executeUpdate();
            dbSuccess = (affected > 0);
            if (dbSuccess) {
                LOGGER.info(String.format("Persisted topology edge: Bed #%d <-> Bed #%d (%s)", bedId1, bedId2, type));
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, String.format(
                    "Failed to insert topology edge (%d <-> %d, %s) into DB, storing in fallback graph: %s",
                    bedId1, bedId2, type, e.getMessage()));
        }

        // Always keep in-memory fallback graph synchronized
        inMemoryGraph.addEdge(bedId1, bedId2, type);
        return dbSuccess || true;
    }

    /**
     * Queries {@code ward_topology} and returns a fully populated {@link WardTopologyGraph}.
     * Falls back to the internal memory graph if the database is unreachable or empty.
     *
     * @return populated {@link WardTopologyGraph}
     */
    public WardTopologyGraph loadTopologyGraph() {
        WardTopologyGraph graph = new WardTopologyGraph();
        boolean loadedFromDb = false;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_LOAD_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long bed1 = rs.getLong("bed_id_1");
                long bed2 = rs.getLong("bed_id_2");
                String relType = rs.getString("relationship_type");
                graph.addEdge(bed1, bed2, relType);
                // Also cache into inMemoryGraph
                inMemoryGraph.addEdge(bed1, bed2, relType);
                loadedFromDb = true;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to load topology from database. Using fallback graph: " + e.getMessage());
        }

        if (loadedFromDb) {
            return graph;
        }

        // Return copy of in-memory graph
        WardTopologyGraph fallbackCopy = new WardTopologyGraph();
        for (Map.Entry<Long, Set<Long>> entry : inMemoryGraph.getNeighborEdges().entrySet()) {
            for (Long neighbor : entry.getValue()) {
                fallbackCopy.addEdge(entry.getKey(), neighbor, WardTopologyGraph.RELATION_NEIGHBOR);
            }
        }
        for (Map.Entry<Long, Set<Long>> entry : inMemoryGraph.getSharedAirEdges().entrySet()) {
            for (Long air : entry.getValue()) {
                fallbackCopy.addEdge(entry.getKey(), air, WardTopologyGraph.RELATION_SHARED_AIR);
            }
        }
        return fallbackCopy;
    }

    /**
     * Finds all beds connected to the specified bed (union of neighbors and shared-air).
     *
     * @param bedId the bed identifier
     * @return set of all connected bed IDs
     */
    public Set<Long> findConnectedBeds(long bedId) {
        Set<Long> connected = new HashSet<>();

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_FIND_CONNECTED)) {

            ps.setLong(1, bedId);
            ps.setLong(2, bedId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    connected.add(rs.getLong("connected_bed"));
                }
            }

            if (!connected.isEmpty()) {
                return connected;
            }

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to query connected beds from DB for bed #" + bedId + ": " + e.getMessage());
        }

        // Fallback to in-memory graph
        return inMemoryGraph.getAllAffectedBufferBeds(bedId);
    }

    /**
     * Deletes all topology relationships referencing the specified bed.
     *
     * @param bedId the bed identifier
     * @return number of deleted records
     */
    public int deleteEdgesForBed(long bedId) {
        int deleted = 0;

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE_FOR_BED)) {

            ps.setLong(1, bedId);
            ps.setLong(2, bedId);

            deleted = ps.executeUpdate();
            LOGGER.info("Deleted " + deleted + " topology edges for bed #" + bedId);

        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Failed to delete topology edges for bed #" + bedId + ": " + e.getMessage());
        }

        // Always remove from in-memory fallback
        inMemoryGraph.removeBed(bedId);
        return deleted;
    }
}
