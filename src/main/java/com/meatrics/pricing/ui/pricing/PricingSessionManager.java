package com.meatrics.pricing.ui.pricing;

import com.meatrics.pricing.product.GroupedLineItem;
import com.meatrics.pricing.session.PricingSession;
import com.meatrics.pricing.session.PricingSessionService;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.VaadinSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Manages session persistence operations for pricing sessions.
 * Handles saving/loading sessions to/from database and VaadinSession.
 */
@Component
public class PricingSessionManager {

    private static final Logger log = LoggerFactory.getLogger(PricingSessionManager.class);

    private static final String SESSION_DATA_KEY = "meatrics.pricingSessions.workingData";
    private static final String SESSION_STATE_KEY = "meatrics.pricingSessions.sessionState";
    private static final String SESSION_UNSAVED_CHANGES_KEY = "meatrics.pricingSessions.hasUnsavedChanges";

    private final PricingSessionService pricingSessionService;

    public PricingSessionManager(PricingSessionService pricingSessionService) {
        this.pricingSessionService = pricingSessionService;
    }

    /**
     * Save working data to VaadinSession for persistence across navigation
     */
    public void saveToSession(List<GroupedLineItem> backingList, PricingSession currentSession, boolean hasUnsavedChanges) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) return;

        try {
            if (backingList != null && !backingList.isEmpty()) {
                session.setAttribute(SESSION_DATA_KEY, new ArrayList<>(backingList));
                log.debug("Saved {} items to session", backingList.size());
            } else {
                session.setAttribute(SESSION_DATA_KEY, null);
            }

            if (currentSession != null) {
                session.setAttribute(SESSION_STATE_KEY, currentSession);
            } else {
                session.setAttribute(SESSION_STATE_KEY, null);
            }

            // Save the unsaved changes flag
            session.setAttribute(SESSION_UNSAVED_CHANGES_KEY, hasUnsavedChanges);
            log.debug("Saved session state: currentSession={}, hasUnsavedChanges={}",
                     currentSession != null ? currentSession.getSessionName() : "null", hasUnsavedChanges);
        } catch (Exception e) {
            log.error("Error saving to session", e);
        }
    }

    /**
     * Restore working data from VaadinSession if available
     *
     * @param onRestoreComplete Callback invoked with restored data (backingList, currentSession, hasUnsavedChanges)
     */
    @SuppressWarnings("unchecked")
    public void restoreFromSession(Consumer<SessionData> onRestoreComplete) {
        VaadinSession session = VaadinSession.getCurrent();
        if (session == null) return;

        try {
            // Restore backing list
            List<GroupedLineItem> savedData = (List<GroupedLineItem>) session.getAttribute(SESSION_DATA_KEY);
            PricingSession savedSession = (PricingSession) session.getAttribute(SESSION_STATE_KEY);
            Boolean savedHasUnsavedChanges = (Boolean) session.getAttribute(SESSION_UNSAVED_CHANGES_KEY);

            // Default to false if not set
            boolean hasUnsavedChanges = savedHasUnsavedChanges != null ? savedHasUnsavedChanges : false;

            if (savedData != null && !savedData.isEmpty()) {
                SessionData sessionData = new SessionData(
                    new ArrayList<>(savedData),
                    savedSession,
                    hasUnsavedChanges
                );

                onRestoreComplete.accept(sessionData);
                log.info("Restored {} items from session (hasUnsavedChanges={})", savedData.size(), hasUnsavedChanges);

                // Show notification to user
                String statusText = hasUnsavedChanges ? " - unsaved changes" : "";
                Notification notification = Notification.show(
                    "Working data restored (" + savedData.size() + " items" + statusText + ")",
                    3000,
                    Notification.Position.BOTTOM_END
                );
                notification.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
            } else if (savedSession != null) {
                // Only session state restored, no data
                SessionData sessionData = new SessionData(null, savedSession, hasUnsavedChanges);
                onRestoreComplete.accept(sessionData);
            }
        } catch (Exception e) {
            log.error("Error restoring session data", e);
        }
    }

    /**
     * Perform the actual session save operation to database
     */
    public PricingSession performSave(String sessionName, String notes, List<GroupedLineItem> backingList) {
        try {
            // Log what we're about to save
            long itemsWithRules = backingList.stream()
                    .filter(item -> !item.getAppliedRules().isEmpty())
                    .count();
            long itemsWithManualOverride = backingList.stream()
                    .filter(GroupedLineItem::isManualOverride)
                    .count();

            log.info("Saving session '{}': {} total items, {} with applied rules, {} with manual override",
                     sessionName, backingList.size(), itemsWithRules, itemsWithManualOverride);

            // Save session with all line items
            PricingSession savedSession = pricingSessionService.saveSession(sessionName, backingList);

            // Update notes if provided
            if (notes != null && !notes.trim().isEmpty()) {
                savedSession.setNotes(notes.trim());
            }

            log.info("Saved pricing session: {} with {} items", sessionName, backingList.size());

            return savedSession;

        } catch (Exception e) {
            log.error("Error saving pricing session: " + sessionName, e);
            throw new RuntimeException("Error saving session: " + e.getMessage(), e);
        }
    }

    /**
     * Perform the actual session load operation from database
     */
    public List<GroupedLineItem> performLoad(Long sessionId) {
        try {
            List<GroupedLineItem> loadedItems = pricingSessionService.loadSession(sessionId);
            log.info("Loaded pricing session with {} items", loadedItems.size());
            return loadedItems;
        } catch (Exception e) {
            log.error("Error loading pricing session: " + sessionId, e);
            throw new RuntimeException("Error loading session: " + e.getMessage(), e);
        }
    }

    /**
     * Clear working data from VaadinSession
     */
    public void clearSessionData() {
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null) {
            session.setAttribute(SESSION_DATA_KEY, null);
            session.setAttribute(SESSION_STATE_KEY, null);
            session.setAttribute(SESSION_UNSAVED_CHANGES_KEY, null);
        }
    }

    /**
     * Check if a session name already exists
     */
    public boolean sessionNameExists(String sessionName) {
        return pricingSessionService.sessionNameExists(sessionName);
    }

    /**
     * Get all available sessions
     */
    public List<PricingSession> getAllSessions() {
        return pricingSessionService.getAllSessions();
    }

    /**
     * Delete a session
     */
    public void deleteSession(Long sessionId) {
        pricingSessionService.deleteSession(sessionId);
        log.info("Deleted pricing session: {}", sessionId);
    }

    /**
     * Data class to hold restored session data
     */
    public static class SessionData {
        private final List<GroupedLineItem> backingList;
        private final PricingSession currentSession;
        private final boolean hasUnsavedChanges;

        public SessionData(List<GroupedLineItem> backingList, PricingSession currentSession, boolean hasUnsavedChanges) {
            this.backingList = backingList;
            this.currentSession = currentSession;
            this.hasUnsavedChanges = hasUnsavedChanges;
        }

        public List<GroupedLineItem> getBackingList() {
            return backingList;
        }

        public PricingSession getCurrentSession() {
            return currentSession;
        }

        public boolean hasUnsavedChanges() {
            return hasUnsavedChanges;
        }
    }
}
