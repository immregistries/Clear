package org.immregistries.clear.service;

import static org.junit.Assert.assertEquals;

import java.util.HashMap;
import java.util.Map;

import org.immregistries.clear.model.JurisdictionAccessRole;
import org.junit.Test;

public class JurisdictionAccessServiceTest {

    private final JurisdictionAccessService jurisdictionAccessService = new JurisdictionAccessService();

    @Test
    public void shouldReturnExplicitOverrideBeforeImplicitHomeAccess() {
        Map<Integer, JurisdictionAccessRole> overrideMap = new HashMap<Integer, JurisdictionAccessRole>();
        overrideMap.put(Integer.valueOf(12), JurisdictionAccessRole.NOT_AUTHORIZED);

        JurisdictionAccessRole accessRole = jurisdictionAccessService.getEffectiveAccessRole(overrideMap,
                Integer.valueOf(12), 12);

        assertEquals(JurisdictionAccessRole.NOT_AUTHORIZED, accessRole);
    }

    @Test
    public void shouldReturnImplicitViewerForHomeJurisdictionWithoutOverride() {
        Map<Integer, JurisdictionAccessRole> overrideMap = new HashMap<Integer, JurisdictionAccessRole>();

        JurisdictionAccessRole accessRole = jurisdictionAccessService.getEffectiveAccessRole(overrideMap,
                Integer.valueOf(25), 25);

        assertEquals(JurisdictionAccessRole.VIEWER, accessRole);
    }

    @Test
    public void shouldReturnExplicitCrossJurisdictionRole() {
        Map<Integer, JurisdictionAccessRole> overrideMap = new HashMap<Integer, JurisdictionAccessRole>();
        overrideMap.put(Integer.valueOf(44), JurisdictionAccessRole.SECONDARY_REPORTER);

        JurisdictionAccessRole accessRole = jurisdictionAccessService.getEffectiveAccessRole(overrideMap,
                Integer.valueOf(10), 44);

        assertEquals(JurisdictionAccessRole.SECONDARY_REPORTER, accessRole);
    }

    @Test
    public void shouldReturnNotAuthorizedForNonHomeJurisdictionWithoutOverride() {
        Map<Integer, JurisdictionAccessRole> overrideMap = new HashMap<Integer, JurisdictionAccessRole>();

        JurisdictionAccessRole accessRole = jurisdictionAccessService.getEffectiveAccessRole(overrideMap,
                Integer.valueOf(10), 99);

        assertEquals(JurisdictionAccessRole.NOT_AUTHORIZED, accessRole);
    }
}