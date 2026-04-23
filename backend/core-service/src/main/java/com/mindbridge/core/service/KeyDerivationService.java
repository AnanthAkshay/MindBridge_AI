package com.mindbridge.core.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * HKDF-SHA256 key derivation for per-session encryption keys.
 *
 * <p>Derives a unique 256-bit AES key for each session by applying
 * HKDF (RFC 5869) with the session ID as the salt and a master secret
 * from the environment variable {@code ENCRYPTION_MASTER_KEY}.</p>
 *
 * <p>This ensures that even if a single session key is compromised,
 * other sessions remain secure (forward secrecy per session).</p>
 */
@Component
public class KeyDerivationService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int KEY_LENGTH_BYTES = 32; // AES-256
    private static final byte[] INFO = "mindbridge-session-key".getBytes(StandardCharsets.UTF_8);

    private final byte[] masterKeyBytes;

    /**
     * Construct the key derivation service.
     *
     * <p>The master key is read from the environment and NEVER logged.
     * In production, this must be a high-entropy secret stored in a
     * secure vault.</p>
     *
     * @param masterKey the master encryption key from environment/config
     */
    public KeyDerivationService(
            @Value("${mindbridge.encryption.master-key:${ENCRYPTION_MASTER_KEY:MindBridgeAI-Master-Key-256bit-Secret!}}") String masterKey) {
        this.masterKeyBytes = masterKey.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Derive a per-session AES-256 key using HKDF-SHA256.
     *
     * <p>Steps:</p>
     * <ol>
     *   <li>HKDF-Extract: PRK = HMAC-SHA256(salt=sessionId, IKM=masterKey)</li>
     *   <li>HKDF-Expand: OKM = HMAC-SHA256(PRK, info || 0x01), truncated to 32 bytes</li>
     * </ol>
     *
     * @param sessionId the session identifier used as HKDF salt
     * @return a 256-bit AES SecretKey unique to this session
     */
    public SecretKey deriveSessionKey(Long sessionId) {
        try {
            byte[] salt = String.valueOf(sessionId).getBytes(StandardCharsets.UTF_8);

            // HKDF-Extract
            byte[] prk = hkdfExtract(salt, masterKeyBytes);

            // HKDF-Expand (single round — 32 bytes is within one HMAC output)
            byte[] okm = hkdfExpand(prk, INFO, KEY_LENGTH_BYTES);

            return new SecretKeySpec(okm, "AES");
        } catch (Exception e) {
            throw new RuntimeException("Key derivation failed for session " + sessionId, e);
        }
    }

    /**
     * HKDF-Extract: PRK = HMAC-SHA256(salt, IKM).
     *
     * @param salt the salt value
     * @param ikm  the input keying material
     * @return the pseudo-random key (PRK)
     */
    private byte[] hkdfExtract(byte[] salt, byte[] ikm)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(salt, HMAC_ALGORITHM));
        return mac.doFinal(ikm);
    }

    /**
     * HKDF-Expand: OKM = HMAC-SHA256(PRK, info || counter).
     *
     * @param prk    the pseudo-random key from Extract
     * @param info   application-specific context info
     * @param length the desired output key length in bytes
     * @return the output keying material
     */
    private byte[] hkdfExpand(byte[] prk, byte[] info, int length)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(prk, HMAC_ALGORITHM));

        // For AES-256 (32 bytes), one round is sufficient (HMAC-SHA256 = 32 bytes)
        byte[] input = new byte[info.length + 1];
        System.arraycopy(info, 0, input, 0, info.length);
        input[info.length] = 0x01; // Counter byte

        byte[] okm = mac.doFinal(input);
        return Arrays.copyOf(okm, length);
    }
}
