package org.immregistries.clear.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.hibernate.Session;
import org.hibernate.query.Query;
import org.immregistries.clear.model.EntryForInterop;
import org.immregistries.clear.model.Jurisdiction;

public class DashboardEstimateService {

    public static final int MIN_REPORTING_JURISDICTIONS = 4;
    public static final int MIN_REPORTERS_FOR_YEARLY = 5;
    public static final int MIN_QUALIFYING_MONTHS_FOR_YEARLY = 10;

    public static class MonthlyEstimateRow {
        private final String monthLabel;
        private final int reportingCount;
        private final Long conservativeEstimate;
        private final Long centralEstimate;
        private final Long highEstimate;
        private final boolean enoughData;

        public MonthlyEstimateRow(String monthLabel, int reportingCount, Long conservativeEstimate,
                Long centralEstimate, Long highEstimate, boolean enoughData) {
            this.monthLabel = monthLabel;
            this.reportingCount = reportingCount;
            this.conservativeEstimate = conservativeEstimate;
            this.centralEstimate = centralEstimate;
            this.highEstimate = highEstimate;
            this.enoughData = enoughData;
        }

        public String getMonthLabel() {
            return monthLabel;
        }

        public int getReportingCount() {
            return reportingCount;
        }

        public Long getConservativeEstimate() {
            return conservativeEstimate;
        }

        public Long getCentralEstimate() {
            return centralEstimate;
        }

        public Long getHighEstimate() {
            return highEstimate;
        }

        public boolean isEnoughData() {
            return enoughData;
        }
    }

    public static class YearlyEstimate {
        private final YearMonth reportingWindowStart;
        private final YearMonth reportingWindowEnd;
        private final Long lowEstimateAnnual;
        private final Long operationalEstimateAnnual;
        private final Long highEstimateAnnual;
        private final boolean hasEnoughData;

        public YearlyEstimate(YearMonth reportingWindowStart, YearMonth reportingWindowEnd,
                Long lowEstimateAnnual, Long operationalEstimateAnnual, Long highEstimateAnnual,
                boolean hasEnoughData) {
            this.reportingWindowStart = reportingWindowStart;
            this.reportingWindowEnd = reportingWindowEnd;
            this.lowEstimateAnnual = lowEstimateAnnual;
            this.operationalEstimateAnnual = operationalEstimateAnnual;
            this.highEstimateAnnual = highEstimateAnnual;
            this.hasEnoughData = hasEnoughData;
        }

        public YearMonth getReportingWindowStart() {
            return reportingWindowStart;
        }

        public YearMonth getReportingWindowEnd() {
            return reportingWindowEnd;
        }

        public Long getLowEstimateAnnual() {
            return lowEstimateAnnual;
        }

        public Long getOperationalEstimateAnnual() {
            return operationalEstimateAnnual;
        }

        public Long getHighEstimateAnnual() {
            return highEstimateAnnual;
        }

        public boolean isHasEnoughData() {
            return hasEnoughData;
        }
    }

    public static class DashboardEstimateViewModel {
        private final int selectedYear;
        private final List<Integer> availableYears;
        private final List<MonthlyEstimateRow> updateRows;
        private final List<MonthlyEstimateRow> queryRows;
        private final YearlyEstimate updateYearlyEstimate;
        private final YearlyEstimate queryYearlyEstimate;

        public DashboardEstimateViewModel(int selectedYear, List<Integer> availableYears,
                List<MonthlyEstimateRow> updateRows, List<MonthlyEstimateRow> queryRows,
                YearlyEstimate updateYearlyEstimate, YearlyEstimate queryYearlyEstimate) {
            this.selectedYear = selectedYear;
            this.availableYears = availableYears;
            this.updateRows = updateRows;
            this.queryRows = queryRows;
            this.updateYearlyEstimate = updateYearlyEstimate;
            this.queryYearlyEstimate = queryYearlyEstimate;
        }

        public int getSelectedYear() {
            return selectedYear;
        }

        public List<Integer> getAvailableYears() {
            return availableYears;
        }

        public List<MonthlyEstimateRow> getUpdateRows() {
            return updateRows;
        }

        public List<MonthlyEstimateRow> getQueryRows() {
            return queryRows;
        }

        public YearlyEstimate getUpdateYearlyEstimate() {
            return updateYearlyEstimate;
        }

        public YearlyEstimate getQueryYearlyEstimate() {
            return queryYearlyEstimate;
        }
    }

    static class JurisdictionMonthlyCounts {
        private Jurisdiction jurisdiction;
        private int updates;
        private int queries;

        JurisdictionMonthlyCounts(Jurisdiction jurisdiction) {
            this.jurisdiction = jurisdiction;
        }
    }

    static class MetricEstimate {
        private final boolean enoughData;
        private final Long conservativeEstimate;
        private final Long centralEstimate;
        private final Long highEstimate;

        MetricEstimate(boolean enoughData, Long conservativeEstimate, Long centralEstimate, Long highEstimate) {
            this.enoughData = enoughData;
            this.conservativeEstimate = conservativeEstimate;
            this.centralEstimate = centralEstimate;
            this.highEstimate = highEstimate;
        }

        boolean isEnoughData() {
            return enoughData;
        }

        Long getConservativeEstimate() {
            return conservativeEstimate;
        }

        Long getCentralEstimate() {
            return centralEstimate;
        }

        Long getHighEstimate() {
            return highEstimate;
        }
    }

    public DashboardEstimateViewModel buildDashboardEstimates(Session session, int requestedYear,
            Map<String, Integer> populationMap) {
        int currentYear = YearMonth.now().getYear();
        List<Integer> availableYears = loadAvailableYears(session, currentYear);
        int selectedYear = selectYear(requestedYear, availableYears, currentYear);

        Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> byMonth = loadYearData(session, selectedYear);
        long totalNationalPopulation = calculateTotalNationalPopulation(session, populationMap);

        List<MonthlyEstimateRow> updateRows = new ArrayList<MonthlyEstimateRow>();
        List<MonthlyEstimateRow> queryRows = new ArrayList<MonthlyEstimateRow>();

        for (YearMonth month : monthsToShow(selectedYear, currentYear)) {
            Map<Integer, JurisdictionMonthlyCounts> monthData = byMonth.get(month);
            if (monthData == null) {
                monthData = Collections.emptyMap();
            }
            int reportingCount = 0;
            for (JurisdictionMonthlyCounts counts : monthData.values()) {
                if (counts.updates > 0 || counts.queries > 0) {
                    reportingCount++;
                }
            }

            MetricEstimate updateEstimate = calculateEstimate(monthData, populationMap, totalNationalPopulation, true);
            MetricEstimate queryEstimate = calculateEstimate(monthData, populationMap, totalNationalPopulation, false);

            String monthLabel = month.getMonth().getDisplayName(TextStyle.FULL, Locale.US);
            updateRows.add(new MonthlyEstimateRow(monthLabel, reportingCount, updateEstimate.getConservativeEstimate(),
                    updateEstimate.getCentralEstimate(), updateEstimate.getHighEstimate(),
                    updateEstimate.isEnoughData()));
            queryRows.add(new MonthlyEstimateRow(monthLabel, reportingCount, queryEstimate.getConservativeEstimate(),
                    queryEstimate.getCentralEstimate(), queryEstimate.getHighEstimate(), queryEstimate.isEnoughData()));
        }

        YearMonth candidateEnd = determineRollingYearEnd(LocalDate.now());
        Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> rollingData = loadRollingWindowData(session,
                candidateEnd.minusMonths(12), candidateEnd);
        int candidateEndReporters = countReportersForMonth(rollingData, candidateEnd);
        YearMonth windowEnd = candidateEndReporters >= MIN_REPORTERS_FOR_YEARLY
                ? candidateEnd
                : candidateEnd.minusMonths(1);
        YearMonth windowStart = windowEnd.minusMonths(11);
        YearlyEstimate updateYearlyEstimate = buildYearlyEstimate(rollingData, windowStart, windowEnd,
                populationMap, totalNationalPopulation, true);
        YearlyEstimate queryYearlyEstimate = buildYearlyEstimate(rollingData, windowStart, windowEnd,
                populationMap, totalNationalPopulation, false);

        return new DashboardEstimateViewModel(selectedYear, availableYears, updateRows, queryRows,
                updateYearlyEstimate, queryYearlyEstimate);
    }

    MetricEstimate calculateEstimate(Map<Integer, JurisdictionMonthlyCounts> monthData,
            Map<String, Integer> populationMap,
            long totalNationalPopulation, boolean useUpdates) {
        List<Double> rates = new ArrayList<Double>();
        long totalReportedCount = 0;
        long totalReportingPopulation = 0;
        int reportingJurisdictions = 0;

        for (JurisdictionMonthlyCounts counts : monthData.values()) {
            Integer population = resolvePopulation(counts.jurisdiction, populationMap);
            if (population == null || population.intValue() <= 0) {
                continue;
            }

            int count = useUpdates ? counts.updates : counts.queries;
            if (count <= 0) {
                continue;
            }
            double rate = (double) count / (double) population.intValue();
            rates.add(Double.valueOf(rate));
            totalReportedCount += count;
            totalReportingPopulation += population.intValue();
            reportingJurisdictions += 1;
        }

        return calculateEstimateFromRates(rates, totalReportedCount, totalReportingPopulation,
                totalNationalPopulation, reportingJurisdictions);
    }

    MetricEstimate calculateEstimateFromRates(List<Double> rates, long totalReportedCount,
            long totalReportingPopulation,
            long totalNationalPopulation, int reportingJurisdictions) {
        if (reportingJurisdictions < MIN_REPORTING_JURISDICTIONS || totalReportingPopulation <= 0
                || totalNationalPopulation <= 0 || rates.isEmpty()) {
            return new MetricEstimate(false, null, null, null);
        }

        Collections.sort(rates);
        double p25Rate = percentileNearestRank(rates, 0.25);
        double p75Rate = percentileNearestRank(rates, 0.75);
        double centralRate = (double) totalReportedCount / (double) totalReportingPopulation;

        long conservativeEstimate = Math.round(p25Rate * totalNationalPopulation);
        long centralEstimate = Math.round(centralRate * totalNationalPopulation);
        long highEstimate = Math.round(p75Rate * totalNationalPopulation);

        return new MetricEstimate(true, Long.valueOf(conservativeEstimate), Long.valueOf(centralEstimate),
                Long.valueOf(highEstimate));
    }

    double percentileNearestRank(List<Double> sortedRates, double percentile) {
        int n = sortedRates.size();
        if (n == 0) {
            return 0;
        }
        int rank = (int) Math.ceil(percentile * n);
        int index = Math.max(0, Math.min(n - 1, rank - 1));
        return sortedRates.get(index).doubleValue();
    }

    private List<Integer> loadAvailableYears(Session session, int currentYear) {
        Query<Date> query = session.createQuery("select distinct reportingPeriod from EntryForInterop", Date.class);
        List<Date> reportingPeriods = query.list();

        Set<Integer> yearSet = new HashSet<Integer>();
        for (Date reportingPeriod : reportingPeriods) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(reportingPeriod);
            yearSet.add(Integer.valueOf(cal.get(Calendar.YEAR)));
        }
        yearSet.add(Integer.valueOf(currentYear));

        List<Integer> years = new ArrayList<Integer>(yearSet);
        years.sort(Comparator.reverseOrder());
        return years;
    }

    private int selectYear(int requestedYear, List<Integer> availableYears, int currentYear) {
        if (requestedYear > currentYear) {
            return currentYear;
        }
        if (availableYears.contains(Integer.valueOf(requestedYear))) {
            return requestedYear;
        }
        if (availableYears.contains(Integer.valueOf(currentYear))) {
            return currentYear;
        }
        return availableYears.isEmpty() ? currentYear : availableYears.get(0).intValue();
    }

    private Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> loadYearData(Session session, int selectedYear) {
        YearMonth firstMonth = YearMonth.of(selectedYear, 1);
        YearMonth firstMonthNextYear = firstMonth.plusYears(1);

        Date start = Date.from(firstMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endExclusive = Date.from(firstMonthNextYear.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        Query<EntryForInterop> query = session.createQuery(
                "from EntryForInterop where reportingPeriod >= :start and reportingPeriod < :end",
                EntryForInterop.class);
        query.setParameter("start", start);
        query.setParameter("end", endExclusive);
        List<EntryForInterop> entries = query.list();

        Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> byMonth = new LinkedHashMap<YearMonth, Map<Integer, JurisdictionMonthlyCounts>>();
        for (EntryForInterop entry : entries) {
            LocalDate localDate = entry.getReportingPeriod().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            YearMonth yearMonth = YearMonth.of(localDate.getYear(), localDate.getMonthValue());

            Map<Integer, JurisdictionMonthlyCounts> monthMap = byMonth.get(yearMonth);
            if (monthMap == null) {
                monthMap = new LinkedHashMap<Integer, JurisdictionMonthlyCounts>();
                byMonth.put(yearMonth, monthMap);
            }

            Jurisdiction jurisdiction = entry.getJurisdiction();
            if (jurisdiction == null) {
                continue;
            }
            int jurisdictionId = jurisdiction.getJurisdictionId();
            JurisdictionMonthlyCounts counts = monthMap.get(Integer.valueOf(jurisdictionId));
            if (counts == null) {
                counts = new JurisdictionMonthlyCounts(jurisdiction);
                monthMap.put(Integer.valueOf(jurisdictionId), counts);
            }
            counts.updates += entry.getCountUpdate();
            counts.queries += entry.getCountQuery();
        }
        return byMonth;
    }

    private long calculateTotalNationalPopulation(Session session, Map<String, Integer> populationMap) {
        Query<Jurisdiction> query = session.createQuery("from Jurisdiction", Jurisdiction.class);
        List<Jurisdiction> jurisdictions = query.list();

        long total = 0;
        for (Jurisdiction jurisdiction : jurisdictions) {
            Integer population = resolvePopulation(jurisdiction, populationMap);
            if (population != null && population.intValue() > 0) {
                total += population.intValue();
            }
        }
        return total;
    }

    private List<YearMonth> monthsToShow(int selectedYear, int currentYear) {
        List<YearMonth> months = new ArrayList<YearMonth>();
        YearMonth start = YearMonth.of(selectedYear, 1);
        YearMonth end = selectedYear == currentYear ? YearMonth.now().minusMonths(1) : YearMonth.of(selectedYear, 12);
        if (end.getYear() < selectedYear) {
            return months;
        }

        YearMonth cursor = start;
        while (!cursor.isAfter(end)) {
            months.add(cursor);
            cursor = cursor.plusMonths(1);
        }
        return months;
    }

    private Integer resolvePopulation(Jurisdiction jurisdiction, Map<String, Integer> populationMap) {
        if (jurisdiction == null) {
            return null;
        }
        Integer population = null;
        if (jurisdiction.getMapLink() != null) {
            population = populationMap.get(jurisdiction.getMapLink());
        }
        if (population == null && jurisdiction.getDisplayLabel() != null) {
            population = populationMap.get(jurisdiction.getDisplayLabel());
        }
        return population;
    }

    YearMonth determineRollingYearEnd(LocalDate today) {
        YearMonth current = YearMonth.of(today.getYear(), today.getMonthValue());
        if (today.getDayOfMonth() >= 15) {
            return current;
        }
        return current.minusMonths(1);
    }

    private Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> loadRollingWindowData(
            Session session, YearMonth windowStart, YearMonth windowEnd) {
        Date start = Date.from(windowStart.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Date endExclusive = Date.from(
                windowEnd.plusMonths(1).atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        Query<EntryForInterop> query = session.createQuery(
                "from EntryForInterop where reportingPeriod >= :start and reportingPeriod < :end",
                EntryForInterop.class);
        query.setParameter("start", start);
        query.setParameter("end", endExclusive);
        List<EntryForInterop> entries = query.list();

        Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> byMonth = new LinkedHashMap<YearMonth, Map<Integer, JurisdictionMonthlyCounts>>();
        for (EntryForInterop entry : entries) {
            LocalDate localDate = entry.getReportingPeriod().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            YearMonth yearMonth = YearMonth.of(localDate.getYear(), localDate.getMonthValue());

            Map<Integer, JurisdictionMonthlyCounts> monthMap = byMonth.get(yearMonth);
            if (monthMap == null) {
                monthMap = new LinkedHashMap<Integer, JurisdictionMonthlyCounts>();
                byMonth.put(yearMonth, monthMap);
            }

            Jurisdiction jurisdiction = entry.getJurisdiction();
            if (jurisdiction == null) {
                continue;
            }
            int jurisdictionId = jurisdiction.getJurisdictionId();
            JurisdictionMonthlyCounts counts = monthMap.get(Integer.valueOf(jurisdictionId));
            if (counts == null) {
                counts = new JurisdictionMonthlyCounts(jurisdiction);
                monthMap.put(Integer.valueOf(jurisdictionId), counts);
            }
            counts.updates += entry.getCountUpdate();
            counts.queries += entry.getCountQuery();
        }
        return byMonth;
    }

    int countReportersForMonth(
            Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> byMonth, YearMonth month) {
        Map<Integer, JurisdictionMonthlyCounts> monthData = byMonth.get(month);
        if (monthData == null) {
            return 0;
        }
        int count = 0;
        for (JurisdictionMonthlyCounts counts : monthData.values()) {
            if (counts.updates > 0 || counts.queries > 0) {
                count++;
            }
        }
        return count;
    }

    YearlyEstimate buildYearlyEstimate(
            Map<YearMonth, Map<Integer, JurisdictionMonthlyCounts>> windowData,
            YearMonth windowStart, YearMonth windowEnd,
            Map<String, Integer> populationMap, long totalNationalPopulation, boolean useUpdates) {
        long lowSum = 0;
        long operationalSum = 0;
        long highSum = 0;
        int qualifyingMonths = 0;

        YearMonth cursor = windowStart;
        while (!cursor.isAfter(windowEnd)) {
            Map<Integer, JurisdictionMonthlyCounts> monthData = windowData.get(cursor);
            if (monthData == null) {
                monthData = Collections.emptyMap();
            }
            int reporters = 0;
            for (JurisdictionMonthlyCounts counts : monthData.values()) {
                if (counts.updates > 0 || counts.queries > 0) {
                    reporters++;
                }
            }
            if (reporters >= MIN_REPORTERS_FOR_YEARLY) {
                MetricEstimate estimate = calculateEstimate(monthData, populationMap, totalNationalPopulation,
                        useUpdates);
                if (estimate.isEnoughData()) {
                    lowSum += estimate.getConservativeEstimate().longValue();
                    operationalSum += estimate.getCentralEstimate().longValue();
                    highSum += estimate.getHighEstimate().longValue();
                    qualifyingMonths++;
                }
            }
            cursor = cursor.plusMonths(1);
        }

        if (qualifyingMonths < MIN_QUALIFYING_MONTHS_FOR_YEARLY) {
            return new YearlyEstimate(windowStart, windowEnd, null, null, null, false);
        }
        return new YearlyEstimate(windowStart, windowEnd,
                Long.valueOf(lowSum), Long.valueOf(operationalSum), Long.valueOf(highSum), true);
    }
}