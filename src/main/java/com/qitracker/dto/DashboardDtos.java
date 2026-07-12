package com.qitracker.dto;

import java.util.List;

public class DashboardDtos {

    public record StageInfo(
        String label, String endDate, Integer daysElapsed, Integer daysTotal, Double percentElapsed
    ) {}

    public record SummaryInfo(
        int totalIndicators, int onTargetCount, int offTargetCount,
        Double overallPercentOnTarget, String projectStatus, String lastReportedDate
    ) {}

    public record PeriodPoint(String periodLabel, String periodStart, Double avgValue, int entryCount) {}

    public record IndicatorTrend(
        Long indicatorId, String indicatorName, String unit, Double targetValue, String direction,
        Double latestValue, Boolean onTarget, List<PeriodPoint> periods
    ) {}

    public record ProcessAreaGroup(Long processAreaId, String processAreaName, List<IndicatorTrend> indicators) {}

    public record DashboardResponse(
        ProjectDtos.ProjectResponse project, StageInfo stage, SummaryInfo summary,
        List<ProcessAreaGroup> groups, List<IndicatorTrend> ungrouped
    ) {}
}
