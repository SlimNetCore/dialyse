package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Rattrapage au demarrage ({@code ControleDeNuitDesAbsences.rattraperAuDemarrage()} (ex-AbsencePatientScheduler, retire en A3), 31 jours, sans notification, desactivable)
 * et ecritures de masse (plusieurs centaines d'absences, reconciliation paginee par lots de 200).
 */
class RattrapageAuDemarrageEtEcrituresDeMasseCharacterizationIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "demarrage-et-masse";
    }

    private void avecRattrapageAuDemarrage(boolean actif, Runnable action) {
        Object avant = ReflectionTestUtils.getField(scheduler, "rattrapageAuDemarrage");
        ReflectionTestUtils.setField(scheduler, "rattrapageAuDemarrage", actif);
        try {
            action.run();
        } finally {
            ReflectionTestUtils.setField(scheduler, "rattrapageAuDemarrage", avant);
        }
    }

    @Test
    void le_profil_de_test_desactive_le_rattrapage_au_demarrage() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();

        scheduler.rattraperAuDemarrage();

        assertEquals(0, compter(CENTRE_A));
        assertEquals(0, journal(CENTRE_A).size());
    }

    @Test
    void le_rattrapage_au_demarrage_couvre_les_31_derniers_jours_sans_notification() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").admission(jour(2)).poser();

        avecRattrapageAuDemarrage(true, scheduler::rattraperAuDemarrage);

        ObjectNode o = objet();
        List<String> dates = new ArrayList<>();
        datesDe(CENTRE_A, P_AMRANI).forEach(n -> dates.add(n.asString()));
        o.put("nombreCentreA", dates.size());
        o.put("premiere", dates.get(0));
        o.put("derniere", dates.get(dates.size() - 1));
        o.set("centreB", lignes(CENTRE_B));
        o.set("journalA", journal(CENTRE_A));
        o.set("journalB", journal(CENTRE_B));
        o.set("diffusionsA", diffusionsDuCentre(CENTRE_A));
        o.set("diffusionsB", diffusionsDuCentre(CENTRE_B));
        goldenNode("trente_et_un_jours_sans_notification", o);
        assertEquals(31, dates.size());
    }

    @Test
    void le_rattrapage_au_demarrage_est_idempotent_et_reconcilie_sur_31_jours() {
        UUID p2 = P_BELKACEM;
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(p2, CENTRE_A, "ABS3-002", "Belkacem").aucunJour().poser();
        abs(1, p2, jour(20)).poser();
        abs(2, p2, jour(40)).poser();
        seance(p2, CENTRE_A, jour(20), "VALIDEE");
        seance(p2, CENTRE_A, jour(40), "VALIDEE");

        avecRattrapageAuDemarrage(true, scheduler::rattraperAuDemarrage);
        var premier = lignes(CENTRE_A);
        avecRattrapageAuDemarrage(true, scheduler::rattraperAuDemarrage);

        assertEquals(premier, lignes(CENTRE_A));
        ObjectNode o = objet();
        o.set("absencesDeBelkacem", datesDe(CENTRE_A, p2));
        o.put("absencesDAmrani", datesDe(CENTRE_A, P_AMRANI).size());
        goldenNode("reconciliation_a_31_jours", o);
    }

    @Test
    void un_centre_en_echec_au_demarrage_n_empeche_pas_les_autres() {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").admission(jour(3)).poser();
        jdbc.update("UPDATE forfait SET prix = -5.00 WHERE id = ?", F_HD);

        avecRattrapageAuDemarrage(true, scheduler::rattraperAuDemarrage);

        ObjectNode o = objet();
        o.put("absencesA", compter(CENTRE_A));
        o.set("absencesB", lignes(CENTRE_B));
        goldenNode("centre_en_echec_au_demarrage", o);
    }

    @Test
    void le_controle_de_nuit_traite_quarante_patients_sur_sept_jours_et_notifie_une_fois() {
        for (int i = 1; i <= 40; i++) {
            pat(pid("MASSE", i), CENTRE_A, String.format("ABS3-M%02d", i), "Masse" + i).poser();
        }

        scheduler.controlerAbsences();

        ObjectNode o = objet();
        o.put("absences", compter(CENTRE_A));
        o.put("parJour", jdbc.queryForObject(
                "SELECT COUNT(DISTINCT date_seance) FROM absence_patient WHERE center_id = ?", Integer.class, CENTRE_A));
        o.put("parPatient", jdbc.queryForObject(
                "SELECT MIN(n) || '-' || MAX(n) FROM (SELECT COUNT(*) AS n FROM absence_patient WHERE center_id = ? GROUP BY patient_id) t",
                String.class, CENTRE_A));
        o.set("journal", journal(CENTRE_A));
        goldenNode("quarante_patients_sept_jours", o);
        assertEquals(280, compter(CENTRE_A));
    }

    /**
     * 25 patients x 10 jours (J-10..J-1) : 250 absences « a qualifier ». Les seances realisees sont posees seulement aux jours
     * {@code joursAvecSeance} (la liste est lue du plus recent au plus ancien).
     */
    private void poserDeuxCentCinquanteAbsences(int... joursAvecSeance) {
        List<Object[]> absences = new ArrayList<>();
        List<Object[]> seances = new ArrayList<>();
        int rang = 0;
        for (int i = 1; i <= 25; i++) {
            UUID p = pid("LOT", i);
            pat(p, CENTRE_A, String.format("ABS3-L%02d", i), "Lot" + i).aucunJour().poser();
            for (int j = 1; j <= 10; j++) {
                rang++;
                absences.add(new Object[]{idFixture(rang), CENTRE_A, p, Date.valueOf(jour(j))});
                for (int s : joursAvecSeance) {
                    if (s == j) seances.add(new Object[]{UUID.randomUUID(), p, CENTRE_A, Date.valueOf(jour(j))});
                }
            }
        }
        Timestamp t = Timestamp.valueOf("2026-01-15 08:00:00");
        jdbc.batchUpdate("INSERT INTO absence_patient (id, center_id, patient_id, date_seance, source, statut, forfait_libelle, "
                + "prix_ttc, taux_tva, montant_ht, declaree_le) VALUES (?, ?, ?, ?, 'AUTOMATIQUE', 'A_QUALIFIER', 'Forfait HD', "
                + "11900.00, 19.00, 10000.00, ?)", absences.stream().map(a -> new Object[]{a[0], a[1], a[2], a[3], t}).toList());
        jdbc.batchUpdate("INSERT INTO seances (id, patient_id, center_id, date_seance, statut, created_at, validated_at, "
                + "signed_infirmier_at, signed_medecin_at) VALUES (?, ?, ?, ?, 'VALIDEE', ?, ?, ?, ?)",
                seances.stream().map(s -> new Object[]{s[0], s[1], s[2], s[3], t, t, t, t}).toList());
    }

    private long annulees() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM absence_patient WHERE center_id = ? AND statut = 'ANNULEE'", Long.class,
                CENTRE_A);
    }

    @Test
    void la_reconciliation_traite_250_absences_en_un_seul_passage_sans_page_sautee() throws Exception {
        poserDeuxCentCinquanteAbsences(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        var premier = rattraper(jour(10), jour(1));
        long annuleesApresLePremier = annulees();
        var second = rattraper(jour(10), jour(1));
        long annuleesApresLeSecond = annulees();

        ObjectNode o = objet();
        o.set("premierAppel", resume(premier));
        o.put("annuleesApresLePremierAppel", annuleesApresLePremier);
        o.set("secondAppel", resume(second));
        o.put("annuleesApresLeSecondAppel", annuleesApresLeSecond);
        o.put("totalAbsences", compter(CENTRE_A));
        goldenNode("reconciliation_de_250_absences_nouveau_code", o);
    }

    @Test
    void la_reconciliation_lit_la_page_suivante_quand_plus_de_200_absences_restent_a_qualifier() throws Exception {
        // seuls les deux jours les plus anciens ont une seance : leurs 50 absences sont en fin de liste (page 2)
        poserDeuxCentCinquanteAbsences(9, 10);

        var premier = rattraper(jour(10), jour(1));

        ObjectNode o = objet();
        o.set("premierAppel", resume(premier));
        o.put("annulees", annulees());
        o.put("totalAbsences", compter(CENTRE_A));
        goldenNode("reconciliation_page_suivante", o);
    }
}
