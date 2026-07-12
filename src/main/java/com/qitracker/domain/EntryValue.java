package com.qitracker.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "entry_values")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EntryValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "entry_id", nullable = false)
    private Entry entry;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "indicator_element_id", nullable = false)
    private IndicatorElement indicatorElement;

    @Column(nullable = false)
    @Builder.Default
    private Double value = 0.0;
}
