package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slice absences-patients-a6-cloture-mensuelle, comportement NOUVEAU (goldens {@code *_nouveau_code}, KB-ABS-37, ADR 0042) : la
 * détection, la réconciliation, le rattrapage de détection et la notification ne touchent plus un mois d'absences clos. Le
 * rattrapage de détection expose les {@code joursIgnores} (champ additif, absent quand rien n'est ignoré) ; la notification ne
 * compte que les mois ouverts. Jours de calendrier nommés : aucun golden ne dépend du jour d'exécution.
 */
class DetectionSurUnMoisDAbsencesCloNouveauCodeIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    private static final YearMonth MOIS_COURANT = YearMonth.from(aujourdhui());
    private static final LocalDate DERNIER_JOUR_DU_MOIS_MOINS_2 = MOIS_COURANT.minusMonths(2).atEndOfMonth();
    private static final LocalDate PREMIER_JOUR_DU_MOIS_MOINS_1 = MOIS_COURANT.minusMonths(1).atDay(1);
    private static final Map<LocalDate, String> NOMS_DES_JOURS = Map.of(
            DERNIER_JOUR_DU_MOIS_MOINS_2, "<DERNIER_JOUR_DU_MOIS-2>",
            PREMIER_JOUR_DU_MOIS_MOINS_1, "<PREMIER_JOUR_DU_MOIS-1>");

    @Override
    protected String theme() {
        return "mois-clos-a6";
    }

    @BeforeEach
    @AfterEach
    void nettoyerLesMois() {
        for (var centre : new java.util.UUID[]{CENTRE_A, CENTRE_B}) {
            jdbc.update("DELETE FROM mois_absences_journal WHERE center_id = ?", centre);
            jdbc.update("DELETE FROM mois_absences WHERE center_id = ?", centre);
        }
    }

    private void clore(YearMonth mois) throws Exception {
        Resp r = doPost(ABSENCES + "/mois/" + mois + "/cloturer", json("{}"), admin());
        assertEquals(200, r.status());
    }

    private ArrayNode absencesNommees() {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> l : jdbc.queryForList("SELECT date_seance, patient_id, source, statut FROM absence_patient "
                + "WHERE center_id = ? ORDER BY date_seance, patient_id", CENTRE_A)) {
            LocalDate jour = ((java.sql.Date) l.get("DATE_SEANCE")).toLocalDate();
            a.add(NOMS_DES_JOURS.getOrDefault(jour, "<JOUR_NON_NOMME>") + " | " + l.get("PATIENT_ID") + " | " + l.get("STATUT") + "/"
                    + l.get("SOURCE"));
        }
        return a;
    }

    /**
     * La réponse du rattrapage de détection, jours ignorés nommés ; la liste des champs montre ce qui est présent.
     */
    private ObjectNode reponse(Resp r) {
        ObjectNode o = objet();
        o.put("status", r.status());
        JsonNode corps = json(r);
        ArrayNode champs = mapper.createArrayNode();
        corps.propertyNames().stream().sorted().forEach(champs::add);
        o.set("champs", champs);
        o.put("creees", corps.path("creees").asInt(-1));
        o.put("annulees", corps.path("annulees").asInt(-1));
        if (corps.has("joursIgnores")) {
            ArrayNode ignores = mapper.createArrayNode();
            corps.path("joursIgnores").forEach(j -> ignores.add(NOMS_DES_JOURS.getOrDefault(LocalDate.parse(j.asString()),
                    "<JOUR_NON_NOMME>")));
            o.set("joursIgnores", ignores);
        }
        return o;
    }

    @Test
    void le_rattrapage_de_detection_ignore_les_jours_d_un_mois_clos_et_les_expose_dans_la_reponse() throws Exception {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        clore(MOIS_COURANT.minusMonths(2));

        var r = rattraper(DERNIER_JOUR_DU_MOIS_MOINS_2, PREMIER_JOUR_DU_MOIS_MOINS_1);

        ObjectNode o = objet();
        o.set("reponse", reponse(r));
        o.set("absences", absencesNommees());
        goldenNode("rattrapage_avec_un_mois_clos_nouveau_code", o);
        assertEquals(1, compter(CENTRE_A));
    }

    @Test
    void le_rattrapage_de_detection_retrouve_le_jour_une_fois_le_mois_rouvert_et_n_expose_plus_rien() throws Exception {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        clore(MOIS_COURANT.minusMonths(2));
        rattraper(DERNIER_JOUR_DU_MOIS_MOINS_2, PREMIER_JOUR_DU_MOIS_MOINS_1);
        Resp rouverture = doPost(ABSENCES + "/mois/" + MOIS_COURANT.minusMonths(2) + "/rouvrir", json("{'motif':'jour manquant'}"),
                admin());
        assertEquals(200, rouverture.status());

        var r = rattraper(DERNIER_JOUR_DU_MOIS_MOINS_2, PREMIER_JOUR_DU_MOIS_MOINS_1);

        ObjectNode o = objet();
        o.set("reponse", reponse(r));
        o.set("absences", absencesNommees());
        goldenNode("rattrapage_apres_reouverture_nouveau_code", o);
        assertEquals(2, compter(CENTRE_A));
    }

    @Test
    void la_reconciliation_ne_touche_pas_une_absence_detectee_d_un_mois_clos_meme_si_la_seance_a_ete_saisie_apres_coup() throws Exception {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        abs(1, P_AMRANI, DERNIER_JOUR_DU_MOIS_MOINS_2).poser();
        seance(P_AMRANI, CENTRE_A, DERNIER_JOUR_DU_MOIS_MOINS_2, "VALIDEE");
        clore(MOIS_COURANT.minusMonths(2));

        var r = rattraper(DERNIER_JOUR_DU_MOIS_MOINS_2, DERNIER_JOUR_DU_MOIS_MOINS_2);

        ObjectNode o = objet();
        o.set("reponse", reponse(r));
        o.set("absences", absencesNommees());
        goldenNode("reconciliation_mois_clos_nouveau_code", o);
    }

    @Test
    void la_notification_de_nuit_ne_compte_que_les_absences_a_qualifier_des_mois_ouverts() throws Exception {
        // patient en sommeil : le controle de nuit ne detecte rien, seules les deux absences posees sont comptees
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").sommeil(Boolean.TRUE).poser();
        abs(1, P_BELKACEM, DERNIER_JOUR_DU_MOIS_MOINS_2).poser();
        abs(2, P_BELKACEM, PREMIER_JOUR_DU_MOIS_MOINS_1).poser();
        clore(MOIS_COURANT.minusMonths(2));

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("journal", journal(CENTRE_A));
        o.set("absences", absencesNommees());
        goldenNode("notification_un_mois_clos_nouveau_code", o);
    }

    @Test
    void la_notification_se_tait_quand_toutes_les_absences_a_qualifier_sont_dans_des_mois_clos() throws Exception {
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").sommeil(Boolean.TRUE).poser();
        abs(1, P_BELKACEM, DERNIER_JOUR_DU_MOIS_MOINS_2).poser();
        clore(MOIS_COURANT.minusMonths(2));

        scheduler.controlerAbsences();

        assertEquals(0, journal(CENTRE_A).size());
    }
}
