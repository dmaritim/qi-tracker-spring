package com.qitracker.repository;

import com.qitracker.domain.User;
import com.qitracker.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    long countBy();
    List<User> findAllByOrderByCreatedAtAsc();
    long countByRole(UserRole role);
}