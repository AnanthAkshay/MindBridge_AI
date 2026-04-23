package com.mindbridge.gateway.security;

import com.mindbridge.core.service.MessageEncryptionService;
import com.mindbridge.core.service.KeyDerivationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for encryption and key derivation.
 *
 * <p>Validates that messages are stored as ciphertext (not plaintext)
 * and can be correctly decrypted, plus per-session key uniqueness.</p>
 */
class EncryptionTest {

    private final MessageEncryptionService encryptionService =
            new MessageEncryptionService("test-encryption-key-for-unit-tests!");

    private final KeyDerivationService keyDerivationService =
            new KeyDerivationService("test-master-key-for-derivation!");

    /**
     * AT5: Read a message from DB → confirm stored value is ciphertext, not plaintext.
     */
    @Test
    @DisplayName("AT5: Encrypted content is NOT plaintext")
    void encryptedContent_isNotPlaintext() {
        String plaintext = "I'm feeling very sad and hopeless today.";
        String[] encrypted = encryptionService.encrypt(plaintext);

        String ciphertext = encrypted[0];
        String iv = encrypted[1];

        // Ciphertext must not equal plaintext
        assertNotEquals(plaintext, ciphertext,
                "Stored value must be ciphertext, not plaintext");

        // IV must be present and non-empty
        assertNotNull(iv);
        assertFalse(iv.isBlank(), "IV must be non-empty");

        // Ciphertext should be Base64-encoded
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(ciphertext),
                "Ciphertext should be valid Base64");
    }

    /**
     * AT6: Decrypt the same message → confirm plaintext matches original.
     */
    @Test
    @DisplayName("AT6: Decrypt ciphertext → matches original plaintext")
    void decrypt_matchesOriginal() {
        String original = "This is a sensitive mental health conversation message.";
        String[] encrypted = encryptionService.encrypt(original);

        String decrypted = encryptionService.decrypt(encrypted[0], encrypted[1]);

        assertEquals(original, decrypted,
                "Decrypted content must exactly match original plaintext");
    }

    @Test
    @DisplayName("Different messages produce different ciphertexts")
    void differentMessages_differentCiphertexts() {
        String[] enc1 = encryptionService.encrypt("message one");
        String[] enc2 = encryptionService.encrypt("message two");

        assertNotEquals(enc1[0], enc2[0], "Different plaintext should produce different ciphertext");
    }

    @Test
    @DisplayName("Same message encrypted twice produces different ciphertexts (unique IV)")
    void sameMessage_differentIVs() {
        String message = "same message";
        String[] enc1 = encryptionService.encrypt(message);
        String[] enc2 = encryptionService.encrypt(message);

        assertNotEquals(enc1[0], enc2[0], "Same plaintext with different IVs should produce different ciphertext");
        assertNotEquals(enc1[1], enc2[1], "IVs should be unique per encryption");
    }

    @Test
    @DisplayName("HKDF derives different keys for different sessions")
    void hkdf_differentSessionKeys() {
        SecretKey key1 = keyDerivationService.deriveSessionKey(1L);
        SecretKey key2 = keyDerivationService.deriveSessionKey(2L);

        assertNotNull(key1);
        assertNotNull(key2);
        assertNotEquals(key1, key2, "Different sessions must have different derived keys");
        assertEquals("AES", key1.getAlgorithm());
        assertEquals(32, key1.getEncoded().length, "Key must be 256 bits (32 bytes)");
    }

    @Test
    @DisplayName("HKDF produces deterministic keys for same session")
    void hkdf_deterministicForSameSession() {
        SecretKey key1 = keyDerivationService.deriveSessionKey(42L);
        SecretKey key2 = keyDerivationService.deriveSessionKey(42L);

        assertArrayEquals(key1.getEncoded(), key2.getEncoded(),
                "Same session ID should always produce the same derived key");
    }

    @Test
    @DisplayName("Empty string can be encrypted and decrypted")
    void emptyString_roundTrip() {
        String[] enc = encryptionService.encrypt("");
        String decrypted = encryptionService.decrypt(enc[0], enc[1]);
        assertEquals("", decrypted);
    }

    @Test
    @DisplayName("Unicode content survives encryption round-trip")
    void unicode_roundTrip() {
        String unicode = "I feel 😢 and don't know what to do 💔";
        String[] enc = encryptionService.encrypt(unicode);
        String decrypted = encryptionService.decrypt(enc[0], enc[1]);
        assertEquals(unicode, decrypted);
    }
}
