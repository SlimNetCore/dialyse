package com.hemodialyse.backend.characterization.identite.idnlicence;

import com.hemodialyse.backend.application.license.LicenseTokenService;
import com.hemodialyse.backend.characterization.identite.idnlecture.IdnLectureSupport;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Filet IDN-licence : la licence (émission par centre et par société, liste, statut, activation, révocation, vérification en ligne,
 * amorçage de développement) lit les centres et les sociétés de l'Organisation ; les comptes portent des rattachements aux centres.
 * Même harnais que IDN-lecture (MockMvc, sécurité réelle, H2, aucun mock du code caractérisé), goldens sous {@code identite/idn-licence}.
 * <p>
 * Non-déterminisme : la clé de licence (jeton RS256 signé par la clé éphémère du processus) est TOUJOURS remplacée par
 * {@code <CLE_SIGNEE>} ; l'identifiant du jeton ({@code jti}), les identifiants générés et les horodatages sont normalisés par le
 * harnais ; les durées en millisecondes des messages de la bibliothèque de jetons sont masquées. Les dates des scénarios sont des
 * constantes lointaines : aucune date du jour n'est enregistrée et aucun scénario ne compare deux horodatages.
 */
public abstract class IdnLicenceSupport extends IdnLectureSupport {

    protected static final String DEBUT = "2026-01-01T00:00:00Z";
    protected static final String FIN = "2099-12-31T00:00:00Z";

    @Autowired
    protected LicenseTokenService jetons;

    /** Centres sans ligne dans {@code centers} dont des licences ont été insérées (nettoyés par {@link #retirerLesLicences()}). */
    private final List<UUID> centresSansLigne = new ArrayList<>();

    @Override
    protected String racineDesGoldens() {
        return "idn-licence";
    }

    /** Exécuté avant le nettoyage du harnais parent : les licences sont retirées avant les centres qu'elles référencent. */
    @AfterEach
    void retirerLesLicences() {
        supprimerLesLicencesDeLaFixture();
    }

    protected void supprimerLesLicencesDeLaFixture() {
        jdbc.update("DELETE FROM license WHERE center_id IN (SELECT id FROM centers WHERE code LIKE ?)", CODE_PREFIXE + "%");
        for (UUID centre : centresSansLigne) {
            jdbc.update("DELETE FROM license WHERE center_id = ?", centre);
        }
        centresSansLigne.clear();
    }

    // ------------------------------------------------------------------ fixtures de licence

    /** Une clé signée, « générée ailleurs » (jamais enregistrée) : le centre, le type et le nombre de postes sont ceux du jeton. */
    protected String cle(UUID centre, String code, String type, int postes) {
        return jetons.issue(centre, code, type, postes, Instant.parse(DEBUT), Instant.parse(FIN)).compact();
    }

    protected String cleExpiree(UUID centre, String code) {
        return jetons.issue(centre, code, "STANDARD", 5, Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-02-01T00:00:00Z"))
                .compact();
    }

    /**
     * Insère une licence en SQL. {@code jourDeCreation} (1 à 28) fixe {@code created_at} (janvier 2026) : l'ordre « plus récente
     * d'abord » de l'API est ainsi déterministe sans comparer d'horodatages. {@code activation} : date d'activation (ISO) ou null.
     */
    protected UUID licence(String alias, UUID centre, String statut, String cle, String jti, int jourDeCreation, String activation) {
        UUID id = id(alias);
        if (!jdbc.queryForList("SELECT 1 FROM centers WHERE id = ?", centre).isEmpty()) {
            // centre présent : nettoyé avec la fixture
        } else {
            centresSansLigne.add(centre);
        }
        jdbc.update("INSERT INTO license (id, center_id, license_key, jti, type, max_users, valid_from, valid_until, status, "
                        + "activated_at, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, centre, cle, jti, "STANDARD", 5, java.sql.Timestamp.from(Instant.parse(DEBUT)),
                java.sql.Timestamp.from(Instant.parse(FIN)), statut,
                activation == null ? null : java.sql.Timestamp.from(Instant.parse(activation)),
                java.sql.Timestamp.from(Instant.parse(String.format("2026-01-%02dT10:00:00Z", jourDeCreation))));
        return id;
    }

    protected void declarerCentreSansLigne(UUID centre, String alias) {
        aliasNommes.put(centre.toString(), "<" + alias + ">");
        centresSansLigne.add(centre);
    }

    // ------------------------------------------------------------------ corps de requête

    protected static String emission(UUID centre, String type, int postes) {
        return "{'centerId':" + (centre == null ? "null" : "'" + centre + "'") + ",'type':'" + type + "','maxUsers':" + postes
                + ",'validFrom':'" + DEBUT + "','validUntil':'" + FIN + "'}";
    }

    protected static String emissionSociete(UUID societe, List<UUID> centres, String type, int postes) {
        return "{'societeId':" + (societe == null ? "null" : "'" + societe + "'")
                + (centres == null ? "" : ",'centerIds':[" + String.join(",", centres.stream().map(c -> "'" + c + "'").toList()) + "]")
                + ",'type':'" + type + "','maxUsers':" + postes + ",'validFrom':'" + DEBUT + "','validUntil':'" + FIN + "'}";
    }

    // ------------------------------------------------------------------ journal

    /**
     * Une réponse : statut, type de contenu, corps normalisé (clés de licence masquées). {@code garde} (nullable) ne retient, dans une
     * liste, que les éléments de la fixture ; {@code tri} (nullable) trie les éléments retenus par cette clé, SINON l'ordre de l'API est
     * conservé (c'est alors un comportement observé).
     */
    protected void tracer(String libelle, Resp r, Predicate<JsonNode> garde, String tri) {
        ObjectNode n = nouvelleEtape(libelle);
        n.put("status", r.status());
        n.put("contentType", r.contentType());
        if (r.raw() == null || r.raw().isBlank()) {
            n.putNull("body");
            return;
        }
        JsonNode corps;
        try {
            corps = mapper.readTree(r.raw());
        } catch (RuntimeException e) {
            n.put("bodyText", r.raw());
            return;
        }
        if (corps.isArray() && garde != null) {
            List<JsonNode> retenus = new ArrayList<>();
            corps.forEach(e -> {
                if (garde.test(e)) retenus.add(e);
            });
            if (tri != null) retenus.sort(Comparator.comparing((JsonNode e) -> e.path(tri).asString("")));
            ArrayNode a = n.putArray("elementsDeLaFixture");
            retenus.forEach(e -> a.add(masquer(e)));
            return;
        }
        n.set("body", masquer(corps));
    }

    protected void tracer(String libelle, Resp r) {
        tracer(libelle, r, null, null);
    }

    /** Les licences de la fixture, par centre (identifiant de centre de ce filet). */
    protected Predicate<JsonNode> licencesDeLaFixture() {
        return e -> aliasNommes.containsKey(e.path("centerId").asString(""));
    }

    private JsonNode masquer(JsonNode n) {
        if (n.isString()) {
            return mapper.getNodeFactory().stringNode(n.asString().replaceAll("\\d+ milliseconds", "<N> milliseconds"));
        }
        if (n.isObject()) {
            ObjectNode o = (ObjectNode) n;
            for (Map.Entry<String, JsonNode> e : List.copyOf(o.properties())) {
                JsonNode v = e.getValue();
                if ("licenseKey".equals(e.getKey()) && v.isString() && v.asString().startsWith("eyJ")) {
                    o.put("licenseKey", "<CLE_SIGNEE>");
                } else {
                    o.set(e.getKey(), masquer(v));
                }
            }
        } else if (n.isArray()) {
            ArrayNode a = (ArrayNode) n;
            for (int i = 0; i < a.size(); i++) a.set(i, masquer(a.get(i)));
        }
        return n;
    }
}
