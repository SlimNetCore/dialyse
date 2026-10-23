package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Slice absences-patients-a6-cloture-mensuelle, filet « mois ouvert » de la detection : rattrapage de detection et controle de
 * nuit aujourd'hui, sur plusieurs mois. Verrouille (1) la detection a cheval sur deux mois, (2) l'absence de lien avec la
 * cloture mensuelle de la Facturation (mois de {@code periodes_facturation} clos : la detection ecrit quand meme), (3) la reponse
 * du rattrapage qui ne dit rien des jours ignores, (4) la notification qui compte les absences a qualifier de TOUS les mois.
 * Les jours de calendrier sont NOMMES ({@code <DERNIER_JOUR_DU_MOIS-2>}) : aucun golden ne depend du jour d'execution.
 */
class DetectionEtNotificationSurPlusieursMoisA6CharacterizationIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    private static final YearMonth MOIS_COURANT = YearMonth.from(aujourdhui());
    private static final LocalDate DERNIER_JOUR_DU_MOIS_MOINS_2 = MOIS_COURANT.minusMonths(2).atEndOfMonth();
    private static final LocalDate PREMIER_JOUR_DU_MOIS_MOINS_1 = MOIS_COURANT.minusMonths(1).atDay(1);
    private static final Map<LocalDate, String> NOMS_DES_JOURS = Map.of(
            DERNIER_JOUR_DU_MOIS_MOINS_2, "<DERNIER_JOUR_DU_MOIS-2>",
            PREMIER_JOUR_DU_MOIS_MOINS_1, "<PREMIER_JOUR_DU_MOIS-1>");

    @Override
    protected String theme() {
        return "mois-ouvert-a6";
    }

    // d'autres classes (exclusion) laissent des lignes de mois OUVERTES : on repart d'un etat propre avant comme apres
    @org.junit.jupiter.api.BeforeEach
    @AfterEach
    void nettoyerLaClotureDeFacturation() {
        jdbc.update("DELETE FROM periodes_facturation_journal WHERE center_id = ?", CENTRE_A);
        jdbc.update("DELETE FROM periodes_facturation WHERE center_id = ?", CENTRE_A);
    }

    private void cloreLaFacturationDesMoisDe(LocalDate... jours) {
        for (YearMonth m : Stream.of(jours).map(YearMonth::from).distinct().toList()) {
            jdbc.update("INSERT INTO periodes_facturation (center_id, annee, mois, statut, cloturee_par, cloturee_le) "
                            + "VALUES (?, ?, ?, 'CLOTURE', ?, ?)", CENTRE_A, m.getYear(), m.getMonthValue(), U_ADMIN,
                    OffsetDateTime.now(ZoneOffset.UTC));
        }
    }

    /**
     * Une ligne par absence : jour (alias nomme, sinon alias relatif), patient, statut/source. Jamais de date brute.
     */
    private ArrayNode absencesNommees(Map<LocalDate, String> alias) {
        ArrayNode a = mapper.createArrayNode();
        for (Map<String, Object> l : jdbc.queryForList("SELECT date_seance, patient_id, source, statut FROM absence_patient "
                + "WHERE center_id = ? ORDER BY date_seance, patient_id", CENTRE_A)) {
            LocalDate jour = ((java.sql.Date) l.get("DATE_SEANCE")).toLocalDate();
            a.add(alias.getOrDefault(jour, "<JOUR_NON_NOMME>") + " | " + l.get("PATIENT_ID") + " | " + l.get("STATUT") + "/"
                    + l.get("SOURCE"));
        }
        return a;
    }

    @Test
    void le_rattrapage_de_detection_traite_les_deux_mois_d_une_fenetre_et_la_reponse_ne_signale_aucun_jour_ignore() throws Exception {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");

        var r = rattraper(DERNIER_JOUR_DU_MOIS_MOINS_2, PREMIER_JOUR_DU_MOIS_MOINS_1);

        ObjectNode o = objet();
        o.set("reponse", resume(r));
        o.set("absences", absencesNommees(NOMS_DES_JOURS));
        goldenNode("rattrapage_sur_deux_mois_ouverts", o);
        assertEquals(2, compter(CENTRE_A));
    }

    @Test
    void la_cloture_de_facturation_des_deux_mois_n_empeche_ni_la_detection_ni_le_rattrapage_de_detection() throws Exception {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        cloreLaFacturationDesMoisDe(DERNIER_JOUR_DU_MOIS_MOINS_2, PREMIER_JOUR_DU_MOIS_MOINS_1);

        var r = rattraper(DERNIER_JOUR_DU_MOIS_MOINS_2, PREMIER_JOUR_DU_MOIS_MOINS_1);

        ObjectNode o = objet();
        o.set("reponse", resume(r));
        o.set("absences", absencesNommees(NOMS_DES_JOURS));
        goldenNode("rattrapage_avec_facturation_close", o);
        assertEquals(2, compter(CENTRE_A));
    }

    @Test
    void la_cloture_de_facturation_des_mois_des_sept_derniers_jours_n_empeche_pas_le_controle_de_nuit() {
        patientAttendu(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani");
        cloreLaFacturationDesMoisDe(jour(1), jour(7));

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("absences", lignes(CENTRE_A));
        goldenNode("controle_de_nuit_avec_facturation_close", o);
        assertEquals(7, compter(CENTRE_A));
    }

    @Test
    void la_notification_compte_les_absences_a_qualifier_de_chaque_mois_aussi_anciennes_soient_elles() {
        // patient en sommeil : le controle de nuit ne detecte rien, seules les deux absences posees sont comptees
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").sommeil(Boolean.TRUE).poser();
        abs(1, P_BELKACEM, DERNIER_JOUR_DU_MOIS_MOINS_2).poser();
        abs(2, P_BELKACEM, PREMIER_JOUR_DU_MOIS_MOINS_1).poser();

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.set("journal", journal(CENTRE_A));
        o.set("absences", absencesNommees(NOMS_DES_JOURS));
        goldenNode("notification_sur_deux_mois_ouverts", o);
        assertEquals(List.of(2), List.of(compter(CENTRE_A)));
    }
}
