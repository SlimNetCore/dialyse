package com.hemodialyse.backend.application.auth.mfa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffre les secrets TOTP au repos (AES-256-GCM). La clé est dérivée du secret de l'application
 * ({@code JWT_SECRET}) : une fuite de la seule base de données ne suffit donc pas à générer des codes.
 */
@Component
public class MfaSecretCipher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;
    private final SecretKeySpec key;

    public MfaSecretCipher(@Value("${app.jwt.secret}") String applicationSecret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("mfa-secret-key:" + applicationSecret).getBytes(StandardCharsets.UTF_8));
            this.key = new SecretKeySpec(digest, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(enc, 0, all, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Chiffrement impossible", e);
        }
    }

    public String decrypt(String cipherText) {
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Secret 2FA illisible (JWT_SECRET modifié ?)", e);
        }
    }
}
