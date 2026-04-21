package com.mindbridge.core.service;

import com.mindbridge.core.entity.Session;
import com.mindbridge.core.repository.SessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SessionService {

    private final SessionRepository sessionRepository;

    public SessionService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public List<Session> getUserSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    // Abstracting repository access adds a boundary for future business logic
    // like checking security context, applying rate limits, wrapping in Redis cache, etc.
}
