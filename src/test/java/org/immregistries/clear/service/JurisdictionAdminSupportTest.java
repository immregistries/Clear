package org.immregistries.clear.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.immregistries.clear.service.JurisdictionAdminSupport.BulkJurisdictionUpdate;
import org.immregistries.clear.service.JurisdictionAdminSupport.BulkJurisdictionUpdateResult;
import org.immregistries.clear.service.JurisdictionAdminSupport.JurisdictionEditRow;
import org.immregistries.clear.service.JurisdictionAdminSupport.JurisdictionEditValidationResult;
import org.junit.Test;

public class JurisdictionAdminSupportTest {

    private final JurisdictionAdminSupport jurisdictionAdminSupport = new JurisdictionAdminSupport();

    @Test
    public void shouldValidateJurisdictionRows() {
        List<JurisdictionEditRow> rows = new ArrayList<JurisdictionEditRow>();
        rows.add(new JurisdictionEditRow(Integer.valueOf(1), "Alaska", "AK"));
        rows.add(new JurisdictionEditRow(Integer.valueOf(2), "Arizona", "AZ"));

        JurisdictionEditValidationResult validationResult = jurisdictionAdminSupport.validateJurisdictionRows(rows);

        assertFalse(validationResult.hasErrors());
    }

    @Test
    public void shouldRejectDuplicateMapLinksInJurisdictionRows() {
        List<JurisdictionEditRow> rows = new ArrayList<JurisdictionEditRow>();
        rows.add(new JurisdictionEditRow(Integer.valueOf(1), "Alaska", "AK"));
        rows.add(new JurisdictionEditRow(Integer.valueOf(2), "Another Alaska", "ak"));

        JurisdictionEditValidationResult validationResult = jurisdictionAdminSupport.validateJurisdictionRows(rows);

        assertTrue(validationResult.hasErrors());
        assertTrue(validationResult.getErrors().get(0).contains("duplicated"));
    }

    @Test
    public void shouldParseBulkJurisdictionUpdatesAndSkipHeaderRow() {
        BulkJurisdictionUpdateResult parseResult = jurisdictionAdminSupport.parseBulkJurisdictionUpdates(
                "test_participant_label,map_link\nAK VacTraAK, AK\nArizona VacTraAZ, AZ");

        assertFalse(parseResult.hasErrors());
        assertEquals(2, parseResult.getRows().size());
        BulkJurisdictionUpdate firstRow = parseResult.getRows().get(0);
        assertEquals(2, firstRow.getLineNumber());
        assertEquals("AK VacTraAK", firstRow.getDisplayLabel());
        assertEquals("AK", firstRow.getMapLink());
    }

    @Test
    public void shouldRejectDuplicateMapLinksInBulkInput() {
        BulkJurisdictionUpdateResult parseResult = jurisdictionAdminSupport.parseBulkJurisdictionUpdates(
                "Alaska, AK\nAnother Alaska, ak");

        assertTrue(parseResult.hasErrors());
        assertTrue(parseResult.getErrors().get(0).contains("Duplicate mapLink"));
    }
}