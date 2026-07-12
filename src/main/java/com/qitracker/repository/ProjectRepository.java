package com.qitracker.repository;

import com.qitracker.domain.Project;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    @EntityGraph(attributePaths = {"creator"})
    List<Project> findAllByOrderByCreatedAtDesc();
}