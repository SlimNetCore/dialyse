package com.hemodialyse.backend.infrastructure.security.license;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Holds the RSA keypair used to sign/verify license tokens.
 *
 * <p>Deployment model: the vendor's own "authority" instance is configured with BOTH
 * {@code app.license.private-key} and {@code app.license.public-key} (and
 * {@code app.license.authority.enabled=true}) — it is the only place licenses are
 * issued. Every client instance deployed at a center is configured with ONLY the
 * public key: it can verify a license's signature but can never forge or extend one,
 * even with full access to its own database.
 *
 * <p>If no keys are configured at all (local/dev use), an ephemeral keypair is
 * generated in memory at startup so the feature is testable end-to-end without any
 * setup — this is intentionally NOT persisted and must never be relied upon outside
 * development, since licenses issued against it stop verifying the moment the process
 * restarts.
 */
@Component
public class LicenseKeyProperties {

    private static final Logger log = LoggerFactory.getLogger(LicenseKeyProperties.class);

    @Value("${app.license.public-key:}")
    private String publicKeyPem;

    @Value("${app.license.private-key:}")
    private String privateKeyPem;

    @Value("${app.license.authority.enabled:false}")
    private boolean authorityEnabled;

    @Value("${app.license.authority-url:}")
    private String authorityUrl;

    @Value("${app.license.offline-grace-days:5}")
    private int offlineGraceDays;

    private PublicKey publicKey;
    private PrivateKey privateKey;
    private boolean ephemeralDevMode;

    @PostConstruct
    void init() {
        boolean hasPublic = StringUtils.hasText(publicKeyPem);
        boolean hasPrivate = StringUtils.hasText(privateKeyPem);

        if (!hasPublic && !hasPrivate) {
            log.warn("!!! app.license.public-key / private-key are not configured — generating an EPHEMERAL " +
                    "in-memory RSA keypair for this run only. This is fine for local development but MUST NOT " +
                    "be used in production: licenses issued now will fail verification after the next restart.");
            KeyPair keyPair = generateEphemeralKeyPair();
            this.publicKey = keyPair.getPublic();
            this.privateKey = keyPair.getPrivate();
            this.authorityEnabled = true;
            this.ephemeralDevMode = true;
            return;
        }

        if (hasPublic) {
            this.publicKey = parsePublicKey(publicKeyPem);
        }
        if (hasPrivate) {
            this.privateKey = parsePrivateKey(privateKeyPem);
        }
    }

    public PublicKey publicKey() {
        if (publicKey == null) {
            throw new IllegalStateException("Clé publique de licence non configurée (app.license.public-key)");
        }
        return publicKey;
    }

    public PrivateKey privateKey() {
        if (privateKey == null) {
            throw new IllegalStateException(
                    "Clé privée de licence indisponible sur cette instance — seule l'instance autorité " +
                            "(app.license.authority.enabled=true) peut émettre des licences.");
        }
        return privateKey;
    }

    public boolean isAuthority() {
        return authorityEnabled && privateKey != null;
    }

    /**
     * True only when no real keys were configured and a throwaway keypair was generated for this run.
     */
    public boolean isEphemeralDevMode() {
        return ephemeralDevMode;
    }

    public String authorityUrl() {
        return authorityUrl;
    }

    public int offlineGraceDays() {
        return offlineGraceDays;
    }

    private KeyPair generateEphemeralKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algorithme RSA indisponible", e);
        }
    }

    private PrivateKey parsePrivateKey(String pem) {
        try {
            String cleaned = stripPemHeaders(pem);
            byte[] bytes = Base64.getDecoder().decode(cleaned);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePrivate(new PKCS8EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Clé privée de licence invalide (attendu: PKCS8 PEM, RSA)", e);
        }
    }

    private PublicKey parsePublicKey(String pem) {
        try {
            String cleaned = stripPemHeaders(pem);
            byte[] bytes = Base64.getDecoder().decode(cleaned);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return factory.generatePublic(new X509EncodedKeySpec(bytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | IllegalArgumentException e) {
            throw new IllegalStateException("Clé publique de licence invalide (attendu: X.509 PEM, RSA)", e);
        }
    }

    private String stripPemHeaders(String pem) {
        return pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
    }
}
