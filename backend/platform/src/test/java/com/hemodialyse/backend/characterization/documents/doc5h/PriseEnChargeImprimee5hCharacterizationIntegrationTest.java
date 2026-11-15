package com.hemodialyse.backend.characterization.documents.doc5h;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DOC5h : filet de rendu de la prise en charge imprimée ({@code PEC}), TOUT le texte du PDF, avant la conversion du gabarit
 * {@code prise_en_charge.jrxml} de son SQL embarqué (prise_en_charge jointe à patients et à centers, filtre à deux identifiants,
 * {@code ORDER BY date_fin_demande DESC LIMIT 1}) vers un jeu de données fourni par Patient et couverture (le nom du centre par l'annuaire
 * d'Organisation). Par type et par modèle : par identifiant de prise en charge, par identifiant de patient (la plus récente, dates absentes
 * comprises), les deux identifiants (la prise en charge l'emporte), dates et statut absents ou accordés, puis les cas vides et les
 * identifiants absents, vides ou illisibles de la voie par modèle. DONNÉES FICTIVES.
 */
class PriseEnChargeImprimee5hCharacterizationIntegrationTest extends DocumentDuPatient5hSupport {

    @Override
    protected String type() {
        return "PEC";
    }

    @Override
    protected String gabarit() {
        return "reports/prise_en_charge.jrxml";
    }

    private UUID pec(UUID centre, UUID patient, String alias, Integer debut, Integer fin, String statut, Integer debutEffectif,
                     Integer finEffectif) {
        UUID id = idStable("pec5h", alias + "-" + centre);
        aliasNommes.put(id.toString(), "<" + alias + ">");
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, statut, "
                        + "date_debut_effectif, date_fin_effectif, created_at) VALUES (?,?,?,?,?,?,?,?,?)", id, centre, patient,
                date(debut), date(fin), statut, date(debutEffectif), date(finEffectif), CREATION_FIXE);
        return id;
    }

    private java.sql.Date date(Integer n) {
        return n == null ? null : java.sql.Date.valueOf(fixe(n));
    }

    @Test
    void la_prise_en_charge_riche_s_imprime_par_identifiant_ou_par_patient_et_la_prise_en_charge_l_emporte() throws Exception {
        UUID eloi = patient(CENTRE_A, "ELOI", "Éléonore-Dupré", "Zoé-Émilie", "F", fixe(-9000), "1 61 03 75 123 456 78", "Rue", "0550");
        UUID autre = patient(CENTRE_A, "AUTRE", "Autre", "Patient", "M", fixe(-8000), "ASS-AUTRE", "Rue A", "0660");
        UUID ancienne = pec(CENTRE_A, eloi, "ANCIENNE", 1, 100, "VALIDEE", 2, 90);
        UUID recente = pec(CENTRE_A, eloi, "RECENTE", 101, 400, "EN_ATTENTE", null, null);
        UUID milieu = pec(CENTRE_A, eloi, "MILIEU", 50, 250, "validée à la main", 51, 249);
        UUID desAutres = pec(CENTRE_A, autre, "DE_L_AUTRE", 1, 900, "REFUSEE", null, null);

        Map<String, Object> cas = new LinkedHashMap<>();
        MockHttpServletResponse reponse = parType(CENTRE_A, param("pecId", milieu));
        assertEquals(200, reponse.getStatus());
        cas.put("par type, prise en charge du milieu", resume(reponse));
        cas.put("par type, prise en charge ancienne", resume(parType(CENTRE_A, param("pecId", ancienne))));
        cas.put("par type, patient : la plus recente", resume(parType(CENTRE_A, param("patientId", eloi))));
        cas.put("par type, les deux identifiants : la prise en charge l'emporte",
                resume(parType(CENTRE_A, param("patientId", eloi) + "," + param("pecId", desAutres))));
        cas.put("par type, prise en charge inconnue et patient : pas de repli",
                resume(parType(CENTRE_A, param("patientId", eloi) + "," + param("pecId", idStable("pec5h", "INCONNUE")))));
        cas.put("par modele, prise en charge recente", resume(parModele(MODELE_A, CENTRE_A, param("pecId", recente))));
        cas.put("par modele, patient : la plus recente", resume(parModele(MODELE_A, CENTRE_A, param("patientId", eloi))));
        cas.put("par modele, identifiant vide et patient", resume(parModele(MODELE_A, CENTRE_A, param("pecId", "") + ","
                + param("patientId", eloi))));
        arbre("pec_doc5h_riche_texte", cas);
    }

    @Test
    void les_dates_et_valeurs_absentes_s_impriment_comme_avant() throws Exception {
        UUID nu = patient(CENTRE_A, "NU", "Nu", "Sans", "M", null, "", null, null);
        UUID vide = patient(CENTRE_A, "VIDE", "", "", "", null, "", "", "");
        UUID a = pec(CENTRE_A, nu, "NU_1", null, null, "BROUILLON", null, null);
        UUID b = pec(CENTRE_A, vide, "VIDE_1", 4, null, "", null, 44);
        // deux prises en charge du même patient dont l'une sans date de fin de demande : l'ordre est celui du SGBD
        UUID sansFin = pec(CENTRE_A, nu, "NU_SANS_FIN", 7, null, "SANS_FIN", null, null);
        pec(CENTRE_A, nu, "NU_AVEC_FIN", 8, 80, "AVEC_FIN", null, null);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type, patient sans etat civil ni date", resume(parType(CENTRE_A, param("pecId", a))));
        cas.put("par type, patient aux valeurs vides", resume(parType(CENTRE_A, param("pecId", b))));
        cas.put("par type, sans date de fin", resume(parType(CENTRE_A, param("pecId", sansFin))));
        cas.put("par type, patient dont une prise en charge n'a pas de fin", resume(parType(CENTRE_A, param("patientId", nu))));
        arbre("pec_doc5h_minimale_texte", cas);
    }

    @Test
    void les_cas_vides_et_les_identifiants_de_la_voie_par_modele() throws Exception {
        UUID patientA = patient(CENTRE_A, "PAT_A", "Alpha", "Aline", "F", fixe(-7000), "ASS-A", "Rue A", "0550 00 00 01");
        UUID patientB = patient(CENTRE_B, "PAT_B", "Beta", "Bruno", "M", fixe(-6000), "ASS-B", "Rue B", "0550 00 00 02");
        UUID duCentreB = pec(CENTRE_B, patientB, "CENTRE_B", 1, 300, "VALIDEE", null, null);
        UUID patientEtranger = pec(CENTRE_A, patientB, "PATIENT_ETRANGER", 1, 301, "VALIDEE", null, null);
        pec(CENTRE_A, patientA, "ALPHA_1", 1, 200, "VALIDEE", null, null);

        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type, prise en charge inconnue", resume(parType(CENTRE_A, param("pecId", idStable("pec5h", "INCONNUE")))));
        cas.put("par type, patient inconnu", resume(parType(CENTRE_A, param("patientId", idStable("patient5h", "INCONNU")))));
        cas.put("par type, prise en charge du centre B demandee pour le centre A", resume(parType(CENTRE_A, param("pecId", duCentreB))));
        cas.put("par type, patient du centre B demande pour le centre A", resume(parType(CENTRE_A, param("patientId", patientB))));
        cas.put("par type, prise en charge dont le patient est d'un autre centre", resume(parType(CENTRE_A, param("pecId", patientEtranger))));
        cas.put("par type, aucun identifiant", resume(parType(CENTRE_A, "")));
        cas.put("par type, identifiant illisible", resume(parType(CENTRE_A, param("pecId", "pas-un-uuid"))));
        cas.put("par modele : aucun identifiant", resume(parModele(MODELE_A, CENTRE_A, "")));
        cas.put("par modele : identifiants vides", resume(parModele(MODELE_A, CENTRE_A, param("pecId", "") + "," + param("patientId", ""))));
        cas.put("par modele : prise en charge illisible", resume(parModele(MODELE_A, CENTRE_A, param("pecId", "pas-un-uuid"))));
        cas.put("par modele : patient illisible", resume(parModele(MODELE_A, CENTRE_A, param("patientId", "pas-un-uuid"))));
        cas.put("par modele : identifiant vide et patient illisible", resume(parModele(MODELE_A, CENTRE_A,
                param("pecId", "") + "," + param("patientId", "pas-un-uuid"))));
        cas.put("par modele : prise en charge connue et patient illisible", resume(parModele(MODELE_A, CENTRE_A,
                param("pecId", duCentreB) + "," + param("patientId", "pas-un-uuid"))));
        cas.put("par modele : patient d'un autre centre", resume(parModele(MODELE_A, CENTRE_A, param("patientId", patientB))));
        arbre("pec_doc5h_cas_vides_texte", cas);
    }
}
