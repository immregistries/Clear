package org.immregistries.clear.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.List;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.immregistries.clear.auth.SessionUser;
import org.immregistries.clear.model.ClearAgreement;
import org.immregistries.clear.model.ClearJurisdictionSharingAgreement;
import org.immregistries.clear.model.ClearUserAgreementAcknowledgement;
import org.immregistries.clear.model.Jurisdiction;

public class ClearAgreementService {

    public static final String AGREEMENT_CODE_PEER_DATA_SHARING = "PEER_DATA_SHARING";
    public static final String NOT_SHARED_LABEL = "Not Shared";

    public static final String DEFAULT_TITLE = "CLEAR Peer Data Sharing Agreement";

    public static final String DEFAULT_TEXT = "By continuing, I understand that CLEAR detailed results are shared within the participating IIS peer community. Data submitted for my jurisdiction may be visible to other participating jurisdictions, with values rounded for reporting. CLEAR may use submitted data to generate aggregate estimates of IIS query and update activity.\n\n"
            + "I understand that detailed peer-level results are provided for internal public health program use and peer comparison, and are not intended for general distribution. Aggregate national estimates may be shared publicly.\n\n"
            + "I understand that CLEAR data is self-reported operational data used to estimate information flow. These values are not audited, verified, or official final counts.";

    public static final String DEFAULT_CHECKBOX_TEXT = "I agree to these terms and understand that accepting will allow my jurisdiction's submitted data to be shown to other participating jurisdictions.";

    public static final String REMINDER_TEXT = "CLEAR peer-level data is shared for internal public health program use and peer comparison. Detailed peer results are not intended for general distribution. Aggregate national estimates may be shared publicly.";

    public ClearAgreement getActiveAgreement(Session session, String agreementCode) {
        Query<ClearAgreement> query = session.createQuery(
                "FROM ClearAgreement WHERE agreementCode = :agreementCode AND active = true ORDER BY versionNumber DESC, effectiveDate DESC, agreementId DESC",
                ClearAgreement.class);
        query.setParameter("agreementCode", agreementCode);
        query.setMaxResults(1);
        List<ClearAgreement> agreements = query.list();
        if (!agreements.isEmpty()) {
            return agreements.get(0);
        }
        if (!AGREEMENT_CODE_PEER_DATA_SHARING.equals(agreementCode)) {
            return null;
        }
        return createDefaultActiveAgreement(session, agreementCode);
    }

    public boolean hasUserAcknowledgedCurrentAgreement(Session session, SessionUser sessionUser, String agreementCode) {
        if (sessionUser == null) {
            return false;
        }
        if (sessionUser.isAdmin()) {
            return true;
        }
        if (sessionUser.getContactId() == null || sessionUser.getJurisdictionId() == null) {
            return false;
        }
        ClearAgreement activeAgreement = getActiveAgreement(session, agreementCode);
        if (activeAgreement == null) {
            return false;
        }
        Query<Long> query = session.createQuery(
                "SELECT count(a.userAgreementAcknowledgementId) FROM ClearUserAgreementAcknowledgement a "
                        + "WHERE a.userId = :userId AND a.jurisdictionId = :jurisdictionId AND a.agreementId = :agreementId",
                Long.class);
        query.setParameter("userId", sessionUser.getContactId().intValue());
        query.setParameter("jurisdictionId", sessionUser.getJurisdictionId().intValue());
        query.setParameter("agreementId", activeAgreement.getAgreementId());
        Long count = query.uniqueResult();
        return count != null && count.longValue() > 0L;
    }

    public boolean hasJurisdictionShared(Session session, int jurisdictionId) {
        Query<Long> query = session.createQuery(
                "SELECT count(a.jurisdictionSharingAgreementId) FROM ClearJurisdictionSharingAgreement a "
                        + "WHERE a.jurisdictionId = :jurisdictionId AND a.active = true",
                Long.class);
        query.setParameter("jurisdictionId", jurisdictionId);
        Long count = query.uniqueResult();
        return count != null && count.longValue() > 0L;
    }

    public boolean acknowledgeAgreement(Session session, SessionUser sessionUser, HttpServletRequest req,
            String agreementCode) {
        if (sessionUser == null || sessionUser.isAdmin()) {
            return true;
        }
        if (sessionUser.getContactId() == null || sessionUser.getJurisdictionId() == null) {
            return false;
        }

        ClearAgreement activeAgreement = getActiveAgreement(session, agreementCode);
        if (activeAgreement == null) {
            return false;
        }

        int userId = sessionUser.getContactId().intValue();
        int jurisdictionId = sessionUser.getJurisdictionId().intValue();

        Query<Long> existingAckQuery = session.createQuery(
                "SELECT count(a.userAgreementAcknowledgementId) FROM ClearUserAgreementAcknowledgement a "
                        + "WHERE a.userId = :userId AND a.jurisdictionId = :jurisdictionId AND a.agreementId = :agreementId",
                Long.class);
        existingAckQuery.setParameter("userId", userId);
        existingAckQuery.setParameter("jurisdictionId", jurisdictionId);
        existingAckQuery.setParameter("agreementId", activeAgreement.getAgreementId());
        Long existingAckCount = existingAckQuery.uniqueResult();
        if (existingAckCount == null || existingAckCount.longValue() == 0L) {
            ClearUserAgreementAcknowledgement acknowledgement = new ClearUserAgreementAcknowledgement();
            acknowledgement.setUserId(userId);
            acknowledgement.setJurisdictionId(jurisdictionId);
            acknowledgement.setAgreementId(activeAgreement.getAgreementId());
            acknowledgement.setAcknowledgedDate(new Date());
            acknowledgement.setAcknowledgedByName(resolveDisplayName(sessionUser));
            acknowledgement.setAcknowledgedByEmail(emptyToUnknown(sessionUser.getEmail()));
            acknowledgement.setIpAddress(resolveClientIp(req));
            acknowledgement.setUserAgent(trimToLength(req == null ? null : req.getHeader("User-Agent"), 500));
            session.save(acknowledgement);
        }

        Query<Long> existingSharingQuery = session.createQuery(
                "SELECT count(a.jurisdictionSharingAgreementId) FROM ClearJurisdictionSharingAgreement a "
                        + "WHERE a.jurisdictionId = :jurisdictionId AND a.active = true",
                Long.class);
        existingSharingQuery.setParameter("jurisdictionId", jurisdictionId);
        Long existingSharingCount = existingSharingQuery.uniqueResult();
        if (existingSharingCount == null || existingSharingCount.longValue() == 0L) {
            ClearJurisdictionSharingAgreement sharingAgreement = new ClearJurisdictionSharingAgreement();
            sharingAgreement.setJurisdictionId(jurisdictionId);
            sharingAgreement.setAgreementId(activeAgreement.getAgreementId());
            sharingAgreement.setFirstAgreedByUserId(userId);
            sharingAgreement.setFirstAgreedByName(resolveDisplayName(sessionUser));
            sharingAgreement.setFirstAgreedByEmail(emptyToUnknown(sessionUser.getEmail()));
            sharingAgreement.setFirstAgreedDate(new Date());
            sharingAgreement.setActive(true);
            session.save(sharingAgreement);
        }
        return true;
    }

    public String getDisplayJurisdictionLabel(Session session, Jurisdiction jurisdiction, SessionUser currentUser) {
        if (jurisdiction == null) {
            return "";
        }
        if (currentUser == null || currentUser.isAdmin()) {
            return jurisdiction.getDisplayLabel();
        }
        if (hasJurisdictionShared(session, jurisdiction.getJurisdictionId())) {
            return jurisdiction.getDisplayLabel();
        }
        return NOT_SHARED_LABEL;
    }

    private ClearAgreement createDefaultActiveAgreement(Session session, String agreementCode) {
        ClearAgreement agreement = new ClearAgreement();
        agreement.setAgreementCode(agreementCode);
        agreement.setVersionNumber(1);
        agreement.setTitle(DEFAULT_TITLE);
        agreement.setAgreementText(DEFAULT_TEXT);
        agreement.setActive(true);
        agreement.setCreatedDate(new Date());
        agreement.setCreatedByUserId(null);
        agreement.setEffectiveDate(new Date());
        session.save(agreement);
        return agreement;
    }

    private String resolveDisplayName(SessionUser sessionUser) {
        if (sessionUser == null) {
            return "Unknown User";
        }
        if (!isBlank(sessionUser.getDisplayName())) {
            return trimToLength(sessionUser.getDisplayName(), 200);
        }
        String first = sessionUser.getFirstName() == null ? "" : sessionUser.getFirstName().trim();
        String last = sessionUser.getLastName() == null ? "" : sessionUser.getLastName().trim();
        String combined = (first + " " + last).trim();
        if (!combined.isEmpty()) {
            return trimToLength(combined, 200);
        }
        return trimToLength(emptyToUnknown(sessionUser.getEmail()), 200);
    }

    private String resolveClientIp(HttpServletRequest req) {
        if (req == null) {
            return null;
        }
        String xForwardedFor = req.getHeader("X-Forwarded-For");
        if (!isBlank(xForwardedFor)) {
            String[] addresses = xForwardedFor.split(",");
            if (addresses.length > 0) {
                return trimToLength(addresses[0].trim(), 120);
            }
        }
        String xRealIp = req.getHeader("X-Real-IP");
        if (!isBlank(xRealIp)) {
            return trimToLength(xRealIp.trim(), 120);
        }
        return trimToLength(req.getRemoteAddr(), 120);
    }

    private String emptyToUnknown(String value) {
        return isBlank(value) ? "unknown@unknown" : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String trimToLength(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLen) {
            return trimmed;
        }
        return trimmed.substring(0, maxLen);
    }
}
