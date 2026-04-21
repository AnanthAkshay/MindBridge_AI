package com.mindbridge.core.repository;

import com.mindbridge.core.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {

    List<Session> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<Session> findByStatus(String status);

    long countByUserId(Long userId);
}
