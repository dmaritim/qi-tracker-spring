package com.qitracker.repository;

import com.qitracker.domain.PdsaCycleIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PdsaCycleIndicatorRepository extends JpaRepository<PdsaCycleIndicator, Long> {
    List<PdsaCycleIndicator> findByCycleId(Long cycleId);
    List<PdsaCycleIndicator> findByCycleIdIn(Collection<Long> cycleIds);
    void deleteByCycleId(Long cycleId);
}