package com.hemodialyse.backend.characterization.absencespatients.detection;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code POST /api/v1/absences-patients/rattrapage-detection} (ADMIN) : roles, bornes, resultat, effets. */
class RattrapageDeDetectionCharacterizationIntegrationTest extends DetectionAbsencesCharacterizationSupport {

    @Override
    protected String theme() {
        return "rattrapage-detection";
    }

    @Test
    void l_administrateur_rattrape_une_periode_et_recoit_le_nombre_d_absences_creees_et_annulees() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").aucunJour().poser();
        abs(1, P_BELKACEM, jour(11)).poser();
        seance(P_BELKACEM, CENTRE_A, jour(11), "VALIDEE");

        var r = rattraper(jour(12), jour(10));

        ObjectNode o = objet();
        o.set("reponse", resume(r));
        o.set("absences", lignes(CENTRE_A));
        goldenNode("nominal", o);
        assertEquals("no-store", r.cache());
    }

    @Test
    void le_rattrapage_est_idempotent_n_envoie_aucune_notification_et_ne_diffuse_rien() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();

        var premier = rattraper(jour(10), jour(8));
        var avant = lignes(CENTRE_A);
        var second = rattraper(jour(10), jour(8));

        ObjectNode o = objet();
        o.set("premierAppel", resume(premier));
        o.set("secondAppel", resume(second));
        o.set("journal", journal(CENTRE_A));
        o.set("diffusions", diffusionsDuCentre(CENTRE_A));
        goldenNode("idempotence_sans_notification", o);
        assertEquals(avant, lignes(CENTRE_A));
    }

    @Test
    void seul_l_administrateur_peut_rattraper_la_detection() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        ObjectNode o = objet();
        o.set("secretaire", refus(rattraper(secretaire(), jour(10), jour(8))));
        o.set("medecin", refus(rattraper(medecin(), jour(10), jour(8))));
        o.set("infirmier", refus(rattraper(infirmier(), jour(10), jour(8))));
        o.set("direction", refus(rattraper(direction(), jour(10), jour(8))));
        o.set("anonyme", refus(doPostAnonyme(ABSENCES + "/rattrapage-detection",
                json("{'from':'" + jour(10) + "','to':'" + jour(8) + "'}"))));
        o.put("absencesCreees", compter(CENTRE_A));
        goldenNode("roles", o);
        assertEquals(0, compter(CENTRE_A));
    }

    @Test
    void la_fin_de_la_periode_est_ramenee_a_hier_et_le_debut_doit_la_preceder() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        ObjectNode o = objet();
        o.set("finAujourdhui", cas(jour(3), jour(0)));
        o.set("finDansLeFutur", cas(jour(3), jour(-30)));
        o.set("debutAujourdhuiFinAujourdhui", cas(jour(0), jour(0)));
        o.set("debutAujourdhuiFinHier", cas(jour(0), jour(1)));
        o.set("debutHierFinHier", cas(jour(1), jour(1)));
        o.set("debutDansLeFutur", cas(jour(-5), jour(-10)));
        o.set("debutApresLaFin", cas(jour(2), jour(5)));
        o.set("unSeulJourDansLePasse", cas(jour(20), jour(20)));
        goldenNode("bornes_de_dates_nouveau_code", o);
    }

    private ObjectNode cas(LocalDate from, LocalDate to) throws Exception {
        jdbc.update("DELETE FROM absence_patient WHERE center_id = ?", CENTRE_A);
        var r = rattraper(from, to);
        ObjectNode c = objet();
        c.set("reponse", refus(r));
        if (r.status() == 200) c.set("corps", json(r));
        c.set("dates", datesDe(CENTRE_A, P_AMRANI));
        return c;
    }

    @Test
    void la_duree_maximale_est_de_366_jours_comptes_de_la_veille_de_la_fin_au_debut() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        ObjectNode o = objet();
        // between(debut, fin) < 366 : 366 jours inclus acceptes, 367 refuses
        var refuse = rattraper(jour(367), jour(1));
        o.set("367JoursInclus", refus(refuse));
        o.put("absencesApresRefus", compter(CENTRE_A));
        var limite = rattraper(jour(366), jour(1));
        o.set("366JoursInclus", resume(limite));
        o.put("absencesApres366Jours", compter(CENTRE_A));
        o.set("premieresEtDernieres", premieresEtDernieres());
        goldenNode("duree_maximale", o);
    }

    private tools.jackson.databind.node.ArrayNode premieresEtDernieres() {
        var a = mapper.createArrayNode();
        var all = datesDe(CENTRE_A, P_AMRANI);
        a.add(all.get(0));
        a.add(all.get(all.size() - 1));
        return a;
    }

    @Test
    void une_periode_ancienne_de_plus_de_60_jours_est_detectee_sans_le_delai_de_declaration() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();

        var r = rattraper(jour(120), jour(100));

        ObjectNode o = objet();
        o.set("reponse", resume(r));
        o.set("dates", datesDe(CENTRE_A, P_AMRANI));
        goldenNode("periode_ancienne", o);
    }

    @Test
    void le_rattrapage_ne_concerne_que_le_centre_de_l_administrateur() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").aucunJour().poser();
        UUID autre = P_CENTRE_B;
        abs(1, autre, jour(9)).centre(CENTRE_B).poser();
        seance(autre, CENTRE_B, jour(9), "VALIDEE");

        var a = rattraper(jour(10), jour(9));
        var b = rattraper(adminB(), jour(10), jour(9));

        ObjectNode o = objet();
        o.set("administrateurDuCentreA", resume(a));
        o.set("administrateurDuCentreB", resume(b));
        o.set("centreA", lignes(CENTRE_A));
        o.set("centreB", lignes(CENTRE_B));
        goldenNode("isolation_des_centres", o);
    }

    @Test
    void le_parametre_centre_est_controle_par_la_garde_de_centre() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").poser();
        String corps = json("{'from':'" + jour(10) + "','to':'" + jour(9) + "'}");
        ObjectNode o = objet();
        o.set("centreEtranger", refus(doPost(ABSENCES + "/rattrapage-detection?centerId=" + CENTRE_B, corps, admin())));
        o.set("centreIllisible", refus(doPost(ABSENCES + "/rattrapage-detection?centerId=pas-un-uuid", corps, admin())));
        o.set("centrePropre", refus(doPost(ABSENCES + "/rattrapage-detection?centerId=" + CENTRE_A, corps, admin())));
        o.put("absencesCentreB", compter(CENTRE_B));
        o.put("absencesCentreA", compter(CENTRE_A));
        goldenNode("garde_de_centre_nouveau_code", o);
    }

    @Test
    void les_corps_invalides_sont_refuses() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        ObjectNode o = objet();
        String url = ABSENCES + "/rattrapage-detection";
        o.set("objetVide", refus(doPost(url, "{}", admin())));
        o.set("sansFin", refus(doPost(url, json("{'from':'" + jour(5) + "'}"), admin())));
        o.set("sansDebut", refus(doPost(url, json("{'to':'" + jour(5) + "'}"), admin())));
        o.set("finNulle", refus(doPost(url, json("{'from':'" + jour(5) + "','to':null}"), admin())));
        o.set("dateIllisible", refus(doPost(url, "{\"from\":\"abc\",\"to\":\"2026-01-01\"}", admin())));
        o.set("jsonIllisible", refus(doPost(url, "{", admin())));
        o.set("corpsVide", refus(doPost(url, "", admin())));
        o.set("tableau", refus(doPost(url, "[]", admin())));
        o.set("texteBrut", refus(doPostType(url, "from=1", "text/plain", admin())));
        o.set("dateImpossible", refus(doPost(url, "{\"from\":\"2026-02-30\",\"to\":\"2026-03-01\"}", admin())));
        o.put("absencesCreees", compter(CENTRE_A));
        goldenNode("corps_invalides_nouveau_code", o);
        assertEquals(0, compter(CENTRE_A));
    }

    @Test
    void les_absences_detectees_par_le_rattrapage_sont_valorisees_comme_celles_de_la_nuit() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_CENTRE_B, CENTRE_B, "ABS3-B01", "Zerrouki").poser();

        rattraper(jour(3), jour(3));
        rattraper(adminB(), jour(3), jour(3));

        ObjectNode o = objet();
        o.set("centreA", etat(CENTRE_A));
        o.set("centreB", lignes(CENTRE_B));
        goldenNode("valorisation_et_colonnes", o);
        assertTrue(compter(CENTRE_A) == 1);
    }

    @Test
    void le_rattrapage_n_attend_pas_d_absence_deja_annulee_et_respecte_la_periode_facturee() throws Exception {
        pat(P_AMRANI, CENTRE_A, "ABS3-001", "Amrani").poser();
        pat(P_BELKACEM, CENTRE_A, "ABS3-002", "Belkacem").poser();
        abs(1, P_AMRANI, jour(6)).statut("ANNULEE").commentaire("Erreur").poser();
        facture(CENTRE_A, P_BELKACEM, "F-ABS3-1", jour(7), jour(6));

        var r = rattraper(jour(7), jour(5));

        ObjectNode o = objet();
        o.set("reponse", resume(r));
        o.set("absences", lignes(CENTRE_A));
        goldenNode("existante_et_facturee", o);
    }
}
