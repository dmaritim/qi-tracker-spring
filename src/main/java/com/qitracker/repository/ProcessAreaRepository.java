package com.qitracker.repository;

import com.qitracker.domain.ProcessArea;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProcessAreaRepository extends JpaRepository<ProcessArea, Long> {
    List<ProcessArea> findByProjectIdOrderBySortOrderAsc(Long projectId);
}
