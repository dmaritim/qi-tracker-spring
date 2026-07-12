package com.qitracker.repository;

import com.qitracker.domain.EntryValue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EntryValueRepository extends JpaRepository<EntryValue, Long> {
    List<EntryValue> findByEntryId(Long entryId);
    List<EntryValue> findByEntryIdIn(List<Long> entryIds);
    void deleteByEntryId(Long entryId);
}
