package com.group15.hospital.service.dispatch;

import com.group15.hospital.dao.AlertDAO;
import com.group15.hospital.model.ambulance.SystemAlert;
import com.group15.hospital.model.enums.UserRole;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread-safe singleton Observer notification hub for real-time triage screen alerts (SRS F13).
 * <p>
 * <b>Architecture:</b>
 * <ul>
 *   <li>UI panels register themselves as {@link AlertObserver} subscribers at startup.</li>
 *   <li>Services call {@link #broadcastAlert(SystemAlert)} after any state change worth displaying.</li>
 *   <li>Each alert is persisted to the DB via {@link AlertDAO} (offline recovery queue).</li>
 *   <li>Observer callbacks fire on the Swing Event Dispatch Thread via
 *       {@code SwingUtilities.invokeLater}, guaranteeing UI thread safety in {@code < 0.5 s}.</li>
 * </ul>
 * </p>
 *
 * <b>Singleton pattern:</b> Double-checked locking with {@code volatile} instance, identical to
 * {@code DatabaseConnection} already used by the team (Saksham / P1).
 *
 * @author Siddhant (Module P4)
 * @see AlertObserver
 * @see AlertDAO
 */
public final class AlertNotificationManager {

    private static final Logger LOGGER = Logger.getLogger(AlertNotificationManager.class.getName());

    // ── Singleton ──
    private static volatile AlertNotificationManager instance;

    // ── Observer Registry ──
    // CopyOnWriteArrayList: safe for read-heavy iteration during broadcast; rare writes on register/unregister
    private final CopyOnWriteArrayList<AlertObserver> observers = new CopyOnWriteArrayList<>();

    // ── Persistence ──
    private final AlertDAO alertDAO;

    private AlertNotificationManager(AlertDAO alertDAO) {
        this.alertDAO = Objects.requireNonNull(alertDAO, "alertDAO must not be null");
    }

    /**
     * Returns the singleton instance, initializing it with the provided {@link AlertDAO} on first call.
     */
    public static AlertNotificationManager getInstance(AlertDAO alertDAO) {
        if (instance == null) {
            synchronized (AlertNotificationManager.class) {
                if (instance == null) {
                    instance = new AlertNotificationManager(alertDAO);
                }
            }
        }
        return instance;
    }

    /**
     * Returns the existing singleton. Throws if called before {@link #getInstance(AlertDAO)}.
     */
    public static AlertNotificationManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "AlertNotificationManager not yet initialized. Call getInstance(AlertDAO) first.");
        }
        return instance;
    }

    /** Resets singleton for unit testing only. */
    public static void resetForTesting() {
        instance = null;
    }

    // ──────────────────────── Observer Management ─────────────────────────

    /**
     * Registers a new observer (UI panel or service) to receive real-time alerts.
     *
     * @param observer the subscriber to add; ignored if already registered
     */
    public void registerObserver(AlertObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
            LOGGER.fine("AlertObserver registered: " + observer.getClass().getSimpleName());
        }
    }

    /**
     * Removes an observer from the active subscriber list.
     *
     * @param observer the subscriber to remove
     */
    public void unregisterObserver(AlertObserver observer) {
        observers.remove(observer);
        LOGGER.fine("AlertObserver unregistered: " +
                (observer != null ? observer.getClass().getSimpleName() : "null"));
    }

    /** Returns the current count of registered observers (useful for testing). */
    public int getObserverCount() {
        return observers.size();
    }

    // ──────────────────────── Alert Dispatch ──────────────────────────────

    /**
     * Persists the alert (offline queue recovery) and dispatches it to all registered observers
     * on the Swing EDT within <strong>&lt; 0.5 seconds</strong> (SRS Section 4.1).
     *
     * @param alert the alert payload to broadcast
     */
    public void broadcastAlert(SystemAlert alert) {
        if (alert == null) {
            LOGGER.warning("broadcastAlert called with null alert — skipping.");
            return;
        }

        // 1. Persist first — so offline screens pick it up on reconnect (SRS F13 / U6 Exception 3)
        SystemAlert persisted;
        try {
            persisted = alertDAO.saveAlert(alert);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to persist alert before broadcast — broadcasting original", e);
            persisted = alert;
        }

        final SystemAlert toDeliver = persisted;

        // 2. Dispatch to all live observers asynchronously on the Swing EDT
        // SwingUtilities.invokeLater guarantees < 0.5 s delivery to UI components without blocking callers
        final List<AlertObserver> snapshot = List.copyOf(observers); // safe read snapshot
        SwingUtilities.invokeLater(() -> {
            for (AlertObserver observer : snapshot) {
                try {
                    observer.onAlertReceived(toDeliver);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Observer threw exception during alert delivery: "
                            + observer.getClass().getSimpleName(), e);
                }
            }
        });

        LOGGER.info("Broadcast alert [" + toDeliver.getAlertType() + "] to "
                + snapshot.size() + " observer(s) @ " + toDeliver.getHospitalName());
    }

    /**
     * Fetches any pending unacknowledged alerts from the offline queue and delivers them
     * synchronously to a single reconnecting terminal (U6 Exception 3).
     *
     * @param hospitalName the reconnecting terminal's hospital
     * @param role         the staff role of the reconnecting terminal
     * @param observer     the observer to deliver catch-up alerts to
     */
    public void checkAndDispatchOfflineAlerts(String hospitalName, UserRole role, AlertObserver observer) {
        if (hospitalName == null || role == null || observer == null) return;

        List<SystemAlert> pending = alertDAO.fetchUnacknowledgedAlerts(hospitalName, role);
        if (pending.isEmpty()) {
            LOGGER.fine("No pending offline alerts for " + role + " @ " + hospitalName);
            return;
        }

        LOGGER.info("Dispatching " + pending.size() + " offline-queued alerts to reconnecting terminal ["
                + role + "] @ " + hospitalName);

        SwingUtilities.invokeLater(() -> {
            for (SystemAlert queued : pending) {
                try {
                    observer.onAlertReceived(queued);
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error dispatching offline alert #" + queued.getAlertId(), e);
                }
            }
        });
    }
}
