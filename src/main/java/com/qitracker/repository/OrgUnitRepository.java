package com.qitracker.repository;

import com.qitracker.domain.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, String> {
    List<OrgUnit> findAllByOrderByLevelAscNameAsc();
    Optional<OrgUnit> findByCodeIgnoreCase(String code);
    List<OrgUnit> findByParentUuid(String parentUuid);
}