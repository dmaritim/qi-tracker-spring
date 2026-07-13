package com.qitracker.repository;

import com.qitracker.domain.PdsaCycle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PdsaCycleRepository extends JpaRepository<PdsaCycle, Long> {
    List<PdsaCycle> findByProjectIdOrderByStartDateDesc(Long projectId);
}