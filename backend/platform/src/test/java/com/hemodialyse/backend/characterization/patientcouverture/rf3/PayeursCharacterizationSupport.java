package com.hemodialyse.backend.characterization.patientcouverture.rf3;

import com.hemodialyse.backend.characterization.organisation.OrganisationCharacterizationSupport;
import org.junit.jupiter.api.AfterEach;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Harnais de la slice RF3a (caisses d'assurance, agences, centres payeurs), au-dessus du harnais Organisation (MockMvc sur le
 * contexte complet, sécurité réelle, H2 de test, goldens à alias nommés).
 * <p>
 * Point d'observation : HTTP sur l'administration générique ({@code /api/v1/admin/referentials/{caisses,agences,centres-payeurs}}),
 * sur les lectures communes ({@code /api/v1/referentials/{caisses,agences,centres-payeurs,centres-payeurs-details}}), plus l'état SQL
 * des trois tables et les caches {@code ref.*}. Aucun mock.
 */
public abstract class PayeursCharacterizationSupport extends OrganisationCharacterizationSupport {

    protected static final String CAISSES = "/api/v1/admin/referentials/caisses";
    protected static final String AGENCES = "/api/v1/admin/referentials/agences";
    protected static final String CENTRES = "/api/v1/admin/referentials/centres-payeurs";
    protected static final String COMMUNS = "/api/v1/referentials";
    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    /** Administrateur du centre B. */
    protected final RequestPostProcessor adminB = as(CENTRE_B, "ADMIN");
    /** Administrateur du centre A. */
    protected final RequestPostProcessor admin = as(CENTRE_A, "ADMIN");

    /** Sous-dossier des goldens de la classe de test. */
    protected abstract String dossier();

    @Override
    protected String theme() {
        return "../patientcouverture/rf3/" + dossier();
    }

    @Override
    @AfterEach
    protected void nettoyer() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B)) {
            jdbc.update("DELETE FROM patients WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM agence WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM caisse_assurance WHERE center_id = ?", centre);
        }
        super.nettoyer();
    }

    // ------------------------------------------------------------------ fixtures

    protected UUID caisse(UUID centre, String alias, String code, String nom, String type) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO caisse_assurance (id, center_id, code, nom, type_caisse) VALUES (?, ?, ?, ?, ?)", id, centre, code, nom,
                type);
        return id;
    }

    protected UUID agence(UUID centre, String alias, UUID caisse, String code, String nom) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO agence (id, center_id, caisse_id, code, nom) VALUES (?, ?, ?, ?, ?)", id, centre, caisse, code, nom);
        return id;
    }

    protected UUID centrePayeur(UUID centre, String alias, UUID agence, String code, String nom, String adresse) {
        UUID id = id(alias);
        jdbc.update("INSERT INTO centre_payeur (id, center_id, agence_id, code, nom, adresse) VALUES (?, ?, ?, ?, ?, ?)", id, centre,
                agence, code, nom, adresse);
        return id;
    }

    /** Patient du centre qui relève de ce centre payeur. */
    protected UUID patientDuCentrePayeur(UUID centre, String alias, UUID centrePayeur) {
        UUID id = id("PATIENT-" + alias);
        jdbc.update("INSERT INTO patients (id, center_id, code_patient, nom, prenom, sexe, date_admission, date_naissance, numero_assurance, "
                        + "type_patient, etat_patient, created_at, centre_payeur_id) VALUES (?, ?, ?, ?, ?, 'M', ?, ?, ?, 'NON_VACANCIER', 'PERMANENT', ?, ?)",
                id, centre, "RF3-" + alias, "Patient " + alias, "Test", java.sql.Date.valueOf(LocalDate.of(2025, 1, 1)),
                java.sql.Date.valueOf(LocalDate.of(1970, 1, 1)), "ASS-" + alias, BASE, centrePayeur);
        return id;
    }

    /** Une chaîne complète : caisse, agence, centre payeur (codes dérivés du préfixe). */
    protected UUID[] chaine(UUID centre, String prefixe) {
        UUID ca = caisse(centre, prefixe + "-CAISSE", prefixe + "-CA", "Caisse " + prefixe, "STANDARD");
        UUID ag = agence(centre, prefixe + "-AGENCE", ca, prefixe + "-AG", "Agence " + prefixe);
        UUID cp = centrePayeur(centre, prefixe + "-CP", ag, prefixe + "-CP", "Centre payeur " + prefixe, "1 rue " + prefixe);
        return new UUID[]{ca, ag, cp};
    }

    // ------------------------------------------------------------------ requêtes

    /** Corps de saisie : paires clé, valeur ; une valeur nulle n'est pas écrite. */
    protected static String valeurs(String... paires) {
        StringBuilder s = new StringBuilder("{'values':{");
        boolean premier = true;
        for (int i = 0; i < paires.length; i += 2) {
            if (paires[i + 1] == null) continue;
            s.append(premier ? "" : ",").append("'").append(paires[i]).append("':").append(chaine(paires[i + 1]));
            premier = false;
        }
        return s.append("}}").toString();
    }

    private static String chaine(String v) {
        return "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    protected Resp creerCaisse(String code, String nom, String type, RequestPostProcessor qui) throws Exception {
        return doPost(CAISSES, valeurs("code", code, "nom", nom, "typeCaisse", type), qui);
    }

    protected Resp modifierCaisse(UUID id, String code, String nom, String type, RequestPostProcessor qui) throws Exception {
        return doPut(CAISSES + "/" + id, valeurs("code", code, "nom", nom, "typeCaisse", type), qui);
    }

    protected Resp creerAgence(String code, String nom, String caisse, RequestPostProcessor qui) throws Exception {
        return doPost(AGENCES, valeurs("code", code, "nom", nom, "caisse", caisse), qui);
    }

    protected Resp modifierAgence(UUID id, String code, String nom, String caisse, RequestPostProcessor qui) throws Exception {
        return doPut(AGENCES + "/" + id, valeurs("code", code, "nom", nom, "caisse", caisse), qui);
    }

    protected Resp creerCentre(String code, String nom, String adresse, String agence, RequestPostProcessor qui) throws Exception {
        return doPost(CENTRES, valeurs("code", code, "nom", nom, "adresse", adresse, "agence", agence), qui);
    }

    protected Resp modifierCentre(UUID id, String code, String nom, String adresse, String agence, RequestPostProcessor qui)
            throws Exception {
        return doPut(CENTRES + "/" + id, valeurs("code", code, "nom", nom, "adresse", adresse, "agence", agence), qui);
    }

    protected Resp importer(String type, String contenu, boolean verifierSeulement, RequestPostProcessor qui) throws Exception {
        return doPostFichier("/api/v1/admin/referentials/" + type + "/import" + (verifierSeulement ? "" : "?dryRun=false"),
                contenu.getBytes(StandardCharsets.UTF_8), type + ".csv", qui);
    }

    // ------------------------------------------------------------------ observations

    /**
     * Statut et corps d'une réponse sur UNE ligne de golden (relisible un par un) : un refus rend son code, son message et ses
     * anomalies (code, champ, ligne, paramètres) ; une page rend chaque ligne (valeurs triées, références) ; un succès rend son JSON
     * compact ; un corps non JSON rend son texte.
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
                    s.append(" | ");
                    for (JsonNode ligne : corps.path("items")) {
                        s.append(ligne.path("values").toString()).append(' ').append(ligne.path("references").toString()).append(" ; ");
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
            List<Map.Entry<String, JsonNode>> entrees = new ArrayList<>();
            n.properties().forEach(entrees::add);
            entrees.sort(Map.Entry.comparingByKey());
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
        Matcher m = Pattern.compile("(.)\\1{19,}").matcher(texte);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, Matcher.quoteReplacement("<" + m.group().length() + " x " + m.group(1) + ">"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Lignes des trois tables des deux centres, triées, pour comparer l'état de la base. */
    protected void etatDesPayeurs(String nom) {
        goldenSql(nom, "SELECT 'caisse' AS niveau, center_id, code, nom, type_caisse AS detail, '-' AS parent FROM caisse_assurance "
                        + "WHERE center_id IN (?, ?) "
                        + "UNION ALL SELECT 'agence', a.center_id, a.code, a.nom, '-', COALESCE(c.code, '?') FROM agence a "
                        + "LEFT JOIN caisse_assurance c ON c.id = a.caisse_id WHERE a.center_id IN (?, ?) "
                        + "UNION ALL SELECT 'centre payeur', cp.center_id, cp.code, cp.nom, COALESCE(cp.adresse, '-'), COALESCE(a.code, '?') "
                        + "FROM centre_payeur cp LEFT JOIN agence a ON a.id = cp.agence_id WHERE cp.center_id IN (?, ?) "
                        + "ORDER BY 1, 2, 3, 4",
                CENTRE_A, CENTRE_B, CENTRE_A, CENTRE_B, CENTRE_A, CENTRE_B);
    }
}
