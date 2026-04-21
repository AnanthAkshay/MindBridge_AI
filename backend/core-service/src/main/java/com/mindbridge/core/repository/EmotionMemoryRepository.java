package com.mindbridge.core.repository;

import com.mindbridge.core.entity.EmotionMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmotionMemoryRepository extends JpaRepository<EmotionMemory, Long> {
    
    List<EmotionMemory> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    Optional<EmotionMemory> findBySessionId(Long sessionId);
    
    @Query("SELECT e.dominantEmotion FROM EmotionMemory e WHERE e.user.id = :userId GROUP BY e.dominantEmotion HAVING COUNT(e) >= 3")
    List<String> findRecurringEmotions(Long userId);
}
