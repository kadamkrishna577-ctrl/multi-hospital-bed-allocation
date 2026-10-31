package com.group15.hospital.service.dispatch;

import com.group15.hospital.model.ambulance.SystemAlert;

/**
 * Observer interface for receiving real-time system alerts on triage terminals (SRS F13 / Use Cases U5, U6).
 * <p>
 * Any UI panel or service that needs instant notifications — without manual screen refreshing — must
 * implement this interface and register itself with {@link AlertNotificationManager}.
 * </p>
 *
 * @author Siddhant (Module P4)
 * @see AlertNotificationManager
 * @see SystemAlert
 */
public interface AlertObserver {

    /**
     * Invoked asynchronously via {@code SwingUtilities.invokeLater} whenever a new alert is broadcast.
     *
     * @param alert the system alert payload containing type, role, hospital, message, and bed ID
     */
    void onAlertReceived(SystemAlert alert);
}
