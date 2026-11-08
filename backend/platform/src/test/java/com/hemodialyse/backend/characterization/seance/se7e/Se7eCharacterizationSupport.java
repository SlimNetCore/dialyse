package com.hemodialyse.backend.characterization.seance.se7e;

import com.hemodialyse.backend.characterization.seance.se7d.Se7dCharacterizationSupport;
import com.hemodialyse.backend.infrastructure.security.UserPrincipal;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Harnais de la sous-slice SE7e (derniers lecteurs SQL de {@code seances} côté plateforme : pilotage de la Direction, statistiques du
 * patient, cahier de dialyse imprimé et son état Jasper, reprise de données (import et annulation d'un lot), usages d'un forfait du
 * référentiel), au-dessus de SE7d (donc SE7b, SE7a à SE1 et Planification).
 * <p>
 * Points d'observation : HTTP (MockMvc, contexte Spring complet, sécurité réelle + {@code RoleScopeFilter}, filtre d'écart de 2 ms, H2
 * {@code test}) ; exports binaires décodés en texte (PDF via PDFBox), horodatages d'édition masqués ({@code <EDITE_LE>},
 * {@code <GENERE_LE>}) ; état SQL de la table réelle {@code seances} pour la reprise. Aucun mock du code caractérisé, aucun seam, aucun code
 * de production modifié.
 * <p>
 * Dates : FIXES uniquement ({@code <D+n>} = 2026-01-01 + n, {@code <D-n>} avant). Aucun test ne lit l'heure ni la zone système ; les
 * bornes « aujourd'hui » du code (fin de période par défaut, date future refusée à la reprise, absences postérieures à aujourd'hui) sont
 * toujours postérieures aux dates fixes posées. DONNÉES FICTIVES uniquement.
 */
public abstract class Se7eCharacterizationSupport extends Se7dCharacterizationSupport {

    protected static final UUID CENTRE_C = UUID.fromString("99997800-0000-0000-0000-00000000000c");
    protected static final UUID SOCIETE_SE7E = UUID.nameUUIDFromBytes("se7e-societe".getBytes(StandardCharsets.UTF_8));
    protected static final UUID SOCIETE_AUTRE = UUID.nameUUIDFromBytes("se7e-societe-autre".getBytes(StandardCharsets.UTF_8));
    protected static final UUID DIRECTEUR_SE7E = UUID.nameUUIDFromBytes("se7e-directeur".getBytes(StandardCharsets.UTF_8));
    protected static final String DIRECTION_API = "/api/v1/direction";
    protected static final String MIGRATION_API = "/api/v1/admin/migration";
    protected static final String REFERENTIELS_API = "/api/v1/admin/referentials";

    /** Centres de test déjà présents avant le scénario (posés par un autre filet) : rétablis à l'identique en fin de scénario. */
    private final Map<UUID, Map<String, Object>> centresPreexistants = new LinkedHashMap<>();

    @Override
    protected String tranche() {
        return "se7e";
    }

    @Override
    protected void nettoyerSe1() {
        for (UUID centre : List.of(CENTRE_A, CENTRE_B, CENTRE_C)) {
            for (String table : List.of("migration_entity_run", "migration_id_map", "migration_value_map", "migration_batch", "assure_patient",
                    "volet_medical", "prescriptions_medicales", "modele_document", "absence_patient")) {
                jdbc.update("DELETE FROM " + table + " WHERE center_id = ?", centre);
            }
            jdbc.update("UPDATE patients SET centre_payeur_id = NULL WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM centre_payeur WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM agence WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM caisse_assurance WHERE center_id = ?", centre);
        }
        jdbc.update("DELETE FROM volet_paramedical WHERE center_id = ?", CENTRE_C);
        jdbc.update("DELETE FROM seances WHERE center_id = ?", CENTRE_C);
        jdbc.update("DELETE FROM patients WHERE center_id = ?", CENTRE_C);
        super.nettoyerSe1();
        for (UUID centre : List.of(CENTRE_A, CENTRE_B, CENTRE_C)) {
            jdbc.update("DELETE FROM assure WHERE center_id = ?", centre);
        }
        retirerLaSociete();
    }

    // ------------------------------------------------------------------ société, centres et direction

    /**
     * Société SE7e (centres A et B) et une autre société (centre C) ; compte de direction rattaché à la société SE7e seule. Noms de
     * centres FIXES (l'ordre des centres de la Direction est celui du nom).
     */
    protected void societeDeLaDirection() {
        retirerLaSociete();
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", SOCIETE_SE7E,
                "ZT-SE7E-S", "Societe SE7e");
        jdbc.update("INSERT INTO societes (id, code, raison_sociale, actif, created_at) VALUES (?,?,?,TRUE,CURRENT_TIMESTAMP)", SOCIETE_AUTRE,
                "ZT-SE7E-T", "Autre societe SE7e");
        centre(CENTRE_A, "ZT-SE7E-A", "Centre Alpha SE7e", SOCIETE_SE7E);
        centre(CENTRE_B, "ZT-SE7E-B", "Centre Beta SE7e", SOCIETE_SE7E);
        centre(CENTRE_C, "ZT-SE7E-C", "Centre Gamma SE7e", SOCIETE_AUTRE);
        jdbc.update("INSERT INTO app_user (id, username, password_hash, email, full_name, active) VALUES (?,?,?,?,?,TRUE)", DIRECTEUR_SE7E,
                "zt-se7e-direction", "x", null, "Direction SE7e");
        jdbc.update("INSERT INTO app_user_societe (user_id, societe_id) VALUES (?, ?)", DIRECTEUR_SE7E, SOCIETE_SE7E);
        aliasNommes.put(SOCIETE_SE7E.toString(), "<SOCIETE_SE7E>");
        aliasNommes.put(CENTRE_C.toString(), "<CENTRE_C>");
    }

    private void centre(UUID id, String code, String nom, UUID societe) {
        List<Map<String, Object>> avant = jdbc.queryForList("SELECT code, name, societe_id, actif FROM centers WHERE id = ?", id);
        if (avant.isEmpty()) {
            jdbc.update("INSERT INTO centers (id, code, name, societe_id, actif) VALUES (?,?,?,?,TRUE)", id, code, nom, societe);
        } else {
            centresPreexistants.putIfAbsent(id, avant.get(0));
            jdbc.update("UPDATE centers SET name = ?, societe_id = ?, actif = TRUE WHERE id = ?", nom, societe, id);
        }
    }

    protected void retirerLaSociete() {
        jdbc.update("DELETE FROM app_user_societe WHERE user_id = ?", DIRECTEUR_SE7E);
        jdbc.update("DELETE FROM app_user WHERE id = ?", DIRECTEUR_SE7E);
        centresPreexistants.forEach((id, ligne) -> jdbc.update("UPDATE centers SET name = ?, societe_id = ?, actif = ? WHERE id = ?",
                ligne.get("name"), ligne.get("societe_id"), ligne.get("actif"), id));
        centresPreexistants.clear();
        jdbc.update("DELETE FROM centers WHERE code LIKE 'ZT-SE7E-%'");
        jdbc.update("DELETE FROM societes WHERE code LIKE 'ZT-SE7E-%'");
    }

    /** Compte de direction de la société SE7e : sans centre de session, société portée par le jeton (revérifiée en base). */
    protected static RequestPostProcessor directionSe7e() {
        return user(new UserPrincipal(DIRECTEUR_SE7E.toString(), null, "u-direction-se7e", "",
                List.of(new SimpleGrantedAuthority("ROLE_DIRECTION")), true, SOCIETE_SE7E.toString()));
    }

    // ------------------------------------------------------------------ fixtures

    /** Volet paramédical posé dans le centre {@code centreDuVolet} (qui peut différer de celui de la séance) ; UF nulle si {@code uf} nul. */
    protected void voletParamedical(UUID centreDuVolet, UUID seance, String poidsAvant, String poidsApres, String taAvant, String uf) {
        jdbc.update("INSERT INTO volet_paramedical (id, center_id, seance_id, poids_avant_kg, poids_apres_kg, ta_avant, ta_apres, "
                        + "ultrafiltration_ml, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.nameUUIDFromBytes(("se7e-volet-" + seance).getBytes(StandardCharsets.UTF_8)), centreDuVolet, seance,
                poidsAvant == null ? null : new BigDecimal(poidsAvant), poidsApres == null ? null : new BigDecimal(poidsApres), taAvant, null,
                uf == null ? null : new BigDecimal(uf), CREATION_FIXE, CREATION_FIXE);
    }

    /** Prescription médicale portant (ou non) un poids sec ; {@code majLe} départage deux prescriptions du même jour. */
    protected void prescription(UUID centre, UUID patient, String alias, LocalDate jour, String poidsSec, OffsetDateTime majLe) {
        jdbc.update("INSERT INTO prescriptions_medicales (id, patient_id, center_id, date_prescription, poids_sec_cible_kg, created_at, updated_at) "
                        + "VALUES (?,?,?,?,?,?,?)", UUID.nameUUIDFromBytes(("se7e-prescription-" + alias).getBytes(StandardCharsets.UTF_8)),
                patient, centre, Date.valueOf(jour), poidsSec == null ? null : new BigDecimal(poidsSec), Timestamp.from(CREATION_FIXE.toInstant()),
                Timestamp.from(majLe.toInstant()));
    }

    /** Forfait du référentiel du centre, nommé dans les goldens. */
    protected UUID forfait(UUID centre, String alias, String code, String prix) {
        UUID id = UUID.nameUUIDFromBytes(("se7e-forfait-" + alias).getBytes(StandardCharsets.UTF_8));
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO forfait (id, center_id, code, libelle, prix) VALUES (?, ?, ?, ?, ?)", id, centre, code, "Forfait " + code,
                new BigDecimal(prix));
        return id;
    }

    // ------------------------------------------------------------------ appels

    protected MockHttpServletResponse binaire(String path, RequestPostProcessor who) throws Exception {
        return mvc.perform(get(URI.create(path)).with(who)).andReturn().getResponse();
    }

    /** Import d'un fichier CSV dans le lot (écriture réelle, {@code dryRun=false}) ; rend la réponse brute. */
    protected MockHttpServletResponse importer(UUID lot, String entite, String csv, RequestPostProcessor who) throws Exception {
        return mvc.perform(multipart(URI.create(MIGRATION_API + "/batches/" + lot + "/import/" + entite))
                .file(new MockMultipartFile("file", "import.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .param("dryRun", "false").with(who)).andReturn().getResponse();
    }

    /** Ouvre un lot de reprise du centre A (période reprise depuis le 2025-10-01) ; rend son identifiant, nommé {@code <LOT>}. */
    protected UUID ouvrirUnLot() throws Exception {
        Resp r = doPost(MIGRATION_API + "/batches", "{\"libelle\":\"Reprise SE7e\",\"dateDebutReprise\":\"2025-10-01\"}", adminA());
        UUID lot = UUID.fromString(corps(r).path("id").asString());
        aliasNommes.put(lot.toString(), "<LOT>");
        return lot;
    }

    // ------------------------------------------------------------------ lecture des réponses

    /** Texte d'un PDF, ligne à ligne ; horodatages d'édition masqués (date et heure de l'appel). */
    protected List<String> texteNormalise(byte[] pdf) throws Exception {
        List<String> lignes = new ArrayList<>();
        for (String ligne : texteDuPdf(pdf)) {
            lignes.add(ligne.replaceAll("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}", "<EDITE_LE>")
                    .replaceAll("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}(?!:)", "<GENERE_LE>"));
        }
        return lignes;
    }

    /**
     * Les colonnes DATE servies par les statistiques (sérialisées en horodatage UTC du minuit de la JVM, KB-MED-14) ramenées au jour, pour
     * que le golden ne dépende pas du fuseau de la machine.
     */
    protected String joursDesStatistiques(String brut) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?(Z|[+-]\\d{2}:\\d{2})").matcher(brut);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(sb, OffsetDateTime.parse(m.group()).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Statut et code d'erreur, ou le statut seul. */
    protected Map<String, Object> refusOuStatut(int status, String brut) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        if (status >= 400 && brut != null && !brut.isBlank()) {
            try {
                tools.jackson.databind.JsonNode c = mapper.readTree(brut);
                m.put("code", c.path("code").asString());
                if (status < 500) m.put("detail", c.path("detail").asString());
            } catch (RuntimeException e) {
                m.put("texte", brut);
            }
        }
        return m;
    }
}
