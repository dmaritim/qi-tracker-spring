package com.qitracker.repository;

import com.qitracker.domain.IndicatorElement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface IndicatorElementRepository extends JpaRepository<IndicatorElement, Long> {
    List<IndicatorElement> findByIndicatorId(Long indicatorId);
}
