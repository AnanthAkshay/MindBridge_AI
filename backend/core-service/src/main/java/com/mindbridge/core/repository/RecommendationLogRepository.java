package com.mindbridge.core.repository;

import com.mindbridge.core.entity.RecommendationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RecommendationLogRepository extends JpaRepository<RecommendationLog, Long> {
    
    @Query("SELECT r FROM RecommendationLog r WHERE r.user.id = :userId AND r.createdAt >= :since")
    List<RecommendationLog> findRecentRecommendationsForUser(Long userId, java.time.Instant since);

    Optional<RecommendationLog> findByUserIdAndContentIdAndCompletedFalse(Long userId, String contentId);
}
