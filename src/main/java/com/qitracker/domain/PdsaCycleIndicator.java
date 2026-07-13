package com.qitracker.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "pdsa_cycle_indicators", uniqueConstraints = @UniqueConstraint(columnNames = {"cycle_id", "indicator_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdsaCycleIndicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cycle_id", nullable = false)
    private PdsaCycle cycle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_id", nullable = false)
    private Indicator indicator;
}