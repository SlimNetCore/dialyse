package com.hemodialyse.backend.characterization.organisation.pl11;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * PL11-0 : filet complementaire de l'administration generique des CRENEAUX ({@code position_creneau}), qui n'avaient AUCUN golden de
 * contenu dans {@code o2-administration}. Ecarts : KB-PL11-1, 2, 5, 9 (known-bugs.md).
 */
class CreneauxPl11CharacterizationIntegrationTest extends Pl11FiletSupport {

    private static final String ETAT = "SELECT code, libelle FROM position_creneau WHERE center_id = ? ORDER BY code";

    @Override
    protected String theme() {
        return "pl11-0-creneaux";
    }

    @Test
    void creation_modification_et_suppression_d_un_creneau() throws Exception {
        trace("creation", creer("creneaux", CENTRE_A, "{'code':'C1','libelle':'Matin'}", admin()));
        UUID c = creneau(CENTRE_A, "EXISTANT", "C2", "Apres-midi");
        trace("liste triee par code", lister("creneaux", CENTRE_A, null, admin()));
        trace("modification", modifier("creneaux", c, CENTRE_A, "{'code':'C2','libelle':'Apres-midi renomme'}", admin()));
        trace("modification sans libelle", modifier("creneaux", c, CENTRE_A, "{'code':'C2'}", admin()));
        trace("modification d'un creneau inconnu", modifier("creneaux", UUID.fromString("99998200-0000-0000-0000-0000000000e9"), CENTRE_A,
                "{'code':'C9','libelle':'x'}", admin()));
        UUID b = creneau(CENTRE_B, "B", "CB", "Creneau B");
        trace("modification d'un creneau d'un autre centre via A", modifier("creneaux", b, CENTRE_A, "{'code':'CB','libelle':'x'}", admin()));
        trace("suppression d'un creneau d'un autre centre via A", supprimer("creneaux", b, CENTRE_A, admin()));
        trace("suppression", supprimer("creneaux", c, CENTRE_A, admin()));
        trace("seconde suppression", supprimer("creneaux", c, CENTRE_A, admin()));
        traceSql("etat A : suppression physique", ETAT, CENTRE_A);
        traceSql("etat B intact", ETAT, CENTRE_B);
        verrouiller("cycle-de-vie");
    }

    @Test
    void la_suppression_est_refusee_selon_chaque_dependance() throws Exception {
        UUID s = salle(CENTRE_A, "S", "S1", "Salle");
        UUID cPatient = creneau(CENTRE_A, "C_PATIENT", "D1", "Avec patient");
        patient(CENTRE_A, null, cPatient, null);
        patient(CENTRE_A, null, cPatient, null);
        trace("creneau de deux patients", supprimer("creneaux", cPatient, CENTRE_A, admin()));

        UUID cAutre = creneau(CENTRE_A, "C_AUTRE_CENTRE", "D2", "Patient d'un autre centre");
        patient(CENTRE_B, null, cAutre, null);
        trace("usage compte hors du centre (identifiant seul)", supprimer("creneaux", cAutre, CENTRE_A, admin()));

        UUID cInfirmier = creneau(CENTRE_A, "C_INFIRMIER", "D3", "Affectation d'infirmier");
        affectationInfirmier(CENTRE_A, s, cInfirmier);
        trace("creneau d'une affectation d'infirmier : supprime sans refus", supprimer("creneaux", cInfirmier, CENTRE_A, admin()));

        UUID p = patient(CENTRE_A, null, null, null);
        UUID cMouvement = creneau(CENTRE_A, "C_MOUVEMENT", "D4", "Mouvement");
        mouvement(CENTRE_A, p, null, cMouvement);
        trace("creneau d'un mouvement de patient : supprime sans refus", supprimer("creneaux", cMouvement, CENTRE_A, admin()));

        UUID cCalendrier = creneau(CENTRE_A, "C_CALENDRIER", "D5", "Calendrier");
        caseDeCalendrier(CENTRE_A, s, cCalendrier);
        trace("creneau d'une case du calendrier : supprime sans refus", supprimer("creneaux", cCalendrier, CENTRE_A, admin()));

        UUID cSeance = creneau(CENTRE_A, "C_SEANCE", "D6", "Seance");
        seance(CENTRE_A, p, null, cSeance);
        trace("creneau d'une seance : supprime sans refus", supprimer("creneaux", cSeance, CENTRE_A, admin()));

        traceSql("creneaux restants", ETAT, CENTRE_A);
        verrouiller("refus-d-usage-par-dependance");
    }

    @Test
    void l_unicite_et_les_validations() throws Exception {
        UUID c1 = creneau(CENTRE_A, "C1", "C1", "Matin");
        UUID c2 = creneau(CENTRE_A, "C2", "C2", "Midi");
        trace("meme code", creer("creneaux", CENTRE_A, "{'code':'C1','libelle':'Doublon'}", admin()));
        trace("code en minuscules avec espaces", creer("creneaux", CENTRE_A, "{'code':' c1 ','libelle':'Doublon'}", admin()));
        trace("meme code dans le centre B : permis", creer("creneaux", CENTRE_B, "{'code':'C1','libelle':'B'}", as(CENTRE_B, "ADMIN")));
        trace("modification vers le code d'un autre", modifier("creneaux", c2, CENTRE_A, "{'code':'C1','libelle':'x'}", admin()));
        trace("modification qui garde son code", modifier("creneaux", c1, CENTRE_A, "{'code':'C1','libelle':'Matin bis'}", admin()));
        trace("code absent", creer("creneaux", CENTRE_A, "{'libelle':'x'}", admin()));
        trace("libelle absent", creer("creneaux", CENTRE_A, "{'code':'C7'}", admin()));
        trace("code de 51 caracteres", creer("creneaux", CENTRE_A, "{'code':'" + "c".repeat(51) + "','libelle':'x'}", admin()));
        trace("libelle de 255 caracteres", creer("creneaux", CENTRE_A, "{'code':'C8','libelle':'" + "l".repeat(255) + "'}", admin()));
        trace("libelle de 256 caracteres", creer("creneaux", CENTRE_A, "{'code':'C9','libelle':'" + "l".repeat(256) + "'}", admin()));
        trace("deux erreurs", creer("creneaux", CENTRE_A, "{'code':'','libelle':''}", admin()));
        traceSql("etat A", ETAT, CENTRE_A);
        traceSql("etat B", ETAT, CENTRE_B);
        verrouiller("unicite-et-validations");
    }

    @Test
    void la_lecture_cherche_et_pagine() throws Exception {
        creneau(CENTRE_A, "A", "P2", "Apres-midi (13h)");
        creneau(CENTRE_A, "B", "P1", "Matin (06h30)");
        creneau(CENTRE_A, "C", "P3", "Soir");
        trace("tout", lister("creneaux", CENTRE_A, null, admin()));
        trace("recherche par libelle", lister("creneaux", CENTRE_A, "search=MATIN", admin()));
        trace("recherche par code", lister("creneaux", CENTRE_A, "search=p3", admin()));
        trace("recherche sans resultat", lister("creneaux", CENTRE_A, "search=zzz", admin()));
        trace("page 1 de taille 2", lister("creneaux", CENTRE_A, "page=1&size=2", admin()));
        verrouiller("lecture-recherche-pagination");
    }

    @Test
    void l_ecriture_vide_le_cache_des_positions_du_seul_centre_modifie() throws Exception {
        creneau(CENTRE_A, "A", "P1", "A");
        creneau(CENTRE_B, "B", "P1", "B");
        trace("lecture mise en cache A", doGet(LECTURES + "/positions?centerId=" + CENTRE_A, admin()));
        trace("lecture mise en cache B", doGet(LECTURES + "/positions?centerId=" + CENTRE_B, as(CENTRE_B, "ADMIN")));
        creneau(CENTRE_A, "A2", "P2", "Ajout direct A");
        creneau(CENTRE_B, "B2", "P2", "Ajout direct B");
        trace("creation par l'administration dans A", creer("creneaux", CENTRE_A, "{'code':'P3','libelle':'Admin'}", admin()));
        trace("relecture A : voit tout", doGet(LECTURES + "/positions?centerId=" + CENTRE_A, admin()));
        trace("relecture B : cache intact", doGet(LECTURES + "/positions?centerId=" + CENTRE_B, as(CENTRE_B, "ADMIN")));
        UUID c = jdbc.queryForObject("SELECT id FROM position_creneau WHERE center_id = ? AND code = 'P2'", UUID.class, CENTRE_A);
        trace("suppression dans A", supprimer("creneaux", c, CENTRE_A, admin()));
        trace("relecture A apres suppression", doGet(LECTURES + "/positions?centerId=" + CENTRE_A, admin()));
        verrouiller("cache-des-positions");
    }

    @Test
    void l_import_des_creneaux() throws Exception {
        creneau(CENTRE_A, "E", "C1", "Ancien libelle");
        String valide = "code,libelle\nC1,Matin\nC2,Midi\n";
        trace("verification par defaut", importer("creneaux", CENTRE_A, false, valide, admin()));
        traceSql("rien d'ecrit", ETAT, CENTRE_A);
        trace("import reel : C1 mis a jour, C2 cree", importer("creneaux", CENTRE_A, true, valide, admin()));
        traceSql("etat", ETAT, CENTRE_A);
        trace("rejeu", importer("creneaux", CENTRE_A, true, valide, admin()));
        trace("en-tetes par alias", importer("creneaux", CENTRE_A, true, "Code creneau;Horaire\nC3;Soir\n", admin()));
        trace("doublon dans le fichier (casse)", importer("creneaux", CENTRE_A, true, "code,libelle\nC4,Nuit\nc4,Nuit bis\n", admin()));
        trace("libelle manquant sur une ligne", importer("creneaux", CENTRE_A, true, "code,libelle\nC5,\nC6,Six\n", admin()));
        trace("colonne obligatoire absente", importer("creneaux", CENTRE_A, true, "code\nC7\n", admin()));
        trace("fichier vide", importer("creneaux", CENTRE_A, false, "", admin()));
        traceSql("etat final : rien d'ecrit par les imports refuses", ETAT, CENTRE_A);
        verrouiller("import-des-creneaux");
    }

    @Test
    void le_modele_d_import_des_creneaux() throws Exception {
        trace("modele csv", doGet(A + "/creneaux/template?format=csv", admin()));
        trace("modele csv en majuscules", doGet(A + "/creneaux/template?format=CSV", admin()));
        verrouiller("modele-d-import");
    }

    @Test
    void les_ecritures_sont_tracees_par_le_journal_d_audit() throws Exception {
        UUID c = creneau(CENTRE_A, "C", "C1", "Un");
        UUID utilise = creneau(CENTRE_A, "U", "C2", "Utilise");
        patient(CENTRE_A, null, utilise, null);
        trace("creation", creer("creneaux", CENTRE_A, "{'code':'C3','libelle':'Trois'}", admin()));
        trace("modification", modifier("creneaux", c, CENTRE_A, "{'code':'C1','libelle':'Un bis'}", admin()));
        trace("suppression refusee", supprimer("creneaux", utilise, CENTRE_A, admin()));
        trace("suppression", supprimer("creneaux", c, CENTRE_A, admin()));
        trace("import reel", importer("creneaux", CENTRE_A, true, "code,libelle\nC5,Cinq\n", admin()));
        traceAudit("journal d'audit");
        verrouiller("audit_nouveau_code"); // PL11-1 : identifiant créé et type de référentiel tracés (ancien : voir _ecarts-legacy)
    }
}
