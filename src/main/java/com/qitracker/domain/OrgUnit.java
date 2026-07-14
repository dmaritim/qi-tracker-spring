package com.qitracker.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "org_units")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgUnit {

    @Id
    @Column(length = 36)
    private String uuid;

    @Column(nullable = false, length = 300)
    private String name;

    @Column(name = "short_name", length = 100)
    private String shortName;

    @Column(length = 50)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_uuid", referencedColumnName = "uuid")
    private OrgUnit parent;

    private Integer level;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @PrePersist
    private void ensureUuid() {
        if (uuid == null || uuid.isBlank()) {
            uuid = UUID.randomUUID().toString();
        }
    }
}