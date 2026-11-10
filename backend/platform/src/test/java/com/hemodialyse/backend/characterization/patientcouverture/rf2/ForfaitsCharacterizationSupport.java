package com.hemodialyse.backend.characterization.patientcouverture.rf2;

import com.hemodialyse.backend.characterization.organisation.OrganisationCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Harnais de la slice RF2 (catalogue de forfaits), au-dessus du harnais Organisation (MockMvc sur le contexte complet, sécurité
 * réelle, H2 de test, goldens à alias nommés).
 * <p>
 * Point d'observation : HTTP sur l'administration générique ({@code /api/v1/admin/referentials/forfaits}), sur les lectures
 * communes ({@code /api/v1/referentials/forfaits}) et sur leur jumelle canonique ({@code /api/v1/organisation/referentiels/forfaits}),
 * plus l'état SQL de {@code forfait} et le cache {@code ref.forfaits}. Aucun mock.
 */
public abstract class ForfaitsCharacterizationSupport extends OrganisationCharacterizationSupport {

    protected static final String ADMIN = "/api/v1/admin/referentials/forfaits";
    protected static final String COMMUNS = "/api/v1/referentials";
    protected static final String CANONIQUE = "/api/v1/organisation/referentiels";
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    /** Administrateur du centre B. */
    protected final org.springframework.test.web.servlet.request.RequestPostProcessor adminB = as(CENTRE_B, "ADMIN");

    /** Sous-dossier des goldens de la classe de test. */
    protected abstract String dossier();

    @Override
    protected String theme() {
        return "../patientcouverture/rf2/" + dossier();
    }

    @Override
    @AfterEach
    protected void nettoyer() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM prise_en_charge WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM facture_lignes WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM seances WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM forfait WHERE center_id = ?", centre);
        }
        super.nettoyer();
    }

    // ------------------------------------------------------------------ fixtures

    protected UUID forfait(UUID centre, String alias, String code, String libelle, String prix) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre, code, libelle,
                prix == null ? null : new BigDecimal(prix));
        return id;
    }

    /** Prise en charge du centre qui porte le forfait demandé et/ou le forfait effectif (nuls admis). */
    protected UUID pec(UUID centre, String alias, UUID demande, UUID effectif) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, created_at, forfait_demande_id, forfait_effectif_id, patient_id, statut) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'DEMANDEE')", id, centre, BASE, demande, effectif, id("PATIENT-" + alias));
        return id;
    }

    /** Ligne de facture du centre qui reprend ce forfait. */
    protected UUID ligneDeFacture(UUID centre, String alias, UUID forfait) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, seance_count, line_ht) "
                + "VALUES (?, ?, ?, ?, 'Forfait', 100.00, 1, 100.00)", id, id("FACTURE-" + alias), centre, forfait);
        return id;
    }

    /** Séance du centre à laquelle ce forfait a été imposé (instantané, sans clé étrangère). */
    protected UUID seanceImposee(UUID centre, String alias, UUID forfait) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, forfait_override_id) "
                        + "VALUES (?, ?, ?, ?, 'PLANIFIEE', ?, ?)", id, id("PATIENT-" + alias), centre,
                java.sql.Date.valueOf(LocalDate.of(2026, 1, 5)), BASE, forfait);
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    protected static String valeurs(String code, String libelle, String prix) {
        StringBuilder s = new StringBuilder("{'values':{");
        boolean premier = true;
        if (code != null) {
            s.append("'code':").append(chaine(code));
            premier = false;
        }
        if (libelle != null) {
            s.append(premier ? "" : ",").append("'libelle':").append(chaine(libelle));
            premier = false;
        }
        if (prix != null) s.append(premier ? "" : ",").append("'prix':").append(chaine(prix));
        return s.append("}}").toString();
    }

    private static String chaine(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    protected Resp creer(String code, String libelle, String prix, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {
        return doPost(ADMIN, valeurs(code, libelle, prix), who);
    }

    protected Resp modifier(UUID id, String code, String libelle, String prix,
                            org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        return doPut(ADMIN + "/" + id, valeurs(code, libelle, prix), who);
    }

    protected Resp importer(String contenu, boolean verifierSeulement, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {
        return doPostFichier(ADMIN + "/import" + (verifierSeulement ? "" : "?dryRun=false"),
                contenu.getBytes(StandardCharsets.UTF_8), "forfaits.csv", who);
    }

    // ------------------------------------------------------------------ observations

    /**
     * Statut et corps d'une réponse sur UNE ligne de golden (relisible un par un) : un refus rend son code, son message et ses
     * anomalies (code, champ, ligne, paramètres) ; un succès rend son JSON compact ; un corps non JSON rend son texte.
     */
    protected JsonNode vue(Resp r) {
        StringBuilder s = new StringBuilder().append(r.status());
        if (r.raw() != null && !r.raw().isBlank()) {
            try {
                JsonNode corps = trie(mapper.readTree(r.raw()));
                if (corps.isObject() && corps.has("code") && corps.has("title")) {
                    String code = corps.path("code").asString();
                    boolean technique = "INTERNAL_ERROR".equals(code) || "DATA_ACCESS_ERROR".equals(code);
                    s.append(' ').append(code).append(" | ")
                            .append(technique ? "<message technique non verrouille>" : corps.path("detail").asString());
                    for (JsonNode a : corps.path("issues")) {
                        s.append(" | ").append(a.path("code").asString()).append('/').append(a.path("field").asString())
                                .append("/ligne ").append(a.path("row").asString()).append(' ').append(a.path("params").toString());
                    }
                } else if (corps.isObject() && corps.has("items") && corps.has("total")) {
                    // page : code / libelle / prix de chaque ligne dans l'ordre rendu (les identifiants ne sont pas observés ici)
                    s.append(" | ");
                    for (JsonNode ligne : corps.path("items")) {
                        JsonNode v = ligne.path("values");
                        s.append(v.path("code").asString()).append(" / ").append(v.path("libelle").asString()).append(" / ")
                                .append(v.path("prix").isNull() ? "-" : v.path("prix").asString()).append(" ; ");
                    }
                    s.append("total=").append(corps.path("total").asString()).append(" page=").append(corps.path("page").asString())
                            .append(" size=").append(corps.path("size").asString());
                } else {
                    s.append(" | ").append(corps.toString());
                }
            } catch (RuntimeException e) {
                s.append(" | texte ").append(r.raw().replace("\r", "").replace("\n", "\\n"));
            }
        }
        return mapper.getNodeFactory().stringNode(abreger(s.toString()));
    }

    /** Clés d'objet triées à tous les niveaux (l'ordre d'une {@code Map.of} change d'une JVM à l'autre). */
    protected JsonNode trie(JsonNode n) {
        if (n.isObject()) {
            ObjectNode o = mapper.createObjectNode();
            java.util.List<java.util.Map.Entry<String, JsonNode>> entrees = new java.util.ArrayList<>();
            n.properties().forEach(entrees::add);
            entrees.sort(java.util.Map.Entry.comparingByKey());
            entrees.forEach(e -> o.set(e.getKey(), trie(e.getValue())));
            return o;
        }
        if (n.isArray()) {
            tools.jackson.databind.node.ArrayNode a = mapper.createArrayNode();
            n.forEach(x -> a.add(trie(x)));
            return a;
        }
        return n;
    }

    /** Une suite de 20 caractères identiques ou plus devient {@code <N x c>} (libellés de 255 caractères). */
    private static String abreger(String texte) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.)\\1{19,}").matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("<" + m.group().length() + " x " + m.group(1) + ">"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Lignes de {@code forfait} des deux centres, triées, pour comparer l'état de la base. */
    protected void etatDesForfaits(String nom) {
        goldenSql(nom, "SELECT center_id, code, "
                        + "CASE WHEN LENGTH(libelle) > 40 THEN CONCAT(LEFT(libelle, 10), '...[', LENGTH(libelle), ']') ELSE libelle END AS libelle, "
                        + "CAST(prix AS VARCHAR) AS prix "
                        + "FROM forfait WHERE center_id IN (?, ?) ORDER BY center_id, code, libelle",
                CENTRE_A, CENTRE_B);
    }
}
