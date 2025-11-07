package com.meatrics.pricing;

import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.meatrics.generated.Tables.PRICING_SESSIONS;

/**
 * Repository for pricing session data access
 */
@Repository
public class PricingSessionRepository {

    private final DSLContext dsl;

    public PricingSessionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    /**
     * Find all sessions ordered by last modified date (most recent first)
     */
    public List<PricingSession> findAll() {
        return dsl.selectFrom(PRICING_SESSIONS)
                .orderBy(PRICING_SESSIONS.LAST_MODIFIED_DATE.desc())
                .fetch(this::mapToPricingSession);
    }

    /**
     * Find session by ID
     */
    public Optional<PricingSession> findById(Long id) {
        return dsl.selectFrom(PRICING_SESSIONS)
                .where(PRICING_SESSIONS.ID.eq(id))
                .fetchOptional(this::mapToPricingSession);
    }

    /**
     * Find session by session name
     */
    public Optional<PricingSession> findBySessionName(String sessionName) {
        return dsl.selectFrom(PRICING_SESSIONS)
                .where(PRICING_SESSIONS.SESSION_NAME.eq(sessionName))
                .fetchOptional(this::mapToPricingSession);
    }

    /**
     * Save or update a pricing session
     */
    public PricingSession save(PricingSession session) {
        if (session.getId() == null) {
            // Insert new session
            Long newId = dsl.insertInto(PRICING_SESSIONS)
                    .set(PRICING_SESSIONS.SESSION_NAME, session.getSessionName())
                    .set(PRICING_SESSIONS.STATUS, session.getStatus() != null ? session.getStatus() : "IN_PROGRESS")
                    .set(PRICING_SESSIONS.NOTES, session.getNotes())
                    .returningResult(PRICING_SESSIONS.ID)
                    .fetchOne()
                    .value1();

            session.setId(newId);
            // Fetch the complete record to get default timestamps
            return findById(newId).orElse(session);
        } else {
            // Update existing session
            dsl.update(PRICING_SESSIONS)
                    .set(PRICING_SESSIONS.SESSION_NAME, session.getSessionName())
                    .set(PRICING_SESSIONS.LAST_MODIFIED_DATE, LocalDateTime.now())
                    .set(PRICING_SESSIONS.STATUS, session.getStatus())
                    .set(PRICING_SESSIONS.NOTES, session.getNotes())
                    .where(PRICING_SESSIONS.ID.eq(session.getId()))
                    .execute();

            return findById(session.getId()).orElse(session);
        }
    }

    /**
     * Delete session by ID
     */
    public void deleteById(Long id) {
        dsl.deleteFrom(PRICING_SESSIONS)
                .where(PRICING_SESSIONS.ID.eq(id))
                .execute();
    }

    /**
     * Check if session name exists
     */
    public boolean existsBySessionName(String sessionName) {
        return dsl.fetchExists(
                dsl.selectFrom(PRICING_SESSIONS)
                        .where(PRICING_SESSIONS.SESSION_NAME.eq(sessionName))
        );
    }

    private PricingSession mapToPricingSession(org.jooq.Record record) {
        PricingSession session = new PricingSession();
        session.setId(record.get(PRICING_SESSIONS.ID));
        session.setSessionName(record.get(PRICING_SESSIONS.SESSION_NAME));
        session.setCreatedDate(record.get(PRICING_SESSIONS.CREATED_DATE));
        session.setLastModifiedDate(record.get(PRICING_SESSIONS.LAST_MODIFIED_DATE));
        session.setStatus(record.get(PRICING_SESSIONS.STATUS));
        session.setNotes(record.get(PRICING_SESSIONS.NOTES));
        return session;
    }
}
