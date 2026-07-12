package com.qitracker.repository;

import com.qitracker.domain.Entry;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface EntryRepository extends JpaRepository<Entry, Long> {
    List<Entry> findByIndicatorIdOrderByEntryDateAsc(Long indicatorId);
    List<Entry> findByIndicatorProjectIdOrderByEntryDateAsc(Long projectId);
    List<Entry> findByIndicatorProjectIdIn(Collection<Long> projectIds);
}