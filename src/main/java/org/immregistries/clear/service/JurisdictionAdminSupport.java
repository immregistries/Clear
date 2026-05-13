package org.immregistries.clear.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class JurisdictionAdminSupport {

    public JurisdictionEditValidationResult validateJurisdictionRows(List<JurisdictionEditRow> rows) {
        List<String> errors = new ArrayList<String>();
        Set<String> mapLinks = new HashSet<String>();
        if (rows == null || rows.isEmpty()) {
            errors.add("No jurisdictions were submitted.");
            return new JurisdictionEditValidationResult(errors);
        }

        for (int i = 0; i < rows.size(); i++) {
            JurisdictionEditRow row = rows.get(i);
            int rowNumber = i + 1;
            if (row.getJurisdictionId() == null || row.getJurisdictionId().intValue() <= 0) {
                errors.add("Row " + rowNumber + ": Jurisdiction id is required.");
            }

            String displayLabel = normalize(row.getDisplayLabel());
            if (displayLabel == null) {
                errors.add("Row " + rowNumber + ": Display label is required.");
            }

            String mapLink = normalize(row.getMapLink());
            if (mapLink == null) {
                errors.add("Row " + rowNumber + ": Map link is required.");
                continue;
            }

            String mapLinkKey = mapLink.toLowerCase(Locale.ROOT);
            if (!mapLinks.add(mapLinkKey)) {
                errors.add("Row " + rowNumber + ": Map link '" + mapLink + "' is duplicated in the form.");
            }
        }

        return new JurisdictionEditValidationResult(errors);
    }

    public BulkJurisdictionUpdateResult parseBulkJurisdictionUpdates(String bulkText) {
        List<BulkJurisdictionUpdate> rows = new ArrayList<BulkJurisdictionUpdate>();
        List<String> errors = new ArrayList<String>();
        if (bulkText == null) {
            errors.add("Bulk update text is required.");
            return new BulkJurisdictionUpdateResult(rows, errors);
        }

        String[] lines = bulkText.split("\\R");
        boolean sawDataRow = false;
        Set<String> mapLinks = new HashSet<String>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineNumber = i + 1;
            if (line.trim().isEmpty()) {
                continue;
            }

            List<String> columns = parseLine(line);
            if (!sawDataRow && looksLikeHeader(columns)) {
                continue;
            }
            sawDataRow = true;

            if (columns.size() != 2) {
                errors.add("Line " + lineNumber + ": Expected 2 columns but found " + columns.size() + ".");
                continue;
            }

            String displayLabel = normalize(columns.get(0));
            String mapLink = normalize(columns.get(1));
            if (displayLabel == null || mapLink == null) {
                errors.add("Line " + lineNumber + ": displayLabel and mapLink are required.");
                continue;
            }

            String mapLinkKey = mapLink.toLowerCase(Locale.ROOT);
            if (!mapLinks.add(mapLinkKey)) {
                errors.add("Line " + lineNumber + ": Duplicate mapLink '" + mapLink + "' in bulk input.");
                continue;
            }

            rows.add(new BulkJurisdictionUpdate(lineNumber, displayLabel, mapLink));
        }

        if (!sawDataRow && errors.isEmpty()) {
            errors.add("Bulk update text did not contain any data rows.");
        }

        return new BulkJurisdictionUpdateResult(rows, errors);
    }

    private boolean looksLikeHeader(List<String> columns) {
        if (columns == null || columns.size() != 2) {
            return false;
        }
        String first = normalize(columns.get(0));
        String second = normalize(columns.get(1));
        if (first == null || second == null) {
            return false;
        }
        return ("test_participant_label".equalsIgnoreCase(first) && "map_link".equalsIgnoreCase(second))
                || ("displaylabel".equalsIgnoreCase(first) && "maplink".equalsIgnoreCase(second));
    }

    private List<String> parseLine(String line) {
        if (line.contains("\t") && !line.contains(",")) {
            List<String> fields = new ArrayList<String>();
            String[] parts = line.split("\t", -1);
            for (String part : parts) {
                fields.add(part);
            }
            return fields;
        }
        return parseCsvLine(line);
    }

    private List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (ch == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        fields.add(current.toString());
        return fields;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public static class JurisdictionEditRow {
        private final Integer jurisdictionId;
        private final String displayLabel;
        private final String mapLink;

        public JurisdictionEditRow(Integer jurisdictionId, String displayLabel, String mapLink) {
            this.jurisdictionId = jurisdictionId;
            this.displayLabel = displayLabel;
            this.mapLink = mapLink;
        }

        public Integer getJurisdictionId() {
            return jurisdictionId;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public String getMapLink() {
            return mapLink;
        }
    }

    public static class BulkJurisdictionUpdate {
        private final int lineNumber;
        private final String displayLabel;
        private final String mapLink;

        public BulkJurisdictionUpdate(int lineNumber, String displayLabel, String mapLink) {
            this.lineNumber = lineNumber;
            this.displayLabel = displayLabel;
            this.mapLink = mapLink;
        }

        public int getLineNumber() {
            return lineNumber;
        }

        public String getDisplayLabel() {
            return displayLabel;
        }

        public String getMapLink() {
            return mapLink;
        }
    }

    public static class JurisdictionEditValidationResult {
        private final List<String> errors;

        public JurisdictionEditValidationResult(List<String> errors) {
            this.errors = errors;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }

    public static class BulkJurisdictionUpdateResult {
        private final List<BulkJurisdictionUpdate> rows;
        private final List<String> errors;

        public BulkJurisdictionUpdateResult(List<BulkJurisdictionUpdate> rows, List<String> errors) {
            this.rows = rows;
            this.errors = errors;
        }

        public List<BulkJurisdictionUpdate> getRows() {
            return rows;
        }

        public List<String> getErrors() {
            return errors;
        }

        public boolean hasErrors() {
            return errors != null && !errors.isEmpty();
        }
    }
}