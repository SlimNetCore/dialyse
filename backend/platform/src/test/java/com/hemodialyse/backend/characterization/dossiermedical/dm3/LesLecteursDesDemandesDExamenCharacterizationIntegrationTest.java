package com.hemodialyse.backend.characterization.dossiermedical.dm3;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DM3, lot 5 (consommateurs de la demande d'examen). Le seul lien du legacy est la référence SOUPLE {@code demandeExamenId} portée par
 * une observation biologique (DM6, {@code POST /patients/{id}/observations}, lecture {@code GET .../demandes-examen/{id}/observations}) :
 * ni clé étrangère, ni contrôle d'existence, ni effet sur le statut de la demande. La propriété de ces deux routes reste à DM6 ; ce lot ne
 * verrouille que ce que DM3 doit conserver (le statut n'est jamais piloté par les résultats). L'export FHIR (DM8) et le bilan pré-greffe
 * (DM7, {@code demandeExamenId} d'une étape) ne lisent pas les demandes (constat de lecture du code).
 */
class LesLecteursDesDemandesDExamenCharacterizationIntegrationTest extends ExamensCharacterizationSupport {

    @Override
    protected String dossier() {
        return "lecteurs";
    }

    private UUID patientA;
    private UUID patientB;

    private void deuxCentres() {
        patientA = patientDm3(CENTRE_A, "PAT_A");
        patientB = patientDm3(CENTRE_B, "PAT_B");
    }

    private String observation(UUID centre, UUID demande) {
        return "{\"centerId\":\"" + centre + "\"" + (demande == null ? "" : ",\"demandeExamenId\":\"" + demande + "\"")
                + ",\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"codeDisplay\":\"Hemoglobine fictive\",\"valeurNum\":11.5,"
                + "\"unite\":\"g/dL\",\"datePrelevement\":\"" + fixe(2) + "\"}";
    }

    @Test
    void saisir_une_observation_pour_une_demande_ne_change_pas_son_statut_et_la_demande_ne_se_valide_que_a_la_main() throws Exception {
        deuxCentres();
        UUID d = demandeSimple(CENTRE_A, patientA, "DEM", fixe(1), "PRELEVE");
        String avant = empreinte(d);
        Resp obs = doPost(PATIENTS_API + "/" + patientA + "/observations", observation(CENTRE_A, d), as(CENTRE_A, "MEDECIN"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("observation saisie", obs.status());
        m.put("demande liee", corps(obs).path("demandeExamenId").asString().equals(d.toString()));
        m.put("statut de la demande apres la saisie", statutEnBase(d));
        assertThat(empreinte(d)).isEqualTo(avant);
        Resp liste = doGet(demandesApi(patientA), as(CENTRE_A, "MEDECIN"));
        m.put("statut vu par la liste", corps(liste).path("items").get(0).path("statut").asString());
        Resp parDemande = doGet(demandesApi(patientA) + "/" + d + "/observations", as(CENTRE_A, "MEDECIN"));
        List<Object> lues = new ArrayList<>();
        for (JsonNode o : corps(parDemande)) lues.add(o.path("code").asString() + " " + o.path("valeurNum").asString());
        m.put("observations de la demande (route DM6)", Map.of("statut", parDemande.status(), "lues", lues));
        arbre("observation", m);
    }

    @Test
    void une_observation_peut_viser_une_demande_inconnue_ou_d_un_autre_centre_sans_controle() throws Exception {
        deuxCentres();
        UUID db = demandeSimple(CENTRE_B, patientB, "DEM_B", fixe(1), "DEMANDE");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("demande inconnue", doPost(PATIENTS_API + "/" + patientA + "/observations", observation(CENTRE_A, idStable("demande", "INCONNUE")),
                as(CENTRE_A, "MEDECIN")).status());
        m.put("demande du centre B, session A", doPost(PATIENTS_API + "/" + patientA + "/observations", observation(CENTRE_A, db),
                as(CENTRE_A, "MEDECIN")).status());
        m.put("observations lues pour la demande du centre B par la session A", corps(doGet(demandesApi(patientB) + "/" + db + "/observations",
                as(CENTRE_A, "MEDECIN"))).size());
        m.put("observations lues pour la demande du centre B par la session B", corps(doGet(demandesApi(patientB) + "/" + db + "/observations",
                as(CENTRE_B, "MEDECIN"))).size());
        m.put("statut de la demande du centre B", statutEnBase(db));
        aliasNommes.put(idStable("demande", "INCONNUE").toString(), "<DEM_INCONNUE>");
        arbre("references_souples", m);
    }

    @Test
    void les_lignes_d_une_demande_n_ont_pas_de_colonne_de_centre_et_suivent_leur_demande() throws Exception {
        deuxCentres();
        // ADR 0072 : le centre n'est porte que par demandes_examen ; lignes_demande_examen est jointe par demande_id.
        List<String> colonnes = jdbc.queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'lignes_demande_examen' ORDER BY COLUMN_NAME", String.class);
        arbre("colonnes_des_lignes", colonnes);
        assertThat(colonnes).doesNotContain("center_id");
        List<String> colonnesDemandes = jdbc.queryForList("SELECT LOWER(COLUMN_NAME) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE LOWER(TABLE_NAME) = 'demandes_examen' ORDER BY COLUMN_NAME", String.class);
        arbre("colonnes_des_demandes", colonnesDemandes);
    }
}
