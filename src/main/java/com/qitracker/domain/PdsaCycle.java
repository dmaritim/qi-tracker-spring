package com.qitracker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "pdsa_cycles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdsaCycle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_area_id")
    private ProcessArea processArea;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "plan_text", columnDefinition = "TEXT")
    private String planText;

    @Column(name = "prediction_text", columnDefinition = "TEXT")
    private String predictionText;

    @Column(name = "do_text", columnDefinition = "TEXT")
    private String doText;

    @Column(name = "study_text", columnDefinition = "TEXT")
    private String studyText;

    @Enumerated(EnumType.STRING)
    @Column(name = "act_decision", nullable = false, length = 20)
    @Builder.Default
    private PdsaActDecision actDecision = PdsaActDecision.in_progress;

    @Column(name = "act_text", columnDefinition = "TEXT")
    private String actText;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}