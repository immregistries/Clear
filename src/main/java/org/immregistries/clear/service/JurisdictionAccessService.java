package org.immregistries.clear.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.immregistries.clear.auth.SessionUser;
import org.immregistries.clear.model.ContactJurisdictionAccess;
import org.immregistries.clear.model.Jurisdiction;
import org.immregistries.clear.model.JurisdictionAccessRole;

public class JurisdictionAccessService {

    public List<Jurisdiction> getAccessibleJurisdictions(Session session, SessionUser sessionUser) {
        Query<Jurisdiction> query = session.createQuery("FROM Jurisdiction ORDER BY displayLabel", Jurisdiction.class);
        return getAccessibleJurisdictions(session, sessionUser, query.list());
    }

    public List<Jurisdiction> getAccessibleJurisdictions(Session session, SessionUser sessionUser,
            List<Jurisdiction> allJurisdictions) {
        if (sessionUser == null) {
            return new ArrayList<Jurisdiction>();
        }
        if (sessionUser.isAdmin()) {
            return allJurisdictions;
        }

        List<Jurisdiction> accessibleJurisdictions = new ArrayList<Jurisdiction>();
        Map<Integer, JurisdictionAccessRole> overrideMap = loadOverrideMap(session, sessionUser.getContactId());
        for (Jurisdiction jurisdiction : allJurisdictions) {
            JurisdictionAccessRole accessRole = getEffectiveAccessRole(overrideMap, sessionUser.getJurisdictionId(),
                    jurisdiction.getJurisdictionId());
            if (accessRole != JurisdictionAccessRole.NOT_AUTHORIZED) {
                accessibleJurisdictions.add(jurisdiction);
            }
        }
        return accessibleJurisdictions;
    }

    public JurisdictionAccessRole getEffectiveAccessRole(Session session, SessionUser sessionUser, int jurisdictionId) {
        if (sessionUser == null) {
            return JurisdictionAccessRole.NOT_AUTHORIZED;
        }
        if (sessionUser.isAdmin()) {
            return JurisdictionAccessRole.PRIMARY_REPORTER;
        }
        Map<Integer, JurisdictionAccessRole> overrideMap = loadOverrideMap(session, sessionUser.getContactId());
        return getEffectiveAccessRole(overrideMap, sessionUser.getJurisdictionId(), jurisdictionId);
    }

    public boolean canView(Session session, SessionUser sessionUser, int jurisdictionId) {
        return getEffectiveAccessRole(session, sessionUser, jurisdictionId) != JurisdictionAccessRole.NOT_AUTHORIZED;
    }

    public boolean canEdit(Session session, SessionUser sessionUser, int jurisdictionId) {
        return canView(session, sessionUser, jurisdictionId);
    }

    public boolean startsInEditMode(Session session, SessionUser sessionUser, int jurisdictionId) {
        if (sessionUser != null && sessionUser.isAdmin()) {
            return true;
        }
        JurisdictionAccessRole accessRole = getEffectiveAccessRole(session, sessionUser, jurisdictionId);
        return accessRole == JurisdictionAccessRole.PRIMARY_REPORTER
                || accessRole == JurisdictionAccessRole.SECONDARY_REPORTER;
    }

    JurisdictionAccessRole getEffectiveAccessRole(Map<Integer, JurisdictionAccessRole> overrideMap,
            Integer homeJurisdictionId, int jurisdictionId) {
        JurisdictionAccessRole explicitRole = overrideMap.get(jurisdictionId);
        if (explicitRole != null) {
            return explicitRole;
        }
        if (homeJurisdictionId != null && homeJurisdictionId.intValue() == jurisdictionId) {
            return JurisdictionAccessRole.VIEWER;
        }
        return JurisdictionAccessRole.NOT_AUTHORIZED;
    }

    private Map<Integer, JurisdictionAccessRole> loadOverrideMap(Session session, Integer contactId) {
        Map<Integer, JurisdictionAccessRole> overrideMap = new HashMap<Integer, JurisdictionAccessRole>();
        if (contactId == null) {
            return overrideMap;
        }
        Query<ContactJurisdictionAccess> query = session.createQuery(
                "FROM ContactJurisdictionAccess WHERE contactId = :contactId", ContactJurisdictionAccess.class);
        query.setParameter("contactId", contactId);
        for (ContactJurisdictionAccess access : query.list()) {
            overrideMap.put(access.getJurisdictionId(), access.getAccessRole());
        }
        return overrideMap;
    }
}