package com.mindbridge.gateway.security;

import com.mindbridge.core.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnonymousSessionTest {

    @Test
    @DisplayName("AT1: Anonymous user has no email or personal name")
    void anonymousUser_noPiiStored() {
        User anonymous = User.anonymous();
        assertNull(anonymous.getEmail());
        assertNull(anonymous.getPasswordHash());
        assertEquals("Anonymous User", anonymous.getFullName());
        assertEquals("ANONYMOUS", anonymous.getRole());
        assertTrue(anonymous.getIsAnonymous());
        assertNull(anonymous.getAvatarUrl());
    }

    @Test
    @DisplayName("Registered user has PII (contrast test)")
    void registeredUser_hasPii() {
        User registered = new User("test@example.com", "hashed", "John Doe");
        assertNotNull(registered.getEmail());
        assertFalse(registered.getIsAnonymous());
    }
}
