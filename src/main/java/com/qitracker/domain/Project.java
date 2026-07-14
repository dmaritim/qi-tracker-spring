package com.qitracker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "duration_val", length = 20)
    private String durationVal;

    @Column(name = "duration_unit", nullable = false, length = 20)
    @Builder.Default
    private String durationUnit = "months";

    @Column(columnDefinition = "TEXT")
    private String baseline;

    @Column(name = "success_definition", columnDefinition = "TEXT")
    private String successDefinition;

    @Enumerated(EnumType.STRING)
    @Column(name = "reporting_frequency", nullable = false, length = 20)
    @Builder.Default
    private ReportingFrequency reportingFrequency = ReportingFrequency.weekly;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "org_unit_uuid", referencedColumnName = "uuid", nullable = false)
    private OrgUnit orgUnit;

    @Column(name = "last_report_sent_at")
    private Instant lastReportSentAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /** Computed end date = start date + duration, used for stage-of-project tracking. */
    public LocalDate computeEndDate() {
        if (startDate == null || durationVal == null) return null;
        double amount;
        try { amount = Double.parseDouble(durationVal); } catch (NumberFormatException e) { return null; }
        String unit = durationUnit == null ? "months" : durationUnit;
        long days;
        if ("weeks".equals(unit)) {
            days = Math.round(amount * 7);
        } else if ("years".equals(unit)) {
            days = Math.round(amount * 365);
        } else {
            days = Math.round(amount * 30); // months
        }
        return startDate.plusDays(days);
    }
}