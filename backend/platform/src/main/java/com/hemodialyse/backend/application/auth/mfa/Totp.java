package com.hemodialyse.backend.application.auth.mfa;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.OptionalLong;

/**
 * Mots de passe à usage unique basés sur le temps (TOTP, RFC 6238 — HMAC-SHA1, 6 chiffres, pas de 30 s), compatibles
 * avec les applications d'authentification courantes. Classe pure : aucune dépendance Spring ni JPA.
 */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;
    /**
     * Tolérance d'horloge : un pas avant et un pas après le pas courant.
     */
    private static final int WINDOW = 1;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /**
     * Secret aléatoire de 160 bits, encodé en Base32 (saisissable dans une application d'authentification).
     */
    public static String generateSecret() {
        byte[] bytes = new byte[20];
        RANDOM.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /**
     * Code du pas de temps donné (utile aux tests ; l'application n'expose jamais le code attendu).
     */
    public static String codeAt(String base32Secret, long timeStep) {
        try {
            byte[] key = base32Decode(base32Secret);
            byte[] msg = new byte[8];
            long v = timeStep;
            for (int i = 7; i >= 0; i--) {
                msg[i] = (byte) (v & 0xff);
                v >>= 8;
            }
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(msg);
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            int otp = binary % (int) Math.pow(10, DIGITS);
            return String.format("%0" + DIGITS + "d", otp);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 indisponible", e);
        }
    }

    public static long stepAt(long epochSeconds) {
        return epochSeconds / STEP_SECONDS;
    }

    /**
     * Vérifie un code saisi. Renvoie le pas de temps correspondant s'il est valide (les appelants mémorisent le
     * dernier pas accepté pour interdire de rejouer un code), sinon vide. Comparaison à temps constant.
     */
    public static OptionalLong verify(String base32Secret, String code, long epochSeconds) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = stepAt(epochSeconds);
        OptionalLong match = OptionalLong.empty();
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (constantTimeEquals(codeAt(base32Secret, step), code)) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }

    /**
     * URI {@code otpauth://} à saisir ou à convertir en QR code dans l'application d'authentification.
     */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String s) {
        String clean = s.replace(" ", "").replace("=", "").toUpperCase(java.util.Locale.ROOT);
        byte[] out = new byte[clean.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : clean.toCharArray()) {
            int val = BASE32.indexOf(c);
            if (val < 0) {
                throw new IllegalArgumentException("Secret Base32 invalide");
            }
            buffer = (buffer << 5) | val;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) ((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
