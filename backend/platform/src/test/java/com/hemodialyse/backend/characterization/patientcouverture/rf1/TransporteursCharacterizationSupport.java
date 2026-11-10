package com.hemodialyse.backend.characterization.patientcouverture.rf1;

import com.hemodialyse.backend.characterization.organisation.OrganisationCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Harnais de la slice RF1 (transporteurs et catégories de transport), au-dessus du harnais Organisation (MockMvc sur le contexte
 * complet, sécurité réelle, H2 de test, goldens à alias nommés).
 * <p>
 * Point d'observation : HTTP sur l'administration générique ({@code /api/v1/admin/referentials/transporteurs}), sur les lectures
 * communes ({@code /api/v1/referentials/transporteurs}, {@code /categories-transport}) et sur leur jumelle canonique
 * ({@code /api/v1/organisation/referentiels/...}), plus l'état SQL de {@code transporteur} et de {@code categorie_transport} et les
 * caches {@code ref.*}. Aucun mock.
 */
public abstract class TransporteursCharacterizationSupport extends OrganisationCharacterizationSupport {

    protected static final String ADMIN = "/api/v1/admin/referentials/transporteurs";
    protected static final String COMMUNS = "/api/v1/referentials";
    protected static final String CANONIQUE = "/api/v1/organisation/referentiels";
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    /** Administrateur du centre B (le super administrateur est refusé en amont par le filtre de rôles sur ces routes). */
    protected final org.springframework.test.web.servlet.request.RequestPostProcessor adminB = as(CENTRE_B, "ADMIN");

    /** Sous-dossier des goldens de la classe de test. */
    protected abstract String dossier();

    @Override
    protected String theme() {
        return "../patientcouverture/rf1/" + dossier();
    }

    @Override
    @AfterEach
    protected void nettoyer() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM transporteur WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM categorie_transport WHERE center_id = ?", centre);
        }
        super.nettoyer();
    }

    // ------------------------------------------------------------------ fixtures

    protected UUID transporteur(UUID centre, String alias, String nom, String telephone) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO transporteur (id, center_id, nom, telephone) VALUES (?, ?, ?, ?)", id, centre, nom, telephone);
        return id;
    }

    protected UUID categorie(UUID centre, String alias, String libelle) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO categorie_transport (id, center_id, libelle) VALUES (?, ?, ?)", id, centre, libelle);
        return id;
    }

    /** Patient du centre qui utilise les transporteurs donnés (nuls admis). */
    protected UUID patient(UUID centre, String alias, UUID aller, UUID retour) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, date_naissance, numero_assurance, "
                        + "type_patient, etat_patient, transporteur_aller_id, transporteur_retour_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, 'M', ?, ?, ?, 'NON_VACANCIER', 'PERMANENT', ?, ?, ?)",
                id, centre, "RF1-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(LocalDate.of(2020, 1, 1)),
                java.sql.Date.valueOf(LocalDate.of(1970, 1, 1)), "RF1-ASS-" + alias, aller, retour, BASE);
        return id;
    }

    // ------------------------------------------------------------------ requêtes

    protected static String valeurs(String nom, String telephone) {
        StringBuilder s = new StringBuilder("{'values':{");
        if (nom != null) s.append("'nom':").append(chaine(nom));
        if (telephone != null) s.append(nom != null ? "," : "").append("'telephone':").append(chaine(telephone));
        return s.append("}}").toString();
    }

    private static String chaine(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    protected Resp creer(String nom, String telephone, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        return doPost(ADMIN, valeurs(nom, telephone), who);
    }

    protected Resp modifier(UUID id, String nom, String telephone, org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        return doPut(ADMIN + "/" + id, valeurs(nom, telephone), who);
    }

    protected Resp importer(String contenu, boolean verifierSeulement, org.springframework.test.web.servlet.request.RequestPostProcessor who)
            throws Exception {
        return doPostFichier(ADMIN + "/import" + (verifierSeulement ? "" : "?dryRun=false"),
                contenu.getBytes(StandardCharsets.UTF_8), "transporteurs.csv", who);
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
                    // page : nom / telephone de chaque ligne dans l'ordre rendu (les identifiants ne sont pas observés ici)
                    s.append(" | ");
                    for (JsonNode ligne : corps.path("items")) {
                        s.append(ligne.path("values").path("nom").asString()).append(" / ")
                                .append(ligne.path("values").path("telephone").isNull() ? "-" : ligne.path("values").path("telephone").asString())
                                .append(" ; ");
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

    /** Une suite de 20 caractères identiques ou plus devient {@code <N x c>} (noms de 255 caractères). */
    private static String abreger(String texte) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(.)\\1{19,}").matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("<" + m.group().length() + " x " + m.group(1) + ">"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Lignes de {@code transporteur} des deux centres, triées, pour comparer l'état de la base. */
    protected void etatDesTransporteurs(String nom) {
        // les valeurs de plus de 40 caracteres sont abregees (debut + longueur) pour que le golden reste lisible
        goldenSql(nom, "SELECT center_id, "
                        + "CASE WHEN LENGTH(nom) > 40 THEN CONCAT(LEFT(nom, 10), '...[', LENGTH(nom), ']') ELSE nom END AS nom, "
                        + "CASE WHEN LENGTH(telephone) > 40 THEN CONCAT(LEFT(telephone, 10), '...[', LENGTH(telephone), ']') ELSE telephone END AS telephone "
                        + "FROM transporteur WHERE center_id IN (?, ?) ORDER BY center_id, nom, telephone",
                CENTRE_A, CENTRE_B);
    }
}
