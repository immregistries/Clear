package org.immregistries.clear.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.immregistries.clear.service.DashboardEstimateService.MetricEstimate;
import org.junit.Test;

public class DashboardEstimateServiceTest {

    private final DashboardEstimateService dashboardEstimateService = new DashboardEstimateService();

    @Test
    public void shouldReturnNotEnoughDataWhenReportingJurisdictionCountIsBelowMinimum() {
        List<Double> rates = Arrays.asList(Double.valueOf(0.1), Double.valueOf(0.2), Double.valueOf(0.3));

        MetricEstimate metricEstimate = dashboardEstimateService.calculateEstimateFromRates(rates, 60L, 300L, 1000L, 3);

        assertFalse(metricEstimate.isEnoughData());
        assertNull(metricEstimate.getCentralEstimate());
        assertNull(metricEstimate.getConservativeEstimate());
        assertNull(metricEstimate.getHighEstimate());
    }

    @Test
    public void shouldCalculateCentralConservativeAndHighEstimatesWithNearestRankPercentiles() {
        List<Double> rates = new ArrayList<Double>(Arrays.asList(
                Double.valueOf(0.1),
                Double.valueOf(0.2),
                Double.valueOf(0.3),
                Double.valueOf(0.4)));

        MetricEstimate metricEstimate = dashboardEstimateService.calculateEstimateFromRates(rates, 100L, 1000L, 10000L,
                4);

        assertTrue(metricEstimate.isEnoughData());
        assertEquals(Long.valueOf(1000L), metricEstimate.getCentralEstimate());
        assertEquals(Long.valueOf(1000L), metricEstimate.getConservativeEstimate());
        assertEquals(Long.valueOf(3000L), metricEstimate.getHighEstimate());
    }

    @Test
    public void shouldUseNearestRankPercentileSelection() {
        List<Double> sortedRates = new ArrayList<Double>(Arrays.asList(
                Double.valueOf(0.10),
                Double.valueOf(0.20),
                Double.valueOf(0.30),
                Double.valueOf(0.40),
                Double.valueOf(0.50)));

        assertEquals(0.20, dashboardEstimateService.percentileNearestRank(sortedRates, 0.25), 0.000001);
        assertEquals(0.40, dashboardEstimateService.percentileNearestRank(sortedRates, 0.75), 0.000001);
    }

    @Test
    public void shouldClampPercentileIndexForSmallLists() {
        List<Double> sortedRates = Collections.singletonList(Double.valueOf(0.42));

        assertEquals(0.42, dashboardEstimateService.percentileNearestRank(sortedRates, 0.25), 0.000001);
        assertEquals(0.42, dashboardEstimateService.percentileNearestRank(sortedRates, 0.75), 0.000001);
    }
}