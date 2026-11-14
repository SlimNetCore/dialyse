package com.hemodialyse.backend.characterization.identite.idnlicence;

import com.hemodialyse.backend.infrastructure.scheduling.LicenseOnlineCheckJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * IDN-licence, vérification en ligne d'une instance CLIENTE (clé publique seule, URL d'autorité configurée) : la période de grâce hors
 * ligne, la révocation à distance, l'échec réseau ignoré. L'« autorité » est un serveur HTTP local du test (boucle locale, aucun réseau
 * réel) ; les clés de licence sont celles d'une paire éphémère générée par le test. Comportement ACTUEL.
 */
class VerificationEnLigneDUneInstanceCliente_IdnLicenceCharacterizationIntegrationTest extends IdnLicenceSupport {

    private static final String L = "/api/v1/licenses";
    private static final ServerSocket AUTORITE = demarrerAutorite();
    private static final KeyPair CLES = genererLesCles();

    @Autowired
    private LicenseOnlineCheckJob verificationEnLigne;

    private UUID alpha1, alpha2, delta1, delta2, delta3, delta4;
    private UUID administrateur;

    @DynamicPropertySource
    static void instanceCliente(DynamicPropertyRegistry registre) {
        registre.add("app.license.public-key", () -> Base64.getEncoder().encodeToString(CLES.getPublic().getEncoded()));
        registre.add("app.license.authority.enabled", () -> "false");
        registre.add("app.license.authority-url", () -> "http://127.0.0.1:" + AUTORITE.getLocalPort());
    }

    /** L'instance cliente ne signe pas : le test signe, avec la clé privée de SA paire, comme le ferait l'autorité du fournisseur. */
    @Override
    protected String cle(UUID centre, String code, String type, int postes) {
        return io.jsonwebtoken.Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(centre.toString())
                .claim("centerCode", code)
                .claim("type", type)
                .claim("maxUsers", postes)
                .notBefore(java.util.Date.from(Instant.parse(DEBUT)))
                .expiration(java.util.Date.from(Instant.parse(FIN)))
                .signWith(CLES.getPrivate(), io.jsonwebtoken.Jwts.SIG.RS256)
                .compact();
    }

    private static KeyPair genererLesCles() {
        try {
            KeyPairGenerator generateur = KeyPairGenerator.getInstance("RSA");
            generateur.initialize(2048);
            return generateur.generateKeyPair();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * « Autorité » minimale sur la boucle locale (socket bloquante : le sélecteur NIO du serveur du JDK ne démarre pas sur ce poste).
     * Répond à {@code GET ...?jti=<jti>} : révoquée pour le jti « distant-revoque », erreur 500 pour « erreur », non révoquée sinon.
     */
    private static ServerSocket demarrerAutorite() {
        try {
            ServerSocket serveur = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
            Thread fil = new Thread(() -> {
                while (!serveur.isClosed()) {
                    try (Socket client = serveur.accept()) {
                        client.setSoTimeout(5000);
                        var lecteur = new java.io.BufferedReader(new java.io.InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                        String requete = lecteur.readLine();
                        for (String ligne = lecteur.readLine(); ligne != null && !ligne.isEmpty(); ligne = lecteur.readLine()) {
                            // en-têtes ignorés
                        }
                        int statut = 200;
                        String corps = "{\"revoked\":false}";
                        if (requete != null && requete.contains("jti-fictif-distant-revoque")) {
                            corps = "{\"revoked\":true}";
                        } else if (requete != null && requete.contains("jti-fictif-erreur")) {
                            statut = 500;
                            corps = "{}";
                        }
                        byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
                        String entete = "HTTP/1.1 " + statut + (statut == 200 ? " OK" : " Internal Server Error")
                                + "\r\nContent-Type: application/json\r\nContent-Length: " + octets.length + "\r\nConnection: close\r\n\r\n";
                        client.getOutputStream().write(entete.getBytes(StandardCharsets.UTF_8));
                        client.getOutputStream().write(octets);
                        client.getOutputStream().flush();
                    } catch (IOException e) {
                        // serveur fermé ou client interrompu
                    }
                }
            }, "autorite-de-licence-du-test");
            fil.setDaemon(true);
            fil.start();
            return serveur;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @AfterAll
    static void arreter() throws IOException {
        AUTORITE.close();
    }

    @Override
    protected String dossier() {
        return "verification";
    }

    @BeforeEach
    void monde() {
        UUID alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        UUID delta = societeNommee("SOCIETE_DELTA", "DELTA", "Delta Dialyse", true);
        alpha1 = centreNomme("CENTRE_ALPHA_1", alpha, "A1", "Alpha 1", true);
        alpha2 = centreNomme("CENTRE_ALPHA_2", alpha, "A2", "Alpha 2", true);
        delta1 = centreNomme("CENTRE_DELTA_1", delta, "D1", "Delta 1", true);
        delta2 = centreNomme("CENTRE_DELTA_2", delta, "D2", "Delta 2", true);
        delta3 = centreNomme("CENTRE_DELTA_3", delta, "D3", "Delta 3", true);
        delta4 = centreNomme("CENTRE_DELTA_4", delta, "D4", "Delta 4", true);
        administrateur = compte("COMPTE_ADMIN", "admin", true, "ADMIN");
        rattacher(administrateur, alpha1);
        supprimerLesLicencesDeLaFixture();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin(UUID centre) {
        return session(administrateur, centre, null, "ADMIN");
    }

    @Test
    void la_periode_de_grace_hors_ligne_la_revocation_a_distance_et_l_echec_reseau() throws Exception {
        String ancienne = "2026-01-03T10:00:00Z";
        String recente = Instant.now().minus(1, ChronoUnit.DAYS).toString();
        licence("LICENCE_SAINE", alpha1, "ACTIVE", cle(alpha1, CODE_PREFIXE + "A1", "STANDARD", 5), "jti-fictif-sain", 3, ancienne);
        licence("LICENCE_REVOQUEE_A_DISTANCE", alpha2, "ACTIVE", cle(alpha2, CODE_PREFIXE + "A2", "STANDARD", 5), "jti-fictif-distant-revoque", 3,
                ancienne);
        licence("LICENCE_EN_ERREUR", delta1, "ACTIVE", cle(delta1, CODE_PREFIXE + "D1", "STANDARD", 5), "jti-fictif-erreur", 3, ancienne);
        licence("LICENCE_RECENTE", delta2, "ACTIVE", cle(delta2, CODE_PREFIXE + "D2", "STANDARD", 5), "jti-fictif-recent", 3, recente);
        licence("LICENCE_JAMAIS_ACTIVEE", delta3, "ACTIVE", cle(delta3, CODE_PREFIXE + "D3", "STANDARD", 5), "jti-fictif-sans-activation", 3, null);
        licence("LICENCE_DEJA_REVOQUEE", delta4, "REVOKED", "cle-fictive", "jti-fictif-deja-revoque", 3, null);

        tracer("avant : licence ancienne jamais verifiee (hors periode de grace)", doGet(L + "/status", admin(alpha1)));
        tracer("avant : licence recente", doGet(L + "/status", admin(delta2)));
        tracer("avant : licence sans date d'activation ni verification", doGet(L + "/status", admin(delta3)));

        verificationEnLigne.checkAll();

        traceSql("apres la verification en ligne (aucune date enregistree dans le golden)",
                "SELECT center_id, status, revoked_reason, last_online_check_at IS NOT NULL AS verification_enregistree FROM license "
                        + "WHERE center_id IN (?,?,?,?,?,?) ORDER BY center_id", alpha1, alpha2, delta1, delta2, delta3, delta4);
        tracer("apres : licence verifiee aupres de l'autorite", doGet(L + "/status", admin(alpha1)));
        tracer("apres : licence revoquee a distance", doGet(L + "/status", admin(alpha2)));
        tracer("apres : l'autorite en erreur ne change rien", doGet(L + "/status", admin(delta1)));
        tracer("autorite : une instance cliente repond 404", doGet(L + "/authority/verify?jti=jti-fictif-sain", superadmin()));
        tracer("emission : une instance cliente ne detient pas la cle privee de l'autorite",
                doPost(L + "/issue", emission(alpha1, "STANDARD", 5), superadmin()));
        verrouiller("instance_cliente");
    }
}
