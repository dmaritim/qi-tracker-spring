package com.qitracker.repository;

import com.qitracker.domain.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface IndicatorRepository extends JpaRepository<Indicator, Long> {
    List<Indicator> findByProjectIdOrderByCreatedAtAsc(Long projectId);
    List<Indicator> findByProjectIdIn(Collection<Long> projectIds);
}