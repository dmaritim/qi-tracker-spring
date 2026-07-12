package com.qitracker.service;

import com.qitracker.domain.*;
import com.qitracker.dto.DashboardDtos.SummaryInfo;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * Computes the same on-target / off-target / status snapshot that the full Dashboard view shows,
 * but without building period-bucketed trends — used to annotate every card in the project list
 * cheaply (one batch query for all indicators + entries across all projects, not one dashboard
 * fetch per project).
 */
@Service
public class ProjectSummaryService {

    private static final Map<ReportingFrequency, Integer> INTERVAL_DAYS = Map.of(
        ReportingFrequency.daily, 1, ReportingFrequency.weekly, 7, ReportingFrequency.biweekly, 14,
        ReportingFrequency.monthly, 30, ReportingFrequency.quarterly, 91
    );

    public SummaryInfo compute(Project project, List<Indicator> indicators, List<Entry> entries) {
        int total = indicators.size();
        int onTarget = 0;
        int evaluated = 0;

        for (Indicator indicator : indicators) {
            Entry latest = null;
            for (Entry e : entries) {
                if (!e.getIndicator().getId().equals(indicator.getId())) continue;
                if (latest == null || e.getEntryDate().isAfter(latest.getEntryDate())) latest = e;
            }
            if (latest == null || indicator.getTargetValue() == null) continue;
            evaluated++;
            boolean onT = indicator.getDirection() == Direction.lower
                ? latest.getComputedValue() <= indicator.getTargetValue()
                : latest.getComputedValue() >= indicator.getTargetValue();
            if (onT) onTarget++;
        }
        int offTarget = evaluated - onTarget;
        Double overallPercent = evaluated == 0 ? null : round2((onTarget / (double) evaluated) * 100.0);

        LocalDate lastReported = null;
        for (Entry e : entries) {
            if (lastReported == null || e.getEntryDate().isAfter(lastReported)) lastReported = e.getEntryDate();
        }

        String status;
        if (total == 0 || lastReported == null) {
            status = "nodata";
        } else {
            long daysSince = ChronoUnit.DAYS.between(lastReported, LocalDate.now());
            int interval = INTERVAL_DAYS.get(project.getReportingFrequency());
            if (daysSince <= interval) status = "ontrack";
            else if (daysSince <= interval * 1.5) status = "duesoon";
            else status = "overdue";
        }

        return new SummaryInfo(total, onTarget, offTarget, overallPercent, status,
            lastReported == null ? null : lastReported.toString());
    }

    private double round2(double n) { return Math.round(n * 100.0) / 100.0; }
}