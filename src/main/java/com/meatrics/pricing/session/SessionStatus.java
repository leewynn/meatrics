package com.meatrics.pricing.session;

/**
 * Constants for pricing session statuses
 */
public final class SessionStatus {

    /**
     * Session is being worked on (not yet finalized)
     */
    public static final String IN_PROGRESS = "IN_PROGRESS";

    /**
     * Session has been finalized and is ready for customer reports
     */
    public static final String FINALIZED = "FINALIZED";

    // Private constructor to prevent instantiation
    private SessionStatus() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
