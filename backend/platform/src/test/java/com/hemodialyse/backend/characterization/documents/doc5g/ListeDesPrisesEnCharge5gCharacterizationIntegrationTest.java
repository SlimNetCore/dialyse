package com.hemodialyse.backend.characterization.documents.doc5g;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DOC5g : filet de rendu de la liste des prises en charge ({@code LISTE_PEC}), TOUT le texte du PDF et les cellules du tableur, avant la
 * conversion du gabarit {@code liste_pec.jrxml} de son SQL embarqué (prise_en_charge jointe à patients, bornée au centre, triée par date de
 * fin de demande décroissante) vers un jeu de données fourni par Patient et couverture. Par type et par modèle, sur une liste riche
 * (plusieurs pages, dates et statuts absents, une prise en charge d'un autre centre, une dont le patient est d'un autre centre) et sur un
 * centre sans prise en charge. DONNÉES FICTIVES.
 */
class ListeDesPrisesEnCharge5gCharacterizationIntegrationTest extends ListesDeCouverture5gSupport {

    @Override
    protected String type() {
        return "LISTE_PEC";
    }

    @Override
    protected String gabarit() {
        return "reports/liste_pec.jrxml";
    }

    private void priseEnCharge(UUID centre, UUID patient, String alias, Integer debut, Integer fin, String statut) {
        jdbc.update("INSERT INTO prise_en_charge (id, center_id, patient_id, date_debut_demande, date_fin_demande, statut, created_at) "
                        + "VALUES (?,?,?,?,?,?,?)", idStable("pec5g", alias + "-" + centre), centre, patient,
                debut == null ? null : java.sql.Date.valueOf(fixe(debut)), fin == null ? null : java.sql.Date.valueOf(fixe(fin)), statut,
                CREATION_FIXE);
    }

    @Test
    void la_liste_riche_imprime_tout_le_texte_par_date_de_fin_decroissante_sur_plusieurs_pages() throws Exception {
        UUID amrani = patient(CENTRE_A, "AMRANI", "P-001", "Amrani", "Sara", "ASS-001");
        UUID eloi = patient(CENTRE_A, "ELOI", "P-002", "Éloi", "Émile", "ASS-002");
        UUID minuscule = patient(CENTRE_A, "MINUSCULE", "P-003", "benali", "Karim", "ASS-003");
        UUID sansCode = patient(CENTRE_A, "SANS_CODE", "", "Zéro", "Zélie", "");
        UUID etranger = patient(CENTRE_B, "ETRANGER", "P-900", "Etranger", "Autre", "ASS-900");
        priseEnCharge(CENTRE_A, amrani, "AMRANI_1", 1, 400, "VALIDEE");
        priseEnCharge(CENTRE_A, amrani, "AMRANI_2", 2, 300, "EN_ATTENTE");
        priseEnCharge(CENTRE_A, eloi, "ELOI_1", 3, 350, "REFUSEE");
        priseEnCharge(CENTRE_A, minuscule, "MINUSCULE_1", 4, 280, "validee");
        priseEnCharge(CENTRE_A, sansCode, "SANS_CODE_1", null, 500, "EN_ATTENTE");
        priseEnCharge(CENTRE_A, sansCode, "SANS_FIN", 6, null, "EN_ATTENTE");
        priseEnCharge(CENTRE_A, eloi, "SANS_DATE", null, null, "BROUILLON");
        for (int i = 0; i < 40; i++) {
            priseEnCharge(CENTRE_A, i % 2 == 0 ? amrani : eloi, "GEN_" + i, 10 + i, 600 + i, i % 3 == 0 ? "VALIDEE" : "EN_ATTENTE");
        }
        // une prise en charge d'un autre centre, et une du centre A dont le patient est d'un autre centre : exclues
        priseEnCharge(CENTRE_B, etranger, "ETRANGERE", 1, 700, "VALIDEE");
        priseEnCharge(CENTRE_A, etranger, "PATIENT_ETRANGER", 1, 701, "VALIDEE");

        Map<String, Object> cas = new LinkedHashMap<>();
        MockHttpServletResponse reponse = parType(CENTRE_A, null);
        assertEquals(200, reponse.getStatus());
        cas.put("par type (PDF)", resume(reponse));
        cas.put("par modele (PDF)", resume(parModele(MODELE_A, CENTRE_A)));
        cas.put("par type (tableur)", resume(parType(CENTRE_A, "EXCEL")));
        arbre("liste_pec_doc5g_riche_texte", cas);
    }

    @Test
    void un_centre_sans_prise_en_charge_imprime_le_document_comme_avant() throws Exception {
        UUID etranger = patient(CENTRE_B, "ETRANGER", "P-900", "Etranger", "Autre", "ASS-900");
        priseEnCharge(CENTRE_B, etranger, "ETRANGERE", 1, 700, "VALIDEE");
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type (PDF)", resume(parType(CENTRE_A, null)));
        cas.put("par type (tableur)", resume(parType(CENTRE_A, "EXCEL")));
        arbre("liste_pec_doc5g_vide_texte", cas);
    }
}
