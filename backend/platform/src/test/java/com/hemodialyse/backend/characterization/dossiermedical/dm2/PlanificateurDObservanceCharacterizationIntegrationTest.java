package com.hemodialyse.backend.characterization.dossiermedical.dm2;

import com.hemodialyse.backend.infrastructure.scheduling.ObservancePrescriptionScheduler;
import com.hemodialyse.backend.infrastructure.scheduling.ObservanceSchedulerPontDeCaracterisation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM2, lot 2 : le planificateur {@code ObservancePrescriptionScheduler} (cron 06:30), appelé directement pour un centre. Il lit
 * {@code LocalDate.now()} (jour de la JVM, aucune horloge injectable : KB-MED-15, aucun seam n'est ajouté) : toutes les dates des fixtures
 * sont donc relatives à ce jour et nommées {@code <J-3>} ; une garde attend le passage de minuit si l'on en est à moins de deux minutes.
 * Observation : alertes ({@code alertes_observance}) et notifications durables. Données fictives.
 */
class PlanificateurDObservanceCharacterizationIntegrationTest extends PrescriptionsCharacterizationSupport {

    @Autowired
    private ObservancePrescriptionScheduler planificateur;

    @Override
    protected String dossier() {
        return "planificateur";
    }

    private UUID patientA;
    private UUID patientB;
    private UUID epo;
    private UUID fer;

    private void decorPlanificateur() {
        patientA = patientDm2(CENTRE_A, "PAT_A");
        patientB = patientDm2(CENTRE_B, "PAT_B");
        epo = article(CENTRE_A, "EPO", "EPO-FICT", "EPO", "4000", "UI");
        fer = article(CENTRE_A, "FER", "FER-FICT", "FER_INJECTABLE", "100", "mg");
        for (int n = -20; n <= 10; n++) relatif(n);
    }

    private void nuit(UUID centre) {
        ObservanceSchedulerPontDeCaracterisation.controlerCentre(planificateur, centre);
    }

    /** Alertes lisibles : dates en alias, message avec ses dates en toutes lettres nommées. */
    private List<Object> alertes() {
        List<Object> liste = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT center_id, type_traitement, type_alerte, periode_debut, periode_fin, doses_attendues, "
                + "doses_administrees, unite_dose, dose_prescrite, frequence_valeur, frequence_unite, message, resolved_at FROM alertes_observance "
                + "WHERE center_id IN (?, ?) ORDER BY center_id, type_traitement, type_alerte, periode_debut, resolved_at NULLS FIRST, id", CENTRE_A, CENTRE_B)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("centre", CENTRE_A.equals(row.get("center_id")) ? "A" : "B");
            m.put("traitement", row.get("type_traitement"));
            m.put("alerte", row.get("type_alerte"));
            m.put("periode", row.get("periode_debut") + " -> " + row.get("periode_fin"));
            m.put("attendu / administre", row.get("doses_attendues") + " / " + row.get("doses_administrees") + " " + row.get("unite_dose"));
            m.put("prescription", row.get("dose_prescrite") + " x " + row.get("frequence_valeur") + " par " + row.get("frequence_unite"));
            m.put("message", joursEnFrancais(String.valueOf(row.get("message"))));
            m.put("etat", row.get("resolved_at") == null ? "ouverte" : "resolue");
            liste.add(m);
        }
        return liste;
    }

    private List<Object> notificationsDuPlanificateur() {
        List<Object> liste = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList("SELECT center_id, type, target_roles, payload FROM notification_evenement "
                + "WHERE center_id IN (?, ?) ORDER BY center_id, type, payload", CENTRE_A, CENTRE_B)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("centre", CENTRE_A.equals(row.get("center_id")) ? "A" : "B");
            m.put("type", row.get("type"));
            m.put("roles", row.get("target_roles"));
            m.put("payload", joursEnFrancais(String.valueOf(row.get("payload"))));
            liste.add(m);
        }
        return liste;
    }

    private int nombreDeNotifications() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notification_evenement WHERE center_id IN (?, ?)", Integer.class, CENTRE_A, CENTRE_B);
    }

    // ------------------------------------------------------------------ retard et rappel

    @Test
    void une_periode_close_non_soldee_ouvre_une_alerte_de_retard_constate_en_quantite() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        administration(CENTRE_A, patientA, "A1", "EPO", relatif(-9), true, 4000, "UI");
        nuit(CENTRE_A);
        arbre("alertes", alertes());
        arbre("notifications", notificationsDuPlanificateur());
    }

    @Test
    void sans_dose_prescrite_le_retard_se_compte_en_nombre_d_administrations() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-10), epo, null, 2, "SEMAINE", null, null, null, null);
        administration(CENTRE_A, patientA, "A1", "EPO", relatif(-9), true, 4000, "UI");
        nuit(CENTRE_A);
        arbre("alertes", alertes());
    }

    @Test
    void a_l_approche_de_l_echeance_un_rappel_est_ouvert_puis_resolu_automatiquement_des_que_la_dose_est_rattrapee() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-6), epo, 8000, 1, "SEMAINE", null, null, null, null);
        nuit(CENTRE_A);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("1 - dernier jour de la periode, rien d'administre", alertes());
        administration(CENTRE_A, patientA, "A1", "EPO", relatif(0), true, 4000, "UI");
        nuit(CENTRE_A);
        cas.put("2 - 4000 UI administrees, le rappel reste ouvert (message non mis a jour)", alertes());
        administration(CENTRE_A, patientA, "A2", "EPO", relatif(0), true, 4000, "UI");
        nuit(CENTRE_A);
        cas.put("3 - dose soldee : le rappel est resolu automatiquement", alertes());
        arbre("rappel", cas);
    }

    @Test
    void loin_de_l_echeance_ou_pour_une_prescription_recente_ou_future_aucune_alerte() throws Exception {
        decorPlanificateur();
        Map<String, Object> cas = new LinkedHashMap<>();
        // Periode en cours encore longue (4 jours restants, seuil 2) et periode precedente anterieure a la prescription.
        prescriptionAnemie(CENTRE_A, patientA, "P1", relatif(-3), epo, 8000, 1, "SEMAINE", null, null, null, null);
        nuit(CENTRE_A);
        cas.put("prescription de J-3 (4 jours restants)", alertes());
        nettoyerDm2();
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P2", relatif(5), epo, 8000, 1, "SEMAINE", null, null, null, null);
        nuit(CENTRE_A);
        cas.put("prescription ancree dans le futur (J+5)", alertes());
        nettoyerDm2();
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P3", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        administration(CENTRE_A, patientA, "A1", "EPO", relatif(-9), true, 8000, "UI");
        nuit(CENTRE_A);
        cas.put("periode precedente soldee (8000 UI)", alertes());
        arbre("silence", cas);
    }

    @Test
    void les_periodes_en_jours_ouvrent_le_retard_de_la_veille_et_le_rappel_du_jour() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-5), epo, 4000, 2, "JOUR", null, null, null, null);
        administration(CENTRE_A, patientA, "A1", "EPO", relatif(-1), true, 4000, "UI");
        nuit(CENTRE_A);
        arbre("alertes", alertes());
    }

    @Test
    void le_fer_injectable_est_controle_en_milligrammes_en_parallele_de_l_epo() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-10), epo, 4000, 1, "SEMAINE", fer, 200, 1, "SEMAINE");
        administration(CENTRE_A, patientA, "E1", "EPO", relatif(-9), true, 4000, "UI");
        administration(CENTRE_A, patientA, "F1", "FER_INJECTABLE", relatif(-9), true, 100, "mg");
        nuit(CENTRE_A);
        arbre("alertes", alertes());
    }

    // ------------------------------------------------------------------ rejeu et cycle de vie

    @Test
    void rejouer_la_nuit_n_ouvre_pas_de_seconde_alerte_mais_renvoie_la_notification() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        nuit(CENTRE_A);
        int apresLaPremiere = nombreDeNotifications();
        nuit(CENTRE_A);
        nuit(CENTRE_A);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("alertes apres trois nuits", alertes());
        cas.put("notifications apres une nuit", apresLaPremiere);
        cas.put("notifications apres trois nuits", nombreDeNotifications());
        arbre("rejeu", cas);
    }

    @Test
    void une_alerte_de_retard_acquittee_est_rouverte_a_la_nuit_suivante_tant_que_la_periode_precedente_reste_la_meme() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        nuit(CENTRE_A);
        jdbc.update("UPDATE alertes_observance SET resolved_at = TIMESTAMP WITH TIME ZONE '2026-01-02 00:00:00+00:00' WHERE center_id = ?", CENTRE_A);
        nuit(CENTRE_A);
        arbre("alertes", alertes());
    }

    @Test
    void seule_la_derniere_prescription_du_patient_est_controlee_et_les_prescriptions_incompletes_sont_ignorees() throws Exception {
        decorPlanificateur();
        Map<String, Object> cas = new LinkedHashMap<>();
        // Ancienne prescription en retard, remplacee par une prescription plus recente non en retard : aucune alerte.
        prescriptionAnemie(CENTRE_A, patientA, "ANCIENNE", relatif(-17), epo, 8000, 1, "SEMAINE", null, null, null, null);
        prescriptionAnemie(CENTRE_A, patientA, "RECENTE", relatif(-3), epo, 8000, 1, "SEMAINE", null, null, null, null);
        nuit(CENTRE_A);
        cas.put("ancienne en retard, recente non echue", alertes());
        nettoyerDm2();
        decorPlanificateur();
        // Sans article, sans frequence, frequence nulle, unite absente : ignorees, quel que soit le retard.
        prescriptionAnemie(CENTRE_A, patientA, "SANS_ARTICLE", relatif(-10), null, 8000, 1, "SEMAINE", null, null, null, null);
        UUID p2 = patientDm2(CENTRE_A, "PAT_A2");
        prescriptionAnemie(CENTRE_A, p2, "SANS_FREQUENCE", relatif(-10), epo, 8000, null, null, null, null, null, null);
        UUID p3 = patientDm2(CENTRE_A, "PAT_A3");
        prescriptionAnemie(CENTRE_A, p3, "FREQUENCE_NULLE", relatif(-10), epo, 8000, 0, "SEMAINE", null, null, null, null);
        UUID p4 = patientDm2(CENTRE_A, "PAT_A4");
        prescription(CENTRE_A, p4, "CIBLES_SEULES", relatif(-10), 300, "70.00");
        nuit(CENTRE_A);
        cas.put("prescriptions incompletes", alertes());
        arbre("selection", cas);
    }

    @Test
    void le_controle_d_un_centre_ne_touche_pas_aux_autres_centres() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "PA", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        prescriptionAnemie(CENTRE_B, patientB, "PB", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        administration(CENTRE_B, patientA, "A_AUTRE_CENTRE", "EPO", relatif(-9), true, 8000, "UI");
        nuit(CENTRE_A);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("apres la nuit du centre A seulement (l'administration du centre B ne solde pas le centre A)", alertes());
        nuit(CENTRE_B);
        cas.put("apres la nuit du centre B", alertes());
        arbre("cloisonnement", cas);
    }

    @Test
    void le_controle_de_tous_les_centres_est_programme_a_6h30_et_ouvre_les_alertes_de_chaque_centre() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "PA", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        prescriptionAnemie(CENTRE_B, patientB, "PB", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        String cron = ObservancePrescriptionScheduler.class.getMethod("controlerObservance").getAnnotation(Scheduled.class).cron();
        assertThat(cron).isEqualTo("0 30 6 * * *");
        planificateur.controlerObservance();
        // Les autres centres de la base de test ne sont pas observes : seuls les deux centres du filet le sont.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alertes_observance WHERE center_id = ?", Integer.class, CENTRE_A)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alertes_observance WHERE center_id = ?", Integer.class, CENTRE_B)).isEqualTo(1);
    }

    @Test
    void le_message_est_limite_a_500_caracteres_et_les_alertes_listees_par_l_api_sont_celles_du_centre() throws Exception {
        decorPlanificateur();
        prescriptionAnemie(CENTRE_A, patientA, "P", relatif(-10), epo, 8000, 1, "SEMAINE", null, null, null, null);
        nuit(CENTRE_A);
        Resp r = doGet(PATIENTS_API + "/" + patientA + "/alertes-observance", as(CENTRE_A, "MEDECIN"));
        List<Object> lues = new ArrayList<>();
        corps(r).forEach(a -> lues.add(Map.of("type", a.path("type").asString(), "traitement", a.path("typeTraitement").asString(),
                "periodeDebut", a.path("periodeDebut").asString(), "periodeFin", a.path("periodeFin").asString(),
                "longueur du message <= 500", a.path("message").asString().length() <= 500)));
        arbre("api", lues);
    }
}
