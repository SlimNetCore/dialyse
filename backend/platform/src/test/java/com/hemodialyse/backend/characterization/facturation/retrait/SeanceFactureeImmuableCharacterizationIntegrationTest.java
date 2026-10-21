package com.hemodialyse.backend.characterization.facturation.retrait;

import com.hemodialyse.backend.infrastructure.persistence.adapter.SeanceBillingStatusJdbcAdapter;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Slice facturation-f6-retrait-bc-facturation, consommateurs Séance et Stock : une séance au statut {@code FACTUREE}
 * (posé par la Facturation à l'émission, F3) est immuable. Chaque route de modification d'une séance est rejouée sur une
 * séance facturée ; la réponse HTTP et l'état SQL (séance, stock) sont verrouillés, ainsi que la lecture du statut par le
 * port Stock {@code SeanceBillingStatusPort} (adaptateur de production appelé directement, sans double).
 * Les routes {@code DELETE /seances/{id}} (SeanceSuppressionIntegrationTest) et {@code PUT /stock/bons-sortie/{id}}
 * (BonSortieRestControllerIntegrationTest) sont déjà verrouillées ailleurs et ne sont pas dupliquées.
 */
class SeanceFactureeImmuableCharacterizationIntegrationTest extends RetraitCharacterizationSupport {

    private static final UUID FACTURE = UUID.fromString("99997600-0000-0000-0000-0000000f0001");
    private static final UUID SEANCE_FACTUREE = UUID.fromString("99997600-0000-0000-0000-000000005001");
    private static final UUID SEANCE_VALIDEE = UUID.fromString("99997600-0000-0000-0000-000000005002");
    private static final UUID SEANCE_AUTRE_CENTRE = UUID.fromString("99997600-0000-0000-0000-000000005003");

    @Autowired
    private SeanceBillingStatusJdbcAdapter etatDeFacturation;

    @Override
    protected String theme() {
        return "seance-facturee";
    }

    @BeforeEach
    void donnees() {
        article();
        forfait();
        patient(PATIENT_1, CENTRE_A, "AMRANI");
        patient(PATIENT_B, CENTRE_B, "BENALI");
        facture(FACTURE, "<FACTURE_1>", CENTRE_A, PATIENT_1, "FAC-0001", "2026-02-01", "2026-02-28", "2026-02-28", "3500.00");
        seance(SEANCE_FACTUREE, "<SEANCE_FACTUREE>", CENTRE_A, PATIENT_1, "FACTUREE", "2026-02-10", FACTURE);
        seance(SEANCE_VALIDEE, "<SEANCE_VALIDEE>", CENTRE_A, PATIENT_1, "VALIDEE", "2026-02-11", null);
        seance(SEANCE_AUTRE_CENTRE, "<SEANCE_AUTRE_CENTRE>", CENTRE_B, PATIENT_B, "FACTUREE", "2026-02-10", null);
    }

    private ObjectNode etat() {
        ObjectNode o = mapper.createObjectNode();
        o.set("seances", lignes("SELECT id, statut, date_seance, facture_id FROM seances WHERE center_id = ? ORDER BY date_seance, id",
                CENTRE_A));
        o.set("lot", lignes("SELECT quantite_restante FROM lots WHERE id = ?", LOT_1));
        o.put("bonsDeSortie", compter("bons_sortie", CENTRE_A));
        o.put("mouvementsDeStock", compter("stock_movements", CENTRE_A));
        o.put("voletsMedicaux", compter("volet_medical", CENTRE_A));
        o.put("voletsParamedicaux", compter("volet_paramedical", CENTRE_A));
        return o;
    }

    /**
     * Rejoue une requête, verrouille réponse et état avant/après ; renvoie vrai si l'état est strictement inchangé.
     */
    private boolean observer(String nom, Resp reponse, ObjectNode avant) {
        ObjectNode apres = etat();
        ObjectNode o = mapper.createObjectNode();
        o.put("status", reponse.status());
        if (reponse.raw() != null && !reponse.raw().isBlank()) {
            try {
                o.set("body", mapper.readTree(reponse.raw()));
            } catch (RuntimeException e) {
                o.put("bodyText", reponse.raw());
            }
        }
        boolean inchange = avant.equals(apres);
        o.put("etatInchange", inchange);
        o.set("etatApres", apres);
        goldenNode(nom, o);
        return inchange;
    }

    private static final String CENTRE = "'centerId':'" + CENTRE_A + "'";

    @Test
    void modifier_la_date_d_une_seance_facturee_est_refuse() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(put("/api/v1/seances/{id}", SEANCE_FACTUREE), adminA(),
                json("{" + CENTRE + ",'dateSeance':'2026-02-12'}"));
        assertThat(observer("date_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void modifier_la_date_d_une_seance_non_facturee_reste_possible() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(put("/api/v1/seances/{id}", SEANCE_VALIDEE), adminA(),
                json("{" + CENTRE + ",'dateSeance':'2026-02-12'}"));
        assertThat(observer("date_seance_validee_temoin", r, avant)).isFalse();
    }

    @Test
    void changer_le_forfait_d_une_seance_facturee_est_refuse() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(put("/api/v1/seances/{id}/forfait", SEANCE_FACTUREE), adminA(),
                json("{" + CENTRE + ",'forfaitId':'" + FORFAIT_1 + "','userId':'admin'}"));
        assertThat(observer("forfait_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void valider_une_seance_facturee_est_refuse() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(post("/api/v1/seances/{id}/valider", SEANCE_FACTUREE), adminA(),
                json("{" + CENTRE + ",'userId':'admin','consommations':[]}"));
        assertThat(observer("valider_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void le_medecin_ne_signe_pas_une_seance_facturee() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(post("/api/v1/seances/{id}/signer-medecin", SEANCE_FACTUREE), as(CENTRE_A, "MEDECIN"),
                json("{" + CENTRE + ",'userId':'medecin'}"));
        assertThat(observer("signer_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void ajouter_un_consommable_a_une_seance_facturee_est_refuse_et_le_stock_ne_bouge_pas() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(post("/api/v1/seances/{id}/consommables", SEANCE_FACTUREE), as(CENTRE_A, "INFIRMIER"),
                json("{" + CENTRE + ",'articleId':'" + ARTICLE_1 + "','quantite':2}"));
        assertThat(observer("consommable_ajout_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void modifier_un_consommable_d_une_seance_facturee_est_refuse() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(put("/api/v1/seances/{id}/consommables/{article}", SEANCE_FACTUREE, ARTICLE_1), as(CENTRE_A, "INFIRMIER"),
                json("{" + CENTRE + ",'userId':'inf','quantite':3}"));
        assertThat(observer("consommable_modification_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void retirer_un_consommable_d_une_seance_facturee_est_refuse() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(delete("/api/v1/seances/{id}/consommables/{article}", SEANCE_FACTUREE, ARTICLE_1)
                .param("centerId", CENTRE_A.toString()).param("userId", "inf"), as(CENTRE_A, "INFIRMIER"));
        assertThat(observer("consommable_retrait_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void le_volet_medical_d_une_seance_facturee_n_est_plus_modifiable() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(put("/api/v1/seances/{id}/volet-medical", SEANCE_FACTUREE), as(CENTRE_A, "MEDECIN"),
                json("{" + CENTRE + ",'prescription':'HD 4h','conclusionMedicale':'RAS'}"));
        assertThat(observer("volet_medical_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void le_volet_paramedical_d_une_seance_facturee_n_est_plus_modifiable() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(put("/api/v1/seances/{id}/volet-paramedical", SEANCE_FACTUREE), as(CENTRE_A, "INFIRMIER"),
                json("{" + CENTRE + ",'poidsAvantKg':72.5,'poidsApresKg':70.1}"));
        assertThat(observer("volet_paramedical_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void deverrouiller_en_regularisation_une_seance_facturee_est_refuse() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(post("/api/v1/seances/{id}/deverrouiller-regularisation", SEANCE_FACTUREE)
                .param("centerId", CENTRE_A.toString()), adminA());
        assertThat(observer("deverrouillage_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void administrer_un_traitement_sur_une_seance_facturee_ne_sort_rien_du_stock() throws Exception {
        ObjectNode avant = etat();
        Resp r = appeler(post("/api/v1/patients/{patient}/administrations-anemie", PATIENT_1), as(CENTRE_A, "INFIRMIER"),
                json("{" + CENTRE + ",'typeTraitement':'EPO','molecule':'Epoetine','voie':'IV',"
                        + "'dateAdministration':'2026-02-10','seanceId':'" + SEANCE_FACTUREE + "','administrePar':'inf',"
                        + "'administree':true,'articleId':'" + ARTICLE_1 + "','quantiteArticle':1}"));
        assertThat(observer("administration_traitement_seance_facturee_nouveau_code", r, avant)).isTrue();
    }

    @Test
    void le_port_stock_lit_le_statut_facturee_de_la_seance_du_centre_seulement() {
        CenterId a = CenterId.of(CENTRE_A);
        jdbc.update("UPDATE seances SET statut = 'facturee' WHERE id = ?", SEANCE_VALIDEE);
        assertThat(etatDeFacturation.isBilled(a, SEANCE_FACTUREE)).as("FACTUREE").isTrue();
        assertThat(etatDeFacturation.isBilled(a, SEANCE_VALIDEE)).as("statut en minuscules (insensible a la casse)").isTrue();
        jdbc.update("UPDATE seances SET statut = 'SIGNEE' WHERE id = ?", SEANCE_VALIDEE);
        assertThat(etatDeFacturation.isBilled(a, SEANCE_VALIDEE)).as("SIGNEE").isFalse();
        assertThat(etatDeFacturation.isBilled(a, UUID.fromString("99997600-0000-0000-0000-0000000050ff"))).as("inconnue").isFalse();
        assertThat(etatDeFacturation.isBilled(a, SEANCE_AUTRE_CENTRE)).as("facturee mais d'un autre centre").isFalse();
        assertThat(etatDeFacturation.isBilled(CenterId.of(CENTRE_B), SEANCE_AUTRE_CENTRE)).as("son propre centre").isTrue();
    }

    @Test
    void le_statut_facturee_se_lit_dans_la_table_seances_sans_consulter_la_facture() {
        jdbc.update("UPDATE seances SET facture_id = NULL WHERE id = ?", SEANCE_FACTUREE);
        assertThat(etatDeFacturation.isBilled(CenterId.of(CENTRE_A), SEANCE_FACTUREE))
                .as("seul le statut compte, pas le lien vers la facture").isTrue();
        jdbc.update("UPDATE seances SET statut = 'VALIDEE', facture_id = ? WHERE id = ?", FACTURE, SEANCE_FACTUREE);
        assertThat(etatDeFacturation.isBilled(CenterId.of(CENTRE_A), SEANCE_FACTUREE))
                .as("un lien vers une facture sans statut FACTUREE ne suffit pas").isFalse();
    }
}
