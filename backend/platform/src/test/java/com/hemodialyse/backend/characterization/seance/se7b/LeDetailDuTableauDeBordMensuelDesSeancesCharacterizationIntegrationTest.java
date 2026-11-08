package com.hemodialyse.backend.characterization.seance.se7b;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * SE7b : le détail du tableau de bord mensuel ({@code GET /api/v1/seances/dashboard/details?kind=presence|absence}). A4 verrouille déjà
 * le type absent ou inconnu, les détails du centre B lus par l'administrateur du centre A et un jeu d'absences d'un mois ; ici : le
 * contenu et le tri des présences, les statuts, les bornes du mois, la normalisation du {@code kind}, l'absence de pagination, les lignes
 * d'un autre centre, l'écart avec les compteurs. Décrit le comportement ACTUEL.
 */
class LeDetailDuTableauDeBordMensuelDesSeancesCharacterizationIntegrationTest extends Se7bCharacterizationSupport {

    @Override
    protected String dossier() {
        return "details";
    }

    /** Six patients du centre A aux noms choisis pour le tri (casse, accent, deux prénoms pour un même nom). */
    private Map<String, UUID> patientsDuTri() {
        Map<String, UUID> p = new LinkedHashMap<>();
        p.put("Aouad", patientTb(CENTRE_A, "N1", "Aouad", "Zoubir", "M", null, ANCIEN, "PERMANENT", null, false, ""));
        p.put("Benali Karim", patientTb(CENTRE_A, "N2", "Benali", "Karim", "M", null, ANCIEN, "PERMANENT", null, false, ""));
        p.put("Benali Amine", patientTb(CENTRE_A, "N3", "Benali", "Amine", "M", null, ANCIEN, "PERMANENT", null, false, ""));
        p.put("ZIDANE", patientTb(CENTRE_A, "N4", "ZIDANE", "Yacine", "M", null, ANCIEN, "PERMANENT", null, false, ""));
        p.put("Emile", patientTb(CENTRE_A, "N5", "Émile", "Zola", "M", null, ANCIEN, "PERMANENT", null, false, ""));
        p.put("abdi", patientTb(CENTRE_A, "N6", "abdi", "Said", "M", null, ANCIEN, "PERMANENT", null, false, ""));
        return p;
    }

    @Test
    void le_detail_des_presences_est_trie_par_jour_puis_nom_puis_prenom_selon_l_ordre_des_caracteres() throws Exception {
        Map<String, UUID> p = patientsDuTri();
        UUID autreCentre = patientTb(CENTRE_B, "NB", "Autre", "Centre", "M", null, ANCIEN, "PERMANENT", null, false, "");
        for (UUID id : p.values()) {
            seanceTb(CENTRE_A, id, fixe(9), "VALIDEE");
        }
        seanceTb(CENTRE_A, p.get("ZIDANE"), fixe(2), "SIGNEE");
        seanceTb(CENTRE_A, p.get("Aouad"), fixe(30), "FACTUREE");
        seanceTb(CENTRE_A, p.get("Aouad"), fixe(0), "VALIDEE");
        seanceTb(CENTRE_A, p.get("Benali Karim"), fixe(10), "CREE");
        seanceTb(CENTRE_A, p.get("Benali Karim"), fixe(-1), "VALIDEE");
        seanceTb(CENTRE_A, p.get("Benali Karim"), fixe(31), "VALIDEE");
        seanceTb(CENTRE_B, autreCentre, fixe(9), "VALIDEE");
        fixe(58);
        Resp r = details(CENTRE_A, 2026, 1, "presence", infirmierA());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", r.status());
        m.put("entete", mapper.readTree(r.raw()).path("total"));
        m.put("premiere ligne brute", mapper.readTree(r.raw()).path("items").path(0));
        m.put("lignes", lignesDeDetail(r));
        arbre("presences", m);
    }

    @Test
    void le_detail_des_absences_exclut_les_annulees_et_les_patients_d_un_autre_centre_et_se_trie_de_meme() throws Exception {
        Map<String, UUID> p = patientsDuTri();
        UUID autreCentre = patientTb(CENTRE_B, "NB", "Autre", "Centre", "M", null, ANCIEN, "PERMANENT", null, false, "");
        absenceTb(CENTRE_A, p.get("abdi"), fixe(4), "A_QUALIFIER");
        absenceTb(CENTRE_A, p.get("Aouad"), fixe(4), "JUSTIFIEE");
        absenceTb(CENTRE_A, p.get("Benali Amine"), fixe(4), "NON_JUSTIFIEE");
        absenceTb(CENTRE_A, p.get("Benali Karim"), fixe(3), "RATTRAPEE");
        absenceTb(CENTRE_A, p.get("Emile"), fixe(4), "ANNULEE");
        absenceTb(CENTRE_A, p.get("ZIDANE"), fixe(-1), "A_QUALIFIER");
        absenceTb(CENTRE_A, p.get("ZIDANE"), fixe(31), "A_QUALIFIER");
        absenceTb(CENTRE_A, p.get("ZIDANE"), fixe(30), "A_QUALIFIER");
        absenceTb(CENTRE_B, autreCentre, fixe(4), "A_QUALIFIER");
        // absence du centre A pour un patient du centre B : la ligne est ecartee du detail (patient inconnu du centre)
        absenceTb(CENTRE_A, autreCentre, fixe(5), "A_QUALIFIER");
        fixe(0);
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = details(CENTRE_A, 2026, 1, "absence", medecinA());
        m.put("status", r.status());
        m.put("total", mapper.readTree(r.raw()).path("total"));
        m.put("lignes", lignesDeDetail(r));
        m.put("compteur du tableau de bord (absenceCount)", mapper.readTree(tableau(CENTRE_A, 2026, 1, adminA()).raw()).path("absenceCount"));
        arbre("absences", m);
    }

    @Test
    void le_type_est_normalise_sans_casse_ni_espaces_et_les_bornes_du_mois_sont_incluses() throws Exception {
        UUID q = patientTb(CENTRE_A, "K1", "Nom K1", "Pre K1", "F", null, ANCIEN, "PERMANENT", null, false, "");
        seanceTb(CENTRE_A, q, fixe(0), "VALIDEE");
        seanceTb(CENTRE_A, q, fixe(30), "VALIDEE");
        seanceTb(CENTRE_A, q, fixe(-1), "VALIDEE");
        seanceTb(CENTRE_A, q, fixe(31), "VALIDEE");
        absenceTb(CENTRE_A, q, fixe(1), "A_QUALIFIER");
        Map<String, Object> m = new LinkedHashMap<>();
        for (String kind : List.of("presence", "PRESENCE", "%20Presence%20", "absence", "ABSENCE", "%20aBsEnCe%20", "presences", "%20", "")) {
            Resp r = details(CENTRE_A, 2026, 1, kind, infirmierA());
            Map<String, Object> cas = new LinkedHashMap<>();
            cas.put("status", r.status());
            if (r.status() == 200) {
                cas.put("kind rendu", mapper.readTree(r.raw()).path("kind"));
                cas.put("lignes", lignesDeDetail(r));
            } else {
                cas.put("refus", refus(r));
            }
            m.put("kind=[" + kind + "]", cas);
        }
        arbre("kind_et_bornes", m);
    }

    @Test
    void le_detail_n_est_ni_pagine_ni_filtre_et_rend_toutes_les_lignes_du_mois() throws Exception {
        UUID a = patientTb(CENTRE_A, "L1", "Alpha", "Un", "M", null, ANCIEN, "PERMANENT", null, false, "");
        UUID b = patientTb(CENTRE_A, "L2", "Beta", "Deux", "M", null, ANCIEN, "PERMANENT", null, false, "");
        for (int j = 0; j <= 30; j++) {
            seanceTb(CENTRE_A, a, fixe(j), "VALIDEE");
            seanceTb(CENTRE_A, b, fixe(j), j % 3 == 0 ? "SIGNEE" : j % 3 == 1 ? "FACTUREE" : "VALIDEE");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        Resp r = details(CENTRE_A, 2026, 1, "presence", secretaireA());
        List<String> lignes = lignesDeDetail(r);
        m.put("total annonce", mapper.readTree(r.raw()).path("total"));
        m.put("lignes rendues", lignes.size());
        m.put("premieres", new ArrayList<>(lignes.subList(0, 3)));
        m.put("dernieres", new ArrayList<>(lignes.subList(lignes.size() - 3, lignes.size())));
        Resp avecParametres = doGet(SEANCES_API + "/dashboard/details" + requete(CENTRE_A, 2026, 1) + "&kind=presence&page=1&size=5&sort=patientNom,desc&patientId="
                + a + "&status=SIGNEE", secretaireA());
        m.put("avec page, size, sort, patientId et status (parametres ignores)", mapper.readTree(avecParametres.raw()).path("total"));
        m.put("corps identique pour l'infirmier et la secretaire", details(CENTRE_A, 2026, 1, "presence", infirmierA()).raw().equals(r.raw()));
        arbre("sans_pagination", m);
    }

    @Test
    void le_tableau_de_bord_compte_des_seances_que_le_detail_ne_liste_pas_quand_le_patient_est_d_un_autre_centre() throws Exception {
        UUID local = patientTb(CENTRE_A, "M1", "Local", "Un", "M", LocalDate.of(1980, 1, 1), ANCIEN, "PERMANENT", null, false, "");
        UUID etranger = patientTb(CENTRE_B, "MB", "Etranger", "Deux", "F", LocalDate.of(1990, 1, 1), ANCIEN, "PERMANENT", null, false, "");
        seanceTb(CENTRE_A, local, fixe(3), "VALIDEE");
        seanceTb(CENTRE_A, etranger, fixe(3), "VALIDEE");
        fixe(0);
        fixe(30);
        Map<String, Object> m = new LinkedHashMap<>();
        Resp t = tableau(CENTRE_A, 2026, 1, adminA());
        m.put("tableau de bord du centre A", mapper.readTree(t.raw()));
        Resp d = details(CENTRE_A, 2026, 1, "presence", adminA());
        m.put("details du centre A", lignesDeDetail(d));
        m.put("total des details", mapper.readTree(d.raw()).path("total"));
        arbre("ecart_compteur_detail", m);
    }
}
