package com.mindbridge.gateway.chat;

import com.mindbridge.core.entity.Message;
import com.mindbridge.core.entity.Session;
import com.mindbridge.core.entity.User;
import com.mindbridge.core.repository.MessageRepository;
import com.mindbridge.core.repository.SessionRepository;
import com.mindbridge.core.service.MessageEncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Chat service — handles message encryption, persistence, and retrieval.
 * All messages are encrypted with AES-256-GCM before hitting the database.
 */
@Service
public class ChatService {

    private final MessageRepository messageRepository;
    private final SessionRepository sessionRepository;
    private final MessageEncryptionService encryptionService;

    public ChatService(
            MessageRepository messageRepository,
            SessionRepository sessionRepository,
            MessageEncryptionService encryptionService) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
        this.encryptionService = encryptionService;
    }

    /** Create a new chat session for the given user */
    @Transactional
    public Session createSession(User user, String title) {
        Session session = new Session();
        session.setUser(user);
        session.setTitle(title != null ? title : "New conversation");
        session.setSessionType("CHAT");
        session.setStatus("ACTIVE");
        return sessionRepository.save(session);
    }

    /** Save a message — encrypts content before persisting */
    @Transactional
    public ChatMessageResponse saveAndEncrypt(Long sessionId, String senderType, String plaintext, NlpServiceClient.NlpResponse nlp) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // Encrypt the message content
        String[] encrypted = encryptionService.encrypt(plaintext);

        Message msg = new Message(
            session, 
            senderType, 
            encrypted[0], 
            encrypted[1],
            nlp.emotion(),
            nlp.confidence(),
            nlp.valence(),
            nlp.arousal()
        );
        msg = messageRepository.save(msg);

        return new ChatMessageResponse(
                msg.getId(),
                sessionId,
                senderType,
                plaintext, // Return decrypted content over the wire — only DB stores ciphertext
                msg.getEmotion(),
                msg.getEmotionScore(),
                msg.getCreatedAt()
        );
    }

    /** Load full chat history for a session — decrypts all messages */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getSessionHistory(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId)
                .stream()
                .map(msg -> new ChatMessageResponse(
                        msg.getId(),
                        sessionId,
                        msg.getSenderType(),
                        encryptionService.decrypt(msg.getEncryptedContent(), msg.getEncryptionIv()),
                        msg.getEmotion(),
                        msg.getEmotionScore(),
                        msg.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    /** List all sessions for a user */
    @Transactional(readOnly = true)
    public List<Session> getUserSessions(Long userId) {
        return sessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    /** Validate that a session belongs to a user */
    @Transactional(readOnly = true)
    public boolean isSessionOwnedByUser(Long sessionId, Long userId) {
        return sessionRepository.findById(sessionId)
                .map(s -> s.getUser().getId().equals(userId))
                .orElse(false);
    }
}
