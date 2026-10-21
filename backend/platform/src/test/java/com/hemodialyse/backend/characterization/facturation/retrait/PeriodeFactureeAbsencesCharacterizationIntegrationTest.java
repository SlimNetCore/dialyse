package com.hemodialyse.backend.characterization.facturation.retrait;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Slice facturation-f6-retrait-bc-facturation, consommateur Absences : une absence est « clôturée » quand une facture du
 * patient couvre sa date (lecture de {@code factures.period_start <= date <= period_end} par
 * {@code AbsenceDonneesJdbcAdapter.periodeFacturee}). Déjà verrouillé par {@code AbsencePatientIntegrationTest} (au niveau du
 * service) : qualification, annulation, déclaration et détection d'un patient dont la période est facturée. Ajouté ici, en
 * HTTP : bornes inclusives de la période, factures d'un autre patient ou d'un autre centre sans effet, et la route de
 * rattrapage (non couverte jusqu'ici).
 */
class PeriodeFactureeAbsencesCharacterizationIntegrationTest extends RetraitCharacterizationSupport {

    private static final String BASE = "/api/v1/absences-patients";
    private static final UUID FACTURE = UUID.fromString("99997600-0000-0000-0000-0000000f0001");
    private static final UUID FACTURE_AUTRE = UUID.fromString("99997600-0000-0000-0000-0000000f0002");
    private static final UUID FACTURE_CENTRE_B = UUID.fromString("99997600-0000-0000-0000-0000000f0003");

    @Override
    protected String theme() {
        return "absences";
    }

    /**
     * La déclaration d'une absence est limitée à 60 jours en arrière : les jours sont relatifs à l'exécution et NOMMÉS
     * dans les goldens. La période facturée va de J-20 à J-10 inclus.
     */
    private String AVANT;
    private String DEBUT;
    private String MILIEU;
    private String FIN;
    private String APRES;
    private String RATTRAPAGE;

    @BeforeEach
    void donnees() {
        AVANT = jour(21, "<J_MOINS_21>");
        DEBUT = jour(20, "<J_MOINS_20_DEBUT_DE_PERIODE>");
        MILIEU = jour(15, "<J_MOINS_15>");
        FIN = jour(10, "<J_MOINS_10_FIN_DE_PERIODE>");
        APRES = jour(9, "<J_MOINS_9>");
        RATTRAPAGE = jour(5, "<J_MOINS_5_RATTRAPAGE>");
        patient(PATIENT_1, CENTRE_A, "AMRANI");
        patient(PATIENT_2, CENTRE_A, "BELKACEM");
        patient(PATIENT_B, CENTRE_B, "BENALI");
    }

    private String declaration(UUID patient, String jour) {
        return json("{'patientId':'" + patient + "','dateSeance':'" + jour + "','motif':'MALADIE'}");
    }

    private Resp declarer(UUID patient, String jour) throws Exception {
        return appeler(post(BASE).param("centerId", CENTRE_A.toString()), adminA(), declaration(patient, jour));
    }

    private ObjectNode resume(Resp r) {
        ObjectNode o = mapper.createObjectNode();
        o.put("status", r.status());
        String code = r.raw() == null || r.raw().isBlank() ? null : mapper.readTree(r.raw()).path("code").asString(null);
        if (code != null) o.put("code", code);
        return o;
    }

    @Test
    void declarer_une_absence_dans_une_periode_facturee_est_refuse() throws Exception {
        facture(FACTURE, "<FACTURE_1>", CENTRE_A, PATIENT_1, "FAC-0001", DEBUT, FIN, FIN, "3500.00");
        Resp r = declarer(PATIENT_1, MILIEU);
        golden("declaration_periode_facturee", r);
        assertThat(compter("absence_patient", CENTRE_A)).isZero();
    }

    @Test
    void les_bornes_de_la_periode_facturee_sont_incluses() throws Exception {
        facture(FACTURE, "<FACTURE_1>", CENTRE_A, PATIENT_1, "FAC-0001", DEBUT, FIN, FIN, "3500.00");
        ObjectNode o = mapper.createObjectNode();
        String[] cles = {"veille_du_debut", "premier_jour", "dernier_jour", "lendemain_de_la_fin"};
        String[] jours = {AVANT, DEBUT, FIN, APRES};
        for (int i = 0; i < jours.length; i++) {
            String jour = jours[i];
            o.set(cles[i], resume(declarer(PATIENT_1, jour)));
        }
        o.set("absences", lignes("SELECT date_seance, statut, source FROM absence_patient WHERE center_id = ? ORDER BY date_seance",
                CENTRE_A));
        goldenNode("bornes_de_la_periode", o);
    }

    @Test
    void la_facture_d_un_autre_patient_ou_d_un_autre_centre_ne_cloture_pas_l_absence() throws Exception {
        facture(FACTURE_AUTRE, "<FACTURE_AUTRE_PATIENT>", CENTRE_A, PATIENT_2, "FAC-0002", DEBUT, FIN, FIN,
                "3500.00");
        facture(FACTURE_CENTRE_B, "<FACTURE_CENTRE_B>", CENTRE_B, PATIENT_1, "FAC-0003", DEBUT, FIN, FIN,
                "3500.00");
        ObjectNode o = mapper.createObjectNode();
        o.set("patient1_jour10", resume(declarer(PATIENT_1, MILIEU)));
        o.set("patient2_jour10", resume(declarer(PATIENT_2, MILIEU)));
        o.set("absences", lignes("SELECT patient_id, date_seance, statut FROM absence_patient WHERE center_id = ? "
                + "ORDER BY patient_id", CENTRE_A));
        goldenNode("autre_patient_autre_centre", o);
    }

    @Test
    void emettre_la_facture_apres_la_declaration_ferme_la_qualification_le_rattrapage_et_l_annulation() throws Exception {
        Resp declaree = declarer(PATIENT_1, MILIEU);
        assertThat(declaree.status()).isEqualTo(201);
        UUID absence = UUID.fromString(json(declaree).path("id").asString());
        nommer(absence, "<ABSENCE_1>");
        seance(UUID.fromString("99997600-0000-0000-0000-000000005001"), "<SEANCE_RATTRAPAGE>", CENTRE_A, PATIENT_1, "VALIDEE",
                RATTRAPAGE, null);
        facture(FACTURE, "<FACTURE_1>", CENTRE_A, PATIENT_1, "FAC-0001", DEBUT, FIN, FIN, "3500.00");

        ObjectNode o = mapper.createObjectNode();
        o.set("qualification", resume(appeler(put(BASE + "/{id}/qualification", absence).param("centerId", CENTRE_A.toString()),
                adminA(), json("{'motif':'HOSPITALISATION','commentaire':'x'}"))));
        o.set("rattrapage", resume(appeler(put(BASE + "/{id}/rattrapage", absence).param("centerId", CENTRE_A.toString()),
                adminA(), json("{'dateRattrapage':'" + RATTRAPAGE + "'}"))));
        o.set("annulation", resume(appeler(put(BASE + "/{id}/annulation", absence).param("centerId", CENTRE_A.toString()),
                adminA(), json("{'commentaire':'erreur'}"))));
        o.set("absenceInchangee", lignes("SELECT date_seance, statut, motif, source FROM absence_patient WHERE center_id = ?",
                CENTRE_A));
        goldenNode("modifications_apres_emission", o);
    }

    @Test
    void sans_facture_les_memes_routes_modifient_l_absence_temoin() throws Exception {
        Resp declaree = declarer(PATIENT_1, MILIEU);
        UUID absence = UUID.fromString(json(declaree).path("id").asString());
        nommer(absence, "<ABSENCE_1>");
        ObjectNode o = mapper.createObjectNode();
        o.set("qualification", resume(appeler(put(BASE + "/{id}/qualification", absence).param("centerId", CENTRE_A.toString()),
                adminA(), json("{'motif':'HOSPITALISATION','commentaire':'x'}"))));
        o.set("annulation", resume(appeler(put(BASE + "/{id}/annulation", absence).param("centerId", CENTRE_A.toString()),
                adminA(), json("{'commentaire':'erreur'}"))));
        o.set("absence", lignes("SELECT date_seance, statut, motif, source FROM absence_patient WHERE center_id = ?", CENTRE_A));
        goldenNode("modifications_sans_facture_temoin", o);
    }
}
