package com.qitracker.service;

import com.qitracker.domain.*;
import com.qitracker.dto.DashboardDtos.*;
import com.qitracker.dto.PdsaDtos.PdsaMarker;
import com.qitracker.repository.EntryRepository;
import com.qitracker.repository.IndicatorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d");
    private static final Map<ReportingFrequency, Integer> INTERVAL_DAYS = Map.of(
        ReportingFrequency.daily, 1, ReportingFrequency.weekly, 7, ReportingFrequency.biweekly, 14,
        ReportingFrequency.monthly, 30, ReportingFrequency.quarterly, 91
    );

    private final ProjectService projectService;
    private final IndicatorRepository indicatorRepository;
    private final EntryRepository entryRepository;
    private final PdsaCycleService pdsaCycleService;

    public DashboardService(ProjectService projectService, IndicatorRepository indicatorRepository,
                             EntryRepository entryRepository, PdsaCycleService pdsaCycleService) {
        this.projectService = projectService;
        this.indicatorRepository = indicatorRepository;
        this.entryRepository = entryRepository;
        this.pdsaCycleService = pdsaCycleService;
    }

    @Transactional(readOnly = true)
    public DashboardResponse build(Long projectId, User requester) {
        Project project = projectService.getOrThrow(projectId);
        List<Indicator> indicators = indicatorRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        List<Entry> allEntries = entryRepository.findByIndicatorProjectIdOrderByEntryDateAsc(projectId);

        Map<Long, List<Entry>> entriesByIndicator = allEntries.stream()
            .collect(Collectors.groupingBy(e -> e.getIndicator().getId()));

        List<PeriodWindow> windows = buildPeriodWindows(project);

        List<IndicatorTrend> trends = indicators.stream()
            .map(ind -> buildTrend(ind, entriesByIndicator.getOrDefault(ind.getId(), List.of()), windows))
            .toList();

        Map<Long, List<IndicatorTrend>> byArea = new LinkedHashMap<>();
        List<IndicatorTrend> ungrouped = new ArrayList<>();
        Map<Long, String> areaNames = new LinkedHashMap<>();
        for (int idx = 0; idx < indicators.size(); idx++) {
            Indicator ind = indicators.get(idx);
            IndicatorTrend trend = trends.get(idx);
            if (ind.getProcessArea() == null) {
                ungrouped.add(trend);
            } else {
                Long areaId = ind.getProcessArea().getId();
                areaNames.put(areaId, ind.getProcessArea().getName());
                byArea.computeIfAbsent(areaId, k -> new ArrayList<>()).add(trend);
            }
        }
        List<ProcessAreaGroup> groups = byArea.entrySet().stream()
            .map(e -> new ProcessAreaGroup(e.getKey(), areaNames.get(e.getKey()), e.getValue()))
            .toList();

        StageInfo stage = computeStage(project);
        SummaryInfo summary = computeSummary(project, indicators, allEntries, trends);
        List<PdsaMarker> pdsaCycles = pdsaCycleService.markersForProject(projectId);

        return new DashboardResponse(projectService.toResponse(project, requester, summary), stage, summary, groups, ungrouped, pdsaCycles);
    }

    private record PeriodWindow(String label, LocalDate start, LocalDate endExclusive) {}

    // Safety cap so a mistakenly ancient start date can't generate an unbounded number of periods.
    private static final int MAX_PERIODS = 300;

    /** Periods span the whole project, from its start date through today, bucketed by its reporting
     *  frequency — so the chart shows the full history, not just a recent trailing window. Falls back
     *  to a trailing window (like before) only when the project has no start date set to anchor from. */
    private List<PeriodWindow> buildPeriodWindows(Project project) {
        LocalDate today = LocalDate.now();
        LocalDate start = project.getStartDate();
        ReportingFrequency freq = project.getReportingFrequency();

        if (start == null || start.isAfter(today)) {
            return buildTrailingWindows(freq, today);
        }

        List<PeriodWindow> windows = new ArrayList<>();
        LocalDate cursor = alignToPeriodStart(start, freq);
        int guard = 0;
        while (!cursor.isAfter(today) && guard < MAX_PERIODS) {
            LocalDate periodEnd = advance(cursor, freq);
            windows.add(new PeriodWindow(labelFor(cursor, freq), cursor, periodEnd));
            cursor = periodEnd;
            guard++;
        }
        if (windows.isEmpty()) {
            windows.add(new PeriodWindow(labelFor(start, freq), start, advance(start, freq)));
        }
        return windows;
    }

    private LocalDate alignToPeriodStart(LocalDate date, ReportingFrequency freq) {
        if (freq == ReportingFrequency.daily) {
            return date;
        } else if (freq == ReportingFrequency.weekly || freq == ReportingFrequency.biweekly) {
            return date.minusDays(date.getDayOfWeek().getValue() - 1);
        } else if (freq == ReportingFrequency.monthly) {
            return date.withDayOfMonth(1);
        } else { // quarterly
            int q = (date.getMonthValue() - 1) / 3;
            return date.withDayOfMonth(1).withMonth(q * 3 + 1);
        }
    }

    private LocalDate advance(LocalDate periodStart, ReportingFrequency freq) {
        if (freq == ReportingFrequency.daily) {
            return periodStart.plusDays(1);
        } else if (freq == ReportingFrequency.weekly) {
            return periodStart.plusDays(7);
        } else if (freq == ReportingFrequency.biweekly) {
            return periodStart.plusDays(14);
        } else if (freq == ReportingFrequency.monthly) {
            return periodStart.plusMonths(1);
        } else { // quarterly
            return periodStart.plusMonths(3);
        }
    }

    private String labelFor(LocalDate periodStart, ReportingFrequency freq) {
        if (freq == ReportingFrequency.daily) {
            return periodStart.format(SHORT);
        } else if (freq == ReportingFrequency.weekly) {
            return "Wk of " + periodStart.format(SHORT);
        } else if (freq == ReportingFrequency.biweekly) {
            return "2wk of " + periodStart.format(SHORT);
        } else if (freq == ReportingFrequency.monthly) {
            return periodStart.format(DateTimeFormatter.ofPattern("MMM yyyy"));
        } else { // quarterly
            int q = (periodStart.getMonthValue() - 1) / 3 + 1;
            return "Q" + q + " " + periodStart.getYear();
        }
    }

    /** Used only when a project has no start date set — a recent trailing window, same as the app's
     *  original behavior, so the dashboard still shows something useful. */
    private List<PeriodWindow> buildTrailingWindows(ReportingFrequency freq, LocalDate today) {
        List<PeriodWindow> windows = new ArrayList<>();
        if (freq == ReportingFrequency.daily) {
            for (int i = 13; i >= 0; i--) {
                LocalDate start = today.minusDays(i);
                windows.add(new PeriodWindow(start.format(SHORT), start, start.plusDays(1)));
            }
        } else if (freq == ReportingFrequency.weekly) {
            for (int i = 11; i >= 0; i--) {
                LocalDate start = today.minusWeeks(i).minusDays(today.getDayOfWeek().getValue() - 1);
                windows.add(new PeriodWindow("Wk of " + start.format(SHORT), start, start.plusDays(7)));
            }
        } else if (freq == ReportingFrequency.biweekly) {
            LocalDate weekAnchor = today.minusDays(today.getDayOfWeek().getValue() - 1);
            for (int i = 11; i >= 0; i--) {
                LocalDate start = weekAnchor.minusDays(i * 14L);
                windows.add(new PeriodWindow("2wk of " + start.format(SHORT), start, start.plusDays(14)));
            }
        } else if (freq == ReportingFrequency.monthly) {
            LocalDate monthAnchor = today.withDayOfMonth(1);
            for (int i = 11; i >= 0; i--) {
                LocalDate start = monthAnchor.minusMonths(i);
                windows.add(new PeriodWindow(start.format(DateTimeFormatter.ofPattern("MMM yyyy")), start, start.plusMonths(1)));
            }
        } else if (freq == ReportingFrequency.quarterly) {
            int currentQuarter = (today.getMonthValue() - 1) / 3;
            LocalDate quarterAnchor = today.withDayOfMonth(1).withMonth(currentQuarter * 3 + 1);
            for (int i = 7; i >= 0; i--) {
                LocalDate start = quarterAnchor.minusMonths(i * 3L);
                int q = (start.getMonthValue() - 1) / 3 + 1;
                windows.add(new PeriodWindow("Q" + q + " " + start.getYear(), start, start.plusMonths(3)));
            }
        }
        return windows;
    }

    private IndicatorTrend buildTrend(Indicator indicator, List<Entry> entries, List<PeriodWindow> windows) {
        List<PeriodPoint> points = new ArrayList<>();
        for (PeriodWindow w : windows) {
            List<Entry> inWindow = entries.stream()
                .filter(e -> !e.getEntryDate().isBefore(w.start()) && e.getEntryDate().isBefore(w.endExclusive()))
                .toList();
            Double avg = inWindow.isEmpty() ? null :
                inWindow.stream().mapToDouble(Entry::getComputedValue).average().orElse(0.0);
            points.add(new PeriodPoint(w.label(), w.start().toString(), avg == null ? null : round2(avg), inWindow.size()));
        }
        Double latest = entries.isEmpty() ? null : entries.get(entries.size() - 1).getComputedValue();
        Boolean onTarget = null;
        if (latest != null && indicator.getTargetValue() != null) {
            onTarget = indicator.getDirection() == Direction.lower
                ? latest <= indicator.getTargetValue()
                : latest >= indicator.getTargetValue();
        }
        return new IndicatorTrend(indicator.getId(), indicator.getName(), indicator.getUnit(),
            indicator.getTargetValue(), indicator.getDirection().name(), latest, onTarget, points);
    }

    private StageInfo computeStage(Project project) {
        LocalDate start = project.getStartDate();
        LocalDate end = project.computeEndDate();
        if (start == null || end == null) {
            return new StageInfo("Timeline not set", null, null, null, null);
        }
        LocalDate today = LocalDate.now();
        long totalDays = Math.max(1, ChronoUnit.DAYS.between(start, end));
        if (today.isBefore(start)) {
            return new StageInfo("Not started yet", end.toString(), 0, (int) totalDays, 0.0);
        }
        long elapsed = ChronoUnit.DAYS.between(start, today);
        double percent = Math.min(100.0, (elapsed / (double) totalDays) * 100.0);
        String label;
        if (!today.isBefore(end)) {
            label = "Past end date";
            percent = Math.max(percent, 100.0);
        } else if (percent < 25) {
            label = "Early stage";
        } else if (percent < 50) {
            label = "Building momentum";
        } else if (percent < 75) {
            label = "Midpoint push";
        } else {
            label = "Final stretch";
        }
        return new StageInfo(label, end.toString(), (int) elapsed, (int) totalDays, round2(percent));
    }

    private SummaryInfo computeSummary(Project project, List<Indicator> indicators, List<Entry> allEntries, List<IndicatorTrend> trends) {
        int total = indicators.size();
        long onTarget = trends.stream().filter(t -> Boolean.TRUE.equals(t.onTarget())).count();
        long evaluated = trends.stream().filter(t -> t.onTarget() != null).count();
        long offTarget = evaluated - onTarget;
        Double overallPercent = evaluated == 0 ? null : round2((onTarget / (double) evaluated) * 100.0);

        LocalDate lastReported = allEntries.stream().map(Entry::getEntryDate).max(LocalDate::compareTo).orElse(null);
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

        return new SummaryInfo(total, (int) onTarget, (int) offTarget, overallPercent, status,
            lastReported == null ? null : lastReported.toString());
    }

    private double round2(double n) { return Math.round(n * 100.0) / 100.0; }
}