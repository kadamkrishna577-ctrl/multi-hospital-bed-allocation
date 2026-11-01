package com.group15.hospital.model.isolation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory graph model representing ward bed adjacency and air circulation topology.
 * <p>
 * Supports infection containment (SRS F12 &amp; Use Case U4) by tracking:
 * <ul>
 *   <li><b>Direct neighbors:</b> Physically adjacent beds sharing immediate room space (relationship_type = 'NEIGHBOR').</li>
 *   <li><b>Shared-air beds:</b> Beds sharing HVAC / airflow circulation without physical adjacency (relationship_type = 'SHARED_AIR').</li>
 * </ul>
 * All edges are strictly bidirectional: if bed A is connected to bed B, bed B is connected to bed A.
 * </p>
 *
 * @author Jatin (Module P3)
 */
public class WardTopologyGraph {

    public static final String RELATION_NEIGHBOR = "NEIGHBOR";
    public static final String RELATION_SHARED_AIR = "SHARED_AIR";

    /**
     * Adjacency map for direct physical bed neighbors (relationship_type = 'NEIGHBOR').
     * Maps bedId -> Set of neighboring bedIds.
     */
    private final Map<Long, Set<Long>> neighborEdges = new ConcurrentHashMap<>();

    /**
     * Adjacency map for shared HVAC / air circulation zones (relationship_type = 'SHARED_AIR').
     * Maps bedId -> Set of beds sharing air ventilation.
     */
    private final Map<Long, Set<Long>> sharedAirEdges = new ConcurrentHashMap<>();

    public WardTopologyGraph() {}

    /**
     * Adds an undirected edge between two beds with the given relationship type.
     * Both bed1 -> bed2 and bed2 -> bed1 connections are stored.
     *
     * @param bed1             the first bed ID
     * @param bed2             the second bed ID
     * @param relationshipType 'NEIGHBOR' or 'SHARED_AIR' (case-insensitive)
     */
    public void addEdge(long bed1, long bed2, String relationshipType) {
        if (bed1 == bed2) {
            return; // A bed cannot be connected to itself
        }

        String type = (relationshipType != null) ? relationshipType.trim().toUpperCase() : RELATION_NEIGHBOR;

        if (RELATION_SHARED_AIR.equals(type)) {
            sharedAirEdges.computeIfAbsent(bed1, k -> ConcurrentHashMap.newKeySet()).add(bed2);
            sharedAirEdges.computeIfAbsent(bed2, k -> ConcurrentHashMap.newKeySet()).add(bed1);
        } else {
            // Default to NEIGHBOR
            neighborEdges.computeIfAbsent(bed1, k -> ConcurrentHashMap.newKeySet()).add(bed2);
            neighborEdges.computeIfAbsent(bed2, k -> ConcurrentHashMap.newKeySet()).add(bed1);
        }
    }

    /**
     * Returns an unmodifiable set of direct physical neighbors for the given bed.
     *
     * @param bedId the bed identifier
     * @return set of direct neighbor bed IDs
     */
    public Set<Long> getDirectNeighbors(long bedId) {
        Set<Long> set = neighborEdges.get(bedId);
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(set));
    }

    /**
     * Returns an unmodifiable set of beds that share HVAC/air circulation with the given bed.
     *
     * @param bedId the bed identifier
     * @return set of shared air bed IDs
     */
    public Set<Long> getSharedAirBeds(long bedId) {
        Set<Long> set = sharedAirEdges.get(bedId);
        if (set == null || set.isEmpty()) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(new HashSet<>(set));
    }

    /**
     * Returns the union of all direct physical neighbors and shared-air beds for the given bed.
     * These represent all beds that must be locked into {@code RESTRICTED_BUFFER} state when
     * an airborne or contagious patient occupies this bed.
     *
     * @param bedId the bed identifier
     * @return set of all affected buffer bed IDs (excluding the queried bed itself)
     */
    public Set<Long> getAllAffectedBufferBeds(long bedId) {
        Set<Long> allAffected = new HashSet<>();
        Set<Long> neighbors = neighborEdges.get(bedId);
        if (neighbors != null) {
            allAffected.addAll(neighbors);
        }
        Set<Long> sharedAir = sharedAirEdges.get(bedId);
        if (sharedAir != null) {
            allAffected.addAll(sharedAir);
        }
        allAffected.remove(bedId);
        return Collections.unmodifiableSet(allAffected);
    }

    /**
     * Removes all edge connections involving the specified bed ID.
     *
     * @param bedId the bed identifier to detach
     */
    public void removeBed(long bedId) {
        Set<Long> direct = neighborEdges.remove(bedId);
        if (direct != null) {
            for (Long neighbor : direct) {
                Set<Long> backEdges = neighborEdges.get(neighbor);
                if (backEdges != null) {
                    backEdges.remove(bedId);
                }
            }
        }

        Set<Long> air = sharedAirEdges.remove(bedId);
        if (air != null) {
            for (Long partner : air) {
                Set<Long> backEdges = sharedAirEdges.get(partner);
                if (backEdges != null) {
                    backEdges.remove(bedId);
                }
            }
        }
    }

    /**
     * Returns the complete set of all bed IDs present in this topology graph.
     *
     * @return set of all registered bed IDs
     */
    public Set<Long> getAllBeds() {
        Set<Long> all = new HashSet<>(neighborEdges.keySet());
        all.addAll(sharedAirEdges.keySet());
        return Collections.unmodifiableSet(all);
    }

    /**
     * Clears all topology edges.
     */
    public void clear() {
        neighborEdges.clear();
        sharedAirEdges.clear();
    }

    /**
     * Read-only view of neighbor edges map.
     */
    public Map<Long, Set<Long>> getNeighborEdges() {
        return Collections.unmodifiableMap(neighborEdges);
    }

    /**
     * Read-only view of shared air edges map.
     */
    public Map<Long, Set<Long>> getSharedAirEdges() {
        return Collections.unmodifiableMap(sharedAirEdges);
    }

    @Override
    public String toString() {
        return String.format("WardTopologyGraph[totalBeds=%d, neighborNodes=%d, sharedAirNodes=%d]",
                getAllBeds().size(), neighborEdges.size(), sharedAirEdges.size());
    }
}
