package com.hemodialyse.backend.characterization.documents.doc5g;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DOC5g : filet de rendu de la liste des ouvertures de droit ({@code LISTE_ATTESTATIONS}), TOUT le texte du PDF et les cellules du
 * tableur, avant la conversion du gabarit {@code liste_attestations.jrxml} de son SQL embarqué (attestation_droit jointe à patients, bornée
 * au centre, triée par date de fin décroissante) vers un jeu de données fourni par Patient et couverture. Par type et par modèle, sur une
 * liste riche (plusieurs pages, noms accentués, champs absents, une attestation d'un autre centre, une attestation dont le patient est
 * d'un autre centre) et sur un centre sans attestation. DONNÉES FICTIVES.
 */
class ListeDesAttestations5gCharacterizationIntegrationTest extends ListesDeCouverture5gSupport {

    @Override
    protected String type() {
        return "LISTE_ATTESTATIONS";
    }

    @Override
    protected String gabarit() {
        return "reports/liste_attestations.jrxml";
    }

    private void attestation(UUID centre, UUID patient, String alias, int debut, int fin) {
        jdbc.update("INSERT INTO attestation_droit (id, center_id, patient_id, date_debut, date_fin, created_at) VALUES (?,?,?,?,?,?)",
                idStable("attestation5g", alias + "-" + centre), centre, patient, java.sql.Date.valueOf(fixe(debut)),
                java.sql.Date.valueOf(fixe(fin)), CREATION_FIXE);
    }

    @Test
    void la_liste_riche_imprime_tout_le_texte_par_date_de_fin_decroissante_sur_plusieurs_pages() throws Exception {
        UUID amrani = patient(CENTRE_A, "AMRANI", "P-001", "Amrani", "Sara", "ASS-001");
        UUID eloi = patient(CENTRE_A, "ELOI", "P-002", "Éloi", "Émile", "ASS-002");
        UUID minuscule = patient(CENTRE_A, "MINUSCULE", "P-003", "benali", "Karim", "ASS-003");
        UUID sansCode = patient(CENTRE_A, "SANS_CODE", "", "Zéro", "Zélie", "");
        UUID long_ = patient(CENTRE_A, "NOM_LONG", "P-005", "Nom très long qui dépasse largement la colonne prévue pour le nom du patient",
                "Prénom également très long pour vérifier la troncature ou le retour à la ligne", "ASS-005");
        UUID etranger = patient(CENTRE_B, "ETRANGER", "P-900", "Etranger", "Autre", "ASS-900");
        attestation(CENTRE_A, amrani, "AMRANI_1", 1, 400);
        attestation(CENTRE_A, amrani, "AMRANI_2", 2, 300);
        attestation(CENTRE_A, eloi, "ELOI_1", 3, 350);
        attestation(CENTRE_A, minuscule, "MINUSCULE_1", 4, 280);
        attestation(CENTRE_A, sansCode, "SANS_CODE_1", 5, 500);
        attestation(CENTRE_A, long_, "NOM_LONG_1", 6, 320);
        for (int i = 0; i < 40; i++) {
            attestation(CENTRE_A, i % 2 == 0 ? amrani : eloi, "GEN_" + i, 10 + i, 600 + i);
        }
        // une attestation d'un autre centre, et une attestation du centre A dont le patient est d'un autre centre : exclues
        attestation(CENTRE_B, etranger, "ETRANGERE", 1, 700);
        attestation(CENTRE_A, etranger, "PATIENT_ETRANGER", 1, 701);

        Map<String, Object> cas = new LinkedHashMap<>();
        MockHttpServletResponse reponse = parType(CENTRE_A, null);
        assertEquals(200, reponse.getStatus());
        cas.put("par type (PDF)", resume(reponse));
        cas.put("par modele (PDF)", resume(parModele(MODELE_A, CENTRE_A)));
        cas.put("par type (tableur)", resume(parType(CENTRE_A, "EXCEL")));
        arbre("liste_attestations_doc5g_riche_texte", cas);
    }

    @Test
    void un_centre_sans_attestation_imprime_le_document_comme_avant() throws Exception {
        UUID etranger = patient(CENTRE_B, "ETRANGER", "P-900", "Etranger", "Autre", "ASS-900");
        attestation(CENTRE_B, etranger, "ETRANGERE", 1, 700);
        Map<String, Object> cas = new LinkedHashMap<>();
        cas.put("par type (PDF)", resume(parType(CENTRE_A, null)));
        cas.put("par type (tableur)", resume(parType(CENTRE_A, "EXCEL")));
        arbre("liste_attestations_doc5g_vide_texte", cas);
    }
}
