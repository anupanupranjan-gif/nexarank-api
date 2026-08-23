// Copyright (c) 2026 Anup Ranjan. Licensed under Apache 2.0 (https://www.apache.org/licenses/LICENSE-2.0)
package com.nexarank.api.compliance;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * NR-155: AES-256-GCM encrypt/decrypt for {@link EncryptedStringConverter}.
 * Not wired into Spring — a JPA AttributeConverter is instantiated directly
 * by the persistence provider via a no-arg constructor, so keeping this a
 * plain static utility avoids depending on Hibernate's Spring-bean-container
 * integration being configured correctly.
 *
 * Key comes from FIELD_ENCRYPTION_KEY (base64, 32 raw bytes — generate with
 * `openssl rand -base64 32`), read lazily so a deployment that never enables
 * field encryption on any column never needs the env var set at all.
 */
final class FieldEncryptionUtil {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private FieldEncryptionUtil() {
    }

    static String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, loadKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + ciphertext.length);
            buffer.put(iv).put(ciphertext);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    static String decrypt(String encoded) {
        if (encoded == null) return null;
        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, loadKey(), new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // GCM's auth tag check fails closed on any corruption/tampering/wrong
            // key - this exception IS the tamper-detection, not a bug to work around.
            throw new IllegalStateException("Field decryption failed - wrong key, or data was altered", e);
        }
    }

    private static SecretKey loadKey() {
        String base64Key = System.getenv("FIELD_ENCRYPTION_KEY");
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "FIELD_ENCRYPTION_KEY is not set. Required by any entity field using "
                            + "EncryptedStringConverter. Generate one with: openssl rand -base64 32");
        }
        return new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }
}
