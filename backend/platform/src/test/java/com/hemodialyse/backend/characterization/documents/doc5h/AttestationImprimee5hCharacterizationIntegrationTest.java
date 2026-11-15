package com.hemodialyse.backend.characterization.documents.doc5h;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DOC5h : filet de rendu de l'attestation d'ouverture de droit imprimée ({@code ATTESTATION}), TOUT le texte du PDF, avant la conversion du
 * gabarit {@code attestation.jrxml} de son SQL embarqué (attestation_droit jointe à patients et à centers, filtre à deux identifiants,
 * {@code ORDER BY date_fin DESC LIMIT 1}) vers un jeu de données fourni par Patient et couverture (le nom du centre par l'annuaire
 * d'Organisation). Par type et par modèle : par identifiant d'attestation, par identifiant de patient (la plus récente), les deux
 * identifiants (l'attestation l'emporte), un patient sans état civil, puis les cas vides et les identifiants absents, vides ou illisibles
 * de la voie par modèle. DONNÉES FICTIVES.
 */
class AttestationImprimee5hCharacterizationIntegrationTest extends DocumentDuPatient5hSupport {

    @Override
    protected String type() {
        return "ATTESTATION";
    }

    @Override
    protected String gabarit() {
        return "reports/attestation.jrxml";
    }

    private UUID attestation(UUID centre, UUID patient, String alias, int debut, int fin) {
        UUID id = idStable("attestation5h", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO attestation_droit (id, center_id, patient_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,?)",
                id, centre, patient, java.sql.Date.valueOf(fixe(debut)), java.sql.Date.valueOf(fixe(fin)), CREATION_FIXE);
        return id;
    }

    @Test
    void l_attestation_riche_s_imprime_par_attestation_ou_par_patient_et_l_attestation_l_emporte() throws Exception {
        UUID eloi = patient(CENTRE_A, "ELOI", "Éléonore-Dupré", "Zoé-Émilie", "F", fixe(-9000), "1 61 03 75 123 456 78",
                "12, rue de l'Étoile — bâtiment B\n16000 Alger", "0550 12 34 56");
        UUID autre = patient(CENTRE_A, "AUTRE", "Autre", "Patient", "M", fixe(-8000), "ASS-AUTRE", "Adresse autre", "0660 00 00 00");
        UUID ancienne = attestation(CENTRE_A, eloi, "ANCIENNE", 1, 100);
        UUID recente = attestation(CENTRE_A, eloi, "RECENTE", 101, 400);
        UUID milieu = attestation(CENTRE_A, eloi, "MILIEU", 50, 250);
        UUID desAutres = attestation(CENTRE_A, autre, "DE_L_AUTRE", 1, 900);

        Map<String, Object> cas = new LinkedHashMap<>();
        MockHttpServletResponse reponse = parType(CENTRE_A, param("attestationId", milieu));
        assertEquals(200, reponse.getStatus());
        cas.put("par type, attestation du milieu", resume(reponse));
        cas.put("par type, attestation ancienne", resume(parType(CENTRE_A, param("attestationId", ancienne))));
        cas.put("par type, patient : la plus recente", resume(parType(CENTRE_A, param("patientId", eloi))));
        cas.put("par type, les deux identifiants : l'attestation l'emporte",
                resume(parType(CENTRE_A, param("patientId", eloi) + "," + param("attestationId", desAutres))));
        cas.put("par type, attestation inconnue et patient : pas de repli",
                resume(parType(CENTRE_A, param("patientId", eloi) + "," + param("attestationId", idStable("attestation5h", "INCONNUE")))));
        cas.put("par modele, attestation recente", resume(parModele(MODELE_A, CENTRE_A, param("attestationId", recente))));
        cas.put("par modele, patient : la plus recente", resume(parModele(MODELE_A, CENTRE_A, param("patientId", eloi))));
        cas.put("par modele, attestation vide et patient", resume(parModele(MODELE_A, CENTRE_A, param("attestationId", "") + ","
                + param("patientId", eloi))));
        arbre("attestation_doc5h_riche_texte", cas);
    }

    @Test
    void un_patient_sans_etat_civil_imprime_les_valeurs_absentes_comme_avant() throws Exception {
        UUID nu = patient(CENTRE_A, "NU", "Nu", "Sans", "M", null, "", null, null);
        UUID vide = patient(CENTRE_A, "VIDE", "", "", "", null, "", "", "");
        UUID a = attestation(CENTRE_A, nu, "NU_1", 3, 33);
        UUID b = attestation(CENTRE_A, vide, "VIDE_1", 4, 44);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type, patient sans etat civil", resume(parType(CENTRE_A, param("attestationId", a))));
        cas.put("par type, patient aux valeurs vides", resume(parType(CENTRE_A, param("attestationId", b))));
        arbre("attestation_doc5h_minimale_texte", cas);
    }

    @Test
    void les_cas_vides_et_les_identifiants_de_la_voie_par_modele() throws Exception {
        UUID patientA = patient(CENTRE_A, "PAT_A", "Alpha", "Aline", "F", fixe(-7000), "ASS-A", "Rue A", "0550 00 00 01");
        UUID patientB = patient(CENTRE_B, "PAT_B", "Beta", "Bruno", "M", fixe(-6000), "ASS-B", "Rue B", "0550 00 00 02");
        UUID duCentreB = attestation(CENTRE_B, patientB, "CENTRE_B", 1, 300);
        // attestation du centre A dont le patient est d'un autre centre : la jointure sur le patient du centre l'écarte
        UUID patientEtranger = attestation(CENTRE_A, patientB, "PATIENT_ETRANGER", 1, 301);
        attestation(CENTRE_A, patientA, "ALPHA_1", 1, 200);

        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type, attestation inconnue", resume(parType(CENTRE_A, param("attestationId", idStable("attestation5h", "INCONNUE")))));
        cas.put("par type, patient inconnu", resume(parType(CENTRE_A, param("patientId", idStable("patient5h", "INCONNU")))));
        cas.put("par type, attestation du centre B demandee pour le centre A", resume(parType(CENTRE_A, param("attestationId", duCentreB))));
        cas.put("par type, patient du centre B demande pour le centre A", resume(parType(CENTRE_A, param("patientId", patientB))));
        cas.put("par type, attestation dont le patient est d'un autre centre", resume(parType(CENTRE_A, param("attestationId", patientEtranger))));
        cas.put("par type, aucun identifiant", resume(parType(CENTRE_A, "")));
        cas.put("par type, identifiant illisible", resume(parType(CENTRE_A, param("attestationId", "pas-un-uuid"))));
        cas.put("par modele : aucun identifiant", resume(parModele(MODELE_A, CENTRE_A, "")));
        cas.put("par modele : identifiants vides", resume(parModele(MODELE_A, CENTRE_A, param("attestationId", "") + "," + param("patientId", ""))));
        cas.put("par modele : attestation illisible", resume(parModele(MODELE_A, CENTRE_A, param("attestationId", "pas-un-uuid"))));
        cas.put("par modele : patient illisible", resume(parModele(MODELE_A, CENTRE_A, param("patientId", "pas-un-uuid"))));
        cas.put("par modele : attestation illisible mais vide cote patient", resume(parModele(MODELE_A, CENTRE_A,
                param("attestationId", "") + "," + param("patientId", "pas-un-uuid"))));
        cas.put("par modele : attestation connue et patient illisible", resume(parModele(MODELE_A, CENTRE_A,
                param("attestationId", duCentreB) + "," + param("patientId", "pas-un-uuid"))));
        cas.put("par modele : patient d'un autre centre", resume(parModele(MODELE_A, CENTRE_A, param("patientId", patientB))));
        arbre("attestation_doc5h_cas_vides_texte", cas);
    }
}
