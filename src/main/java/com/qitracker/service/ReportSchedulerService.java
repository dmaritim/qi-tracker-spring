package com.qitracker.service;

import com.qitracker.domain.Project;
import com.qitracker.domain.ProjectMember;
import com.qitracker.repository.ProjectMemberRepository;
import com.qitracker.repository.ProjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class ReportSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(ReportSchedulerService.class);

    private static final Map<com.qitracker.domain.ReportingFrequency, Integer> INTERVAL_DAYS = Map.of(
        com.qitracker.domain.ReportingFrequency.daily, 1,
        com.qitracker.domain.ReportingFrequency.weekly, 7,
        com.qitracker.domain.ReportingFrequency.biweekly, 14,
        com.qitracker.domain.ReportingFrequency.monthly, 30,
        com.qitracker.domain.ReportingFrequency.quarterly, 91
    );

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ReportPdfService reportPdfService;
    private final EmailService emailService;

    public ReportSchedulerService(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository,
                                   ReportPdfService reportPdfService, EmailService emailService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.reportPdfService = reportPdfService;
        this.emailService = emailService;
    }

    @Scheduled(cron = "${app.report-scheduler.cron}")
    public void runScheduledReports() {
        log.info("Running scheduled QI report check...");
        for (Project project : projectRepository.findAll()) {
            try {
                if (isDue(project)) sendReport(project.getId());
            } catch (Exception e) {
                log.error("Failed to send scheduled report for project {}: {}", project.getId(), e.getMessage());
            }
        }
    }

    private boolean isDue(Project project) {
        int intervalDays = INTERVAL_DAYS.get(project.getReportingFrequency());
        if (project.getLastReportSentAt() == null) return true;
        long daysSince = ChronoUnit.DAYS.between(project.getLastReportSentAt(), Instant.now());
        return daysSince >= intervalDays;
    }

    /** Also used by the "send report now" manual endpoint. Takes an ID (not an entity) and re-fetches
     *  within this method's own transaction, so lazy associations (creator, etc.) are guaranteed to be
     *  attached to an active session rather than possibly-detached from wherever the caller got the entity. */
    @Transactional
    public void sendReport(Long projectId) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new IllegalStateException("Project not found: " + projectId));

        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(project.getCreator().getEmail());
        for (ProjectMember pm : projectMemberRepository.findByProjectId(project.getId())) {
            recipients.add(pm.getUser().getEmail());
        }
        if (recipients.isEmpty()) return;

        byte[] pdf = reportPdfService.buildProjectReportPdf(project);
        String subject = "QI report: " + project.getName();
        String html = "<p>Attached is the latest progress report for <strong>" + escape(project.getName()) + "</strong>.</p>";
        String filename = project.getName().replaceAll("[^a-zA-Z0-9]+", "-").toLowerCase() + "-report.pdf";

        for (String email : recipients) {
            emailService.sendHtmlWithAttachment(email, subject, html, pdf, filename);
        }

        project.setLastReportSentAt(Instant.now());
        projectRepository.save(project);
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}