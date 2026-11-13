package com.hemodialyse.backend.characterization.organisation.pl11;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * PL11-0 : filet complementaire de l'administration generique des SALLES ({@code /api/v1/admin/referentials/salles}). Complete
 * {@code o2-administration} (sans le dupliquer) : refus d'usage, unicite, validations, remplacement complet, recherche, cache,
 * import, modele, erreur du controle d'usage, audit. Ecarts : KB-PL11-1, 2, 3, 7, 8, 9, 12 (known-bugs.md).
 */
class SallesPl11CharacterizationIntegrationTest extends Pl11FiletSupport {

    private static final String ETAT = "SELECT code, nom, isolement, capacite, ouverte FROM salle WHERE center_id = ? ORDER BY code";

    @Override
    protected String theme() {
        return "pl11-0-salles";
    }

    private UUID idDe(Resp r) {
        return UUID.fromString(mapper.readTree(r.raw()).path("id").asString());
    }

    @Test
    void la_suppression_est_refusee_selon_chaque_dependance() throws Exception {
        UUID cr = creneau(CENTRE_A, "CR", "C1", "Matin");
        UUID sPatient = salle(CENTRE_A, "S_PATIENT", "D1", "Avec patient");
        patient(CENTRE_A, sPatient, null, null);
        trace("salle d'un patient", supprimer("salles", sPatient, CENTRE_A, admin()));

        UUID sEquipement = salle(CENTRE_A, "S_EQUIPEMENT", "D2", "Avec equipement GMAO");
        equipement(CENTRE_A, sEquipement);
        trace("salle d'un equipement GMAO", supprimer("salles", sEquipement, CENTRE_A, admin()));

        UUID sDeux = salle(CENTRE_A, "S_DEUX", "D3", "Patient et equipement");
        patient(CENTRE_A, sDeux, null, null);
        patient(CENTRE_A, sDeux, null, null);
        equipement(CENTRE_A, sDeux);
        trace("trois usages (2 patients, 1 equipement) : le compte est dans le message", supprimer("salles", sDeux, CENTRE_A, admin()));

        UUID sAutreCentre = salle(CENTRE_A, "S_AUTRE_CENTRE", "D4", "Usage par un patient d'un autre centre");
        patient(CENTRE_B, sAutreCentre, null, null);
        trace("usage compte hors du centre (identifiant seul)", supprimer("salles", sAutreCentre, CENTRE_A, admin()));

        UUID sInfirmier = salle(CENTRE_A, "S_INFIRMIER", "D5", "Affectation d'infirmier");
        affectationInfirmier(CENTRE_A, sInfirmier, cr);
        trace("salle d'une affectation d'infirmier : supprimee sans refus", supprimer("salles", sInfirmier, CENTRE_A, admin()));

        UUID sMouvement = salle(CENTRE_A, "S_MOUVEMENT", "D6", "Mouvement de patient");
        UUID pMouvement = patient(CENTRE_A, null, null, null);
        mouvement(CENTRE_A, pMouvement, sMouvement, null);
        trace("salle d'un mouvement de patient : supprimee sans refus", supprimer("salles", sMouvement, CENTRE_A, admin()));

        UUID sCalendrier = salle(CENTRE_A, "S_CALENDRIER", "D7", "Calendrier de planification");
        caseDeCalendrier(CENTRE_A, sCalendrier, cr);
        trace("salle d'une case du calendrier : supprimee sans refus", supprimer("salles", sCalendrier, CENTRE_A, admin()));

        UUID sSeance = salle(CENTRE_A, "S_SEANCE", "D8", "Seance");
        seance(CENTRE_A, pMouvement, sSeance, null);
        trace("salle d'une seance : supprimee sans refus", supprimer("salles", sSeance, CENTRE_A, admin()));

        traceSql("salles restantes", ETAT, CENTRE_A);
        traceSql("affectations orphelines apres suppression", "SELECT COUNT(*) AS lignes FROM infirmier_affectation WHERE center_id = ?", CENTRE_A);
        traceSql("seances orphelines apres suppression",
                "SELECT COUNT(*) AS lignes FROM seances s WHERE s.center_id = ? AND NOT EXISTS (SELECT 1 FROM salle WHERE id = s.salle_id)", CENTRE_A);
        verrouiller("refus-d-usage-par-dependance");
    }

    @Test
    void l_unicite_du_code_est_par_centre_sans_casse_ni_espaces_repetes() throws Exception {
        UUID existante = salle(CENTRE_A, "EXISTANTE", "S1", "Salle une");
        UUID autre = salle(CENTRE_A, "AUTRE", "S2", "Salle deux");
        salle(CENTRE_B, "B1", "S1", "Salle une du centre B");
        trace("creation meme code", creer("salles", CENTRE_A, "{'code':'S1','nom':'Doublon'}", admin()));
        trace("creation code en minuscules", creer("salles", CENTRE_A, "{'code':'s1','nom':'Doublon'}", admin()));
        trace("creation code entoure d'espaces", creer("salles", CENTRE_A, "{'code':'  S1  ','nom':'Doublon'}", admin()));
        trace("creation code a espaces internes repetes puis code distinct", creer("salles", CENTRE_A, "{'code':'S  3','nom':'Trois'}", admin()));
        trace("creation de 'S 3' (espaces internes ramenes a un)", creer("salles", CENTRE_A, "{'code':'S 3','nom':'Trois bis'}", admin()));
        trace("meme code dans le centre B : permis (cle par centre)", creer("salles", CENTRE_B, "{'code':'S2','nom':'Deux B'}", as(CENTRE_B, "ADMIN")));
        trace("modification vers le code d'une autre salle", modifier("salles", autre, CENTRE_A, "{'code':'S1','nom':'Deux'}", admin()));
        trace("modification qui garde son propre code", modifier("salles", existante, CENTRE_A, "{'code':'S1','nom':'Une renommee'}", admin()));
        trace("modification qui change la casse de son propre code", modifier("salles", existante, CENTRE_A, "{'code':'s1','nom':'Une'}", admin()));
        traceSql("etat centre A", ETAT, CENTRE_A);
        traceSql("etat centre B", ETAT, CENTRE_B);
        verrouiller("unicite-du-code");
    }

    @Test
    void les_validations_de_champs() throws Exception {
        trace("code absent", creer("salles", CENTRE_A, "{'nom':'Sans code'}", admin()));
        trace("code vide", creer("salles", CENTRE_A, "{'code':'','nom':'x'}", admin()));
        trace("code blanc", creer("salles", CENTRE_A, "{'code':'   ','nom':'x'}", admin()));
        trace("code de 50 caracteres", creer("salles", CENTRE_A, "{'code':'" + "C".repeat(50) + "','nom':'limite'}", admin()));
        trace("code de 51 caracteres", creer("salles", CENTRE_A, "{'code':'" + "D".repeat(51) + "','nom':'trop long'}", admin()));
        trace("nom de 255 caracteres", creer("salles", CENTRE_A, "{'code':'N255','nom':'" + "n".repeat(255) + "'}", admin()));
        trace("nom de 256 caracteres", creer("salles", CENTRE_A, "{'code':'N256','nom':'" + "n".repeat(256) + "'}", admin()));
        trace("capacite 0", creer("salles", CENTRE_A, "{'code':'K0','nom':'x','capacite':'0'}", admin()));
        trace("capacite 1", creer("salles", CENTRE_A, "{'code':'K1','nom':'x','capacite':'1'}", admin()));
        trace("capacite 999", creer("salles", CENTRE_A, "{'code':'K999','nom':'x','capacite':'999'}", admin()));
        trace("capacite 1000", creer("salles", CENTRE_A, "{'code':'K1000','nom':'x','capacite':'1000'}", admin()));
        trace("capacite negative", creer("salles", CENTRE_A, "{'code':'KNEG','nom':'x','capacite':'-1'}", admin()));
        trace("capacite texte", creer("salles", CENTRE_A, "{'code':'KABC','nom':'x','capacite':'abc'}", admin()));
        trace("capacite decimale", creer("salles", CENTRE_A, "{'code':'KDEC','nom':'x','capacite':'12.5'}", admin()));
        trace("capacite entouree d'espaces", creer("salles", CENTRE_A, "{'code':'KESP','nom':'x','capacite':' 5 '}", admin()));
        trace("capacite vide", creer("salles", CENTRE_A, "{'code':'KVIDE','nom':'x','capacite':''}", admin()));
        trace("capacite JSON numerique", creer("salles", CENTRE_A, "{'code':'KNUM','nom':'x','capacite':7}", admin()));
        trace("isolement OUI en minuscules", creer("salles", CENTRE_A, "{'code':'I1','nom':'x','isolement':'oui'}", admin()));
        trace("isolement avec accent/casse mixte", creer("salles", CENTRE_A, "{'code':'I2','nom':'x','isolement':'Oui '}", admin()));
        trace("isolement hors liste", creer("salles", CENTRE_A, "{'code':'I3','nom':'x','isolement':'PEUT-ETRE'}", admin()));
        trace("isolement vide", creer("salles", CENTRE_A, "{'code':'I4','nom':'x','isolement':''}", admin()));
        trace("isolement JSON booleen", creer("salles", CENTRE_A, "{'code':'I5','nom':'x','isolement':true}", admin()));
        trace("champ inconnu ignore", creer("salles", CENTRE_A, "{'code':'F1','nom':'x','couleur':'rouge'}", admin()));
        trace("plusieurs erreurs a la fois", creer("salles", CENTRE_A, "{'code':'','nom':'','capacite':'0','isolement':'Z'}", admin()));
        traceSql("etat en base", ETAT, CENTRE_A);
        verrouiller("validations-de-champs");
    }

    @Test
    void la_modification_remplace_toutes_les_colonnes_du_descripteur() throws Exception {
        UUID s = salle(CENTRE_A, "S", "S1", "Salle une");
        jdbc.update("UPDATE salle SET capacite = 12, isolement = 'OUI', ouverte = FALSE WHERE id = ?", s);
        traceSql("avant", ETAT, CENTRE_A);
        trace("modification sans capacite ni isolement", modifier("salles", s, CENTRE_A, "{'code':'S1','nom':'Renommee'}", admin()));
        traceSql("apres : capacite effacee, isolement remis a NON, ouverte conservee", ETAT, CENTRE_A);
        trace("remise d'une capacite", modifier("salles", s, CENTRE_A, "{'code':'S1','nom':'Renommee','capacite':'8','isolement':'OUI'}", admin()));
        trace("capacite nulle explicite", modifier("salles", s, CENTRE_A, "{'code':'S1','nom':'Renommee','capacite':null}", admin()));
        traceSql("apres capacite nulle", ETAT, CENTRE_A);
        trace("modification invalide : rien n'est ecrit", modifier("salles", s, CENTRE_A, "{'code':'S1','nom':'','capacite':'0'}", admin()));
        trace("modification d'une salle inconnue", modifier("salles", UUID.fromString("99998200-0000-0000-0000-0000000000e9"), CENTRE_A,
                "{'code':'S9','nom':'x'}", admin()));
        UUID b = salle(CENTRE_B, "B", "SB", "Salle B");
        trace("modification d'une salle d'un autre centre via le centre A", modifier("salles", b, CENTRE_A, "{'code':'SB','nom':'x'}", admin()));
        traceSql("etat final A", ETAT, CENTRE_A);
        traceSql("etat final B", ETAT, CENTRE_B);
        verrouiller("modification-remplacement-complet");
    }

    @Test
    void la_lecture_cherche_trie_et_pagine() throws Exception {
        salle(CENTRE_A, "A", "B2", "Salle Beta");
        salle(CENTRE_A, "B", "A1", "Salle Alpha");
        salle(CENTRE_A, "C", "C_3", "Salle 100%");
        salle(CENTRE_A, "D", "D4", "Autre");
        salle(CENTRE_B, "E", "A9", "Salle Alpha de B");
        trace("tout", lister("salles", CENTRE_A, null, admin()));
        trace("recherche par nom (casse ignoree)", lister("salles", CENTRE_A, "search=ALPHA", admin()));
        trace("recherche par code", lister("salles", CENTRE_A, "search=b2", admin()));
        trace("recherche avec pourcentage litteral", lister("salles", CENTRE_A, "search=%25", admin()));
        trace("recherche avec soulignement litteral", lister("salles", CENTRE_A, "search=_", admin()));
        trace("recherche blanche = tout", lister("salles", CENTRE_A, "search=%20%20", admin()));
        trace("recherche sans resultat", lister("salles", CENTRE_A, "search=zzz", admin()));
        trace("page 1 de taille 2", lister("salles", CENTRE_A, "page=1&size=2", admin()));
        trace("taille 0 = 20 par defaut", lister("salles", CENTRE_A, "size=0", admin()));
        trace("taille negative = 20", lister("salles", CENTRE_A, "size=-3", admin()));
        trace("taille 1000 bornee a 100", lister("salles", CENTRE_A, "size=1000", admin()));
        trace("page negative ramenee a 0", lister("salles", CENTRE_A, "page=-1&size=2", admin()));
        trace("page au-dela de la fin", lister("salles", CENTRE_A, "page=9&size=2", admin()));
        verrouiller("lecture-recherche-pagination");
    }

    @Test
    void l_ecriture_vide_les_caches_du_seul_centre_modifie() throws Exception {
        salle(CENTRE_A, "A", "S1", "Salle A");
        salle(CENTRE_B, "B", "S1", "Salle B");
        trace("lecture mise en cache A", doGet(LECTURES + "/salles?centerId=" + CENTRE_A, admin()));
        trace("lecture mise en cache B", doGet(LECTURES + "/salles?centerId=" + CENTRE_B, as(CENTRE_B, "ADMIN")));
        salle(CENTRE_A, "A2", "S2", "Ajout direct A (non vu : cache)");
        salle(CENTRE_B, "B2", "S2", "Ajout direct B (non vu : cache)");
        trace("relecture A : encore le cache", doGet(LECTURES + "/salles?centerId=" + CENTRE_A, admin()));
        trace("creation par l'administration dans A", creer("salles", CENTRE_A, "{'code':'S3','nom':'Admin A'}", admin()));
        trace("relecture A : cache vide, voit tout", doGet(LECTURES + "/salles?centerId=" + CENTRE_A, admin()));
        trace("relecture B : cache du centre B intact", doGet(LECTURES + "/salles?centerId=" + CENTRE_B, as(CENTRE_B, "ADMIN")));
        verrouiller("cache-des-salles");
    }

    @Test
    void l_ecriture_d_une_salle_vide_aussi_le_cache_des_generateurs() throws Exception {
        UUID s = salle(CENTRE_A, "A", "S1", "Salle A");
        trace("generateurs mis en cache", doGet(LECTURES + "/generateurs?centerId=" + CENTRE_A, admin()));
        equipement(CENTRE_A, s);
        trace("relecture : encore le cache", doGet(LECTURES + "/generateurs?centerId=" + CENTRE_A, admin()));
        trace("modification de la salle par l'administration", modifier("salles", s, CENTRE_A, "{'code':'S1','nom':'Renommee'}", admin()));
        trace("relecture : cache vide", doGet(LECTURES + "/generateurs?centerId=" + CENTRE_A, admin()));
        verrouiller("cache-des-generateurs");
    }

    @Test
    void l_import_des_salles() throws Exception {
        UUID s = salle(CENTRE_A, "S", "S1", "Existante");
        jdbc.update("UPDATE salle SET capacite = 12, isolement = 'OUI', ouverte = FALSE WHERE id = ?", s);
        String valide = "code,nom,isolement,capacite\nS1,Renommee,NON,\nS2,Deux,OUI,6\n";
        trace("verification par defaut", importer("salles", CENTRE_A, false, valide, admin()));
        traceSql("rien d'ecrit", ETAT, CENTRE_A);
        trace("import reel", importer("salles", CENTRE_A, true, valide, admin()));
        traceSql("etat apres import : capacite de S1 effacee, ouverte conservee", ETAT, CENTRE_A);
        trace("en-tetes par alias", importer("salles", CENTRE_A, true, "Code salle;Libelle;Salle isolement;Nombre de generateurs\nS3;Trois;oui;4\n", admin()));
        trace("sans les colonnes facultatives : isolement et capacite remis a zero", importer("salles", CENTRE_A, true, "code,nom\nS2,Deux bis\n", admin()));
        traceSql("etat", ETAT, CENTRE_A);
        trace("capacite invalide, isolement invalide, doublon dans le fichier",
                importer("salles", CENTRE_A, true, "code,nom,isolement,capacite\nS5,Cinq,PEUT,5\nS6,Six,NON,0\nS7,Sept,NON,x\nS8,Huit,NON,1\ns8,Huit bis,NON,2\n", admin()));
        trace("colonne inconnue ignoree", importer("salles", CENTRE_A, false, "code,nom,couleur\nS9,Neuf,rouge\n", admin()));
        trace("fichier sans ligne de donnees", importer("salles", CENTRE_A, false, "code,nom\n", admin()));
        trace("colonne obligatoire absente (nom)", importer("salles", CENTRE_A, false, "code\nS9\n", admin()));
        trace("lignes blanches ignorees", importer("salles", CENTRE_A, false, "code,nom\n\nS9,Neuf\n,\n", admin()));
        StringBuilder gros = new StringBuilder("code,nom\n");
        for (int i = 0; i < 5001; i++) gros.append("G").append(i).append(",Salle ").append(i).append('\n');
        trace("5001 lignes", importer("salles", CENTRE_A, false, gros.toString(), admin()));
        traceSql("etat final", ETAT, CENTRE_A);
        verrouiller("import-des-salles");
    }

    @Test
    void le_modele_d_import_des_salles() throws Exception {
        trace("modele csv", doGet(A + "/salles/template?format=csv", admin()));
        Resp xlsx = doGet(A + "/salles/template?format=pdf", admin());
        traceNote("modele format inconnu = xlsx (le contenu zip n'est pas deterministe) : statut", String.valueOf(xlsx.status()));
        traceNote("modele format inconnu = xlsx : type", xlsx.contentType());
        verrouiller("modele-d-import");
    }

    @Test
    void une_erreur_du_controle_d_usage_autorise_la_suppression() throws Exception {
        UUID s = salle(CENTRE_A, "S", "S1", "Avec equipement");
        equipement(CENTRE_A, s);
        trace("controle normal", supprimer("salles", s, CENTRE_A, admin()));
        jdbc.execute("ALTER TABLE gmao_equipements RENAME TO gmao_equipements_hors_service");
        try {
            trace("table des equipements indisponible : suppression autorisee", supprimer("salles", s, CENTRE_A, admin()));
        } finally {
            jdbc.execute("ALTER TABLE gmao_equipements_hors_service RENAME TO gmao_equipements");
        }
        traceSql("salle en base", ETAT, CENTRE_A);
        traceSql("equipement orphelin", "SELECT COUNT(*) AS lignes FROM gmao_equipements WHERE centre_id = ? AND salle_id = ?", CENTRE_A, s);
        verrouiller("erreur-du-controle-d-usage");
    }

    @Test
    void les_ecritures_sont_tracees_par_le_journal_d_audit() throws Exception {
        UUID s = salle(CENTRE_A, "S", "S1", "Une");
        UUID utilisee = salle(CENTRE_A, "U", "S2", "Utilisee");
        patient(CENTRE_A, utilisee, null, null);
        trace("creation", creer("salles", CENTRE_A, "{'code':'S3','nom':'Trois'}", admin()));
        trace("creation invalide", creer("salles", CENTRE_A, "{'code':'','nom':''}", admin()));
        trace("modification", modifier("salles", s, CENTRE_A, "{'code':'S1','nom':'Renommee'}", admin()));
        trace("suppression refusee", supprimer("salles", utilisee, CENTRE_A, admin()));
        trace("suppression", supprimer("salles", s, CENTRE_A, admin()));
        trace("suppression inconnue", supprimer("salles", UUID.fromString("99998200-0000-0000-0000-0000000000e9"), CENTRE_A, admin()));
        trace("verification d'import", importer("salles", CENTRE_A, false, "code,nom\nS5,Cinq\n", admin()));
        trace("import reel", importer("salles", CENTRE_A, true, "code,nom\nS5,Cinq\n", admin()));
        trace("lecture", lister("salles", CENTRE_A, null, admin()));
        trace("modele", doGet(A + "/salles/template?format=csv", admin()));
        trace("refus 403 secretaire", creer("salles", CENTRE_A, "{'code':'S6','nom':'Six'}", as(CENTRE_A, "SECRETAIRE")));
        trace("refus 403 autre centre", creer("salles", CENTRE_B, "{'code':'S7','nom':'Sept'}", admin()));
        traceAudit("journal d'audit (centre de session A)");
        verrouiller("audit_nouveau_code"); // PL11-1 : identifiant créé et type de référentiel tracés (ancien : voir _ecarts-legacy)
    }
}
