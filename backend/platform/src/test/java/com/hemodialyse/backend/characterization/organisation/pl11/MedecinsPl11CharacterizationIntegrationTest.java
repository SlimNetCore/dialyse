package com.hemodialyse.backend.characterization.organisation.pl11;

import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * PL11-0 : filet complementaire de l'administration generique des MEDECINS. Complete {@code o2-administration} (suppression et
 * import deja verrouilles) : creation/modification (contenu), unicite nom+prenom, refus d'usage, desactivation d'un medecin utilise
 * (route d'Organisation), medecin desactive invisible (recreation, reimport), recherche, cache, audit.
 * Ecarts : KB-PL11-2, 4, 5, 6, 9, 11 (known-bugs.md).
 */
class MedecinsPl11CharacterizationIntegrationTest extends Pl11FiletSupport {

    private static final String ETAT = "SELECT nom, prenom, specialite, actif FROM medecin WHERE center_id = ? ORDER BY nom, prenom, actif";

    @Override
    protected String theme() {
        return "pl11-0-medecins";
    }

    private static final UUID INCONNU = UUID.fromString("99998200-0000-0000-0000-0000000000e9");

    @Test
    void creation_et_modification_d_un_medecin() throws Exception {
        trace("creation complete", creer("medecins", CENTRE_A, "{'nom':'Benali','prenom':'Karim','specialite':'Nephrologue'}", admin()));
        trace("creation nom seul", creer("medecins", CENTRE_A, "{'nom':'Seul'}", admin()));
        trace("creation prenom vide et specialite vide", creer("medecins", CENTRE_A, "{'nom':'Vide','prenom':'','specialite':''}", admin()));
        trace("creation avec espaces autour", creer("medecins", CENTRE_A, "{'nom':'  Espace  ','prenom':'  Doc  ','specialite':'  X  '}", admin()));
        traceSql("etat apres creations (valeurs normalisees ou non)", ETAT, CENTRE_A);
        UUID m = medecin(CENTRE_A, "M", "Ouaret", "Mustapha", "Cardiologue");
        trace("liste triee nom, prenom", lister("medecins", CENTRE_A, null, admin()));
        trace("modification complete", modifier("medecins", m, CENTRE_A, "{'nom':'Ouaret','prenom':'Mustapha','specialite':'Nephrologue'}", admin()));
        trace("modification sans specialite ni prenom : remplacement complet", modifier("medecins", m, CENTRE_A, "{'nom':'Ouaret'}", admin()));
        traceSql("etat apres modification", ETAT, CENTRE_A);
        trace("modification sans nom", modifier("medecins", m, CENTRE_A, "{'prenom':'x'}", admin()));
        trace("modification d'un medecin inconnu", modifier("medecins", INCONNU, CENTRE_A, "{'nom':'x'}", admin()));
        UUID b = medecin(CENTRE_B, "B", "MedecinB", "Pierre", null);
        trace("modification d'un medecin d'un autre centre via A", modifier("medecins", b, CENTRE_A, "{'nom':'x'}", admin()));
        traceSql("etat B intact", ETAT, CENTRE_B);
        verrouiller("creation-et-modification");
    }

    @Test
    void l_unicite_est_sur_nom_et_prenom() throws Exception {
        UUID k = medecin(CENTRE_A, "K", "Benali", "Karim", "Nephrologue");
        UUID s = medecin(CENTRE_A, "S", "Seul", null, null);
        medecin(CENTRE_B, "B", "Benali", "Karim", null);
        trace("meme nom et prenom", creer("medecins", CENTRE_A, "{'nom':'Benali','prenom':'Karim'}", admin()));
        trace("casse et espaces differents", creer("medecins", CENTRE_A, "{'nom':' BENALI ','prenom':'karim'}", admin()));
        trace("meme nom, autre prenom : permis", creer("medecins", CENTRE_A, "{'nom':'Benali','prenom':'Sofiane'}", admin()));
        trace("meme nom sans prenom : permis", creer("medecins", CENTRE_A, "{'nom':'Benali'}", admin()));
        trace("sans prenom, doublon d'un medecin sans prenom", creer("medecins", CENTRE_A, "{'nom':'seul'}", admin()));
        trace("prenom vide = absent (doublon)", creer("medecins", CENTRE_A, "{'nom':'Seul','prenom':''}", admin()));
        trace("modification vers l'identite d'un autre", modifier("medecins", s, CENTRE_A, "{'nom':'Benali','prenom':'Karim'}", admin()));
        trace("modification qui garde son identite", modifier("medecins", k, CENTRE_A, "{'nom':'Benali','prenom':'Karim','specialite':'Autre'}", admin()));
        trace("doublon d'un medecin du centre B lui-meme : refuse", creer("medecins", CENTRE_B, "{'nom':'Benali','prenom':'Karim'}", as(CENTRE_B, "ADMIN")));
        trace("identite existant dans le centre A (Benali Sofiane), creee dans le centre B : permis (cle par centre)", creer("medecins", CENTRE_B, "{'nom':'Benali','prenom':'Sofiane'}", as(CENTRE_B, "ADMIN")));
        traceSql("etat A", ETAT, CENTRE_A);
        verrouiller("unicite-nom-prenom");
    }

    @Test
    void les_validations_de_champs() throws Exception {
        trace("nom absent", creer("medecins", CENTRE_A, "{'prenom':'x'}", admin()));
        trace("nom blanc", creer("medecins", CENTRE_A, "{'nom':'  '}", admin()));
        trace("nom de 255", creer("medecins", CENTRE_A, "{'nom':'" + "n".repeat(255) + "'}", admin()));
        trace("nom de 256", creer("medecins", CENTRE_A, "{'nom':'" + "m".repeat(256) + "'}", admin()));
        trace("prenom de 256", creer("medecins", CENTRE_A, "{'nom':'P256','prenom':'" + "p".repeat(256) + "'}", admin()));
        trace("specialite de 256", creer("medecins", CENTRE_A, "{'nom':'S256','specialite':'" + "s".repeat(256) + "'}", admin()));
        trace("trois erreurs", creer("medecins", CENTRE_A, "{'nom':'','prenom':'" + "p".repeat(256) + "','specialite':'" + "s".repeat(256) + "'}", admin()));
        trace("values vide", doPost(A + "/medecins", "{'centerId':'" + CENTRE_A + "','values':{}}", admin()));
        verrouiller("validations-de-champs");
    }

    @Test
    void la_suppression_est_refusee_selon_chaque_usage() throws Exception {
        UUID mPatient = medecin(CENTRE_A, "MP", "Avec", "Patient", null);
        patient(CENTRE_A, null, null, mPatient);
        trace("medecin traitant d'un patient", supprimer("medecins", mPatient, CENTRE_A, admin()));

        UUID mPrescription = medecin(CENTRE_A, "MR", "Avec", "Prescription", null);
        UUID p = patient(CENTRE_A, null, null, null);
        prescription(CENTRE_A, p, mPrescription);
        trace("medecin d'une prescription", supprimer("medecins", mPrescription, CENTRE_A, admin()));

        UUID mDeux = medecin(CENTRE_A, "MD", "Avec", "Deux", null);
        patient(CENTRE_A, null, null, mDeux);
        prescription(CENTRE_A, p, mDeux);
        prescription(CENTRE_A, p, mDeux);
        trace("trois usages : le compte est dans le message", supprimer("medecins", mDeux, CENTRE_A, admin()));

        UUID mAutre = medecin(CENTRE_A, "MA", "Avec", "AutreCentre", null);
        patient(CENTRE_B, null, null, mAutre);
        trace("usage compte hors du centre (identifiant seul)", supprimer("medecins", mAutre, CENTRE_A, admin()));

        UUID mLibre = medecin(CENTRE_A, "ML", "Sans", "Usage", null);
        trace("medecin sans usage : desactive", supprimer("medecins", mLibre, CENTRE_A, admin()));
        traceSql("etat : seul le medecin libre est inactif", ETAT, CENTRE_A);
        verrouiller("refus-d-usage");
    }

    @Test
    void l_organisation_desactive_un_medecin_utilise_sans_controle() throws Exception {
        UUID m = medecin(CENTRE_A, "M", "Utilise", "Doc", "X");
        UUID p = patient(CENTRE_A, null, null, m);
        prescription(CENTRE_A, p, m);
        trace("administration : refus", supprimer("medecins", m, CENTRE_A, admin()));
        trace("organisation : desactivation acceptee", doDelete(ORG + "/medecins/" + m + "?centreId=" + CENTRE_A, admin()));
        traceSql("etat apres desactivation", ETAT, CENTRE_A);
        traceSql("le patient et la prescription gardent leur reference", "SELECT medecin_traitant_id FROM patients WHERE center_id = ?", CENTRE_A);
        trace("liste de l'administration : masque", lister("medecins", CENTRE_A, null, admin()));
        trace("administration : 2e suppression = introuvable", supprimer("medecins", m, CENTRE_A, admin()));
        trace("organisation : 2e desactivation", doDelete(ORG + "/medecins/" + m + "?centreId=" + CENTRE_A, admin()));
        trace("administration : modification d'un desactive = introuvable", modifier("medecins", m, CENTRE_A, "{'nom':'Utilise','prenom':'Doc'}", admin()));
        verrouiller("desactivation-d-un-medecin-utilise");
    }

    @Test
    void un_medecin_desactive_est_invisible_a_l_unicite_et_a_l_import() throws Exception {
        UUID m = medecin(CENTRE_A, "M", "Benali", "Karim", "Nephrologue");
        trace("desactivation", supprimer("medecins", m, CENTRE_A, admin()));
        trace("recreation du meme nom : acceptee (doublon d'identite)", creer("medecins", CENTRE_A, "{'nom':'Benali','prenom':'Karim','specialite':'Nouveau'}", admin()));
        traceSql("deux lignes, l'ancienne jamais reactivee", ETAT, CENTRE_A);
        trace("nouvelle tentative : refus car le nouveau est actif", creer("medecins", CENTRE_A, "{'nom':'Benali','prenom':'Karim'}", admin()));

        UUID n = medecin(CENTRE_A, "N", "Ouaret", "Mustapha", "Cardiologue");
        trace("desactivation de Ouaret", supprimer("medecins", n, CENTRE_A, admin()));
        trace("verification d'import du medecin desactive : a creer", importer("medecins", CENTRE_A, false, "nom,prenom,specialite\nOuaret,Mustapha,Neph\n", admin()));
        trace("import reel", importer("medecins", CENTRE_A, true, "nom,prenom,specialite\nOuaret,Mustapha,Neph\n", admin()));
        traceSql("etat apres reimport", ETAT, CENTRE_A);
        verrouiller("medecin-desactive-recreation-et-reimport");
    }

    @Test
    void la_lecture_cherche_et_pagine() throws Exception {
        medecin(CENTRE_A, "A", "Zeroual", "Amine", "Nephrologue");
        medecin(CENTRE_A, "B", "Benali", "Karim", "Cardiologue");
        medecin(CENTRE_A, "C", "Benali", "Abdel", null);
        medecin(CENTRE_A, "D", "Hamidi", null, "Generaliste");
        trace("tout (tri nom puis prenom)", lister("medecins", CENTRE_A, null, admin()));
        trace("recherche par nom", lister("medecins", CENTRE_A, "search=benali", admin()));
        trace("recherche par prenom", lister("medecins", CENTRE_A, "search=AMINE", admin()));
        trace("recherche par specialite", lister("medecins", CENTRE_A, "search=cardio", admin()));
        trace("recherche sans resultat", lister("medecins", CENTRE_A, "search=zzz", admin()));
        trace("page 1 de taille 2", lister("medecins", CENTRE_A, "page=1&size=2", admin()));
        verrouiller("lecture-recherche-pagination");
    }

    @Test
    void l_import_des_medecins_complement() throws Exception {
        medecin(CENTRE_A, "K", "Benali", "Karim", "Nephrologue");
        medecin(CENTRE_A, "S", "Seul", null, "Ancienne");
        trace("mise a jour de la specialite par nom et prenom", importer("medecins", CENTRE_A, true,
                "nom,prenom,specialite\nBENALI,karim,Cardiologue\n", admin()));
        trace("sans colonne prenom : le medecin sans prenom est retrouve, specialite mise a jour", importer("medecins", CENTRE_A, true,
                "nom,specialite\nSeul,Nouvelle\n", admin()));
        trace("sans colonne prenom : Benali (qui a un prenom) n'est pas retrouve, doublon de nom cree", importer("medecins", CENTRE_A, true,
                "nom,specialite\nBenali,Autre\n", admin()));
        trace("en-tetes par alias", importer("medecins", CENTRE_A, true,
                "Nom medecin;Prenom medecin;Specialite\nHamidi;Sofiane;Generaliste\n", admin()));
        trace("doublon dans le fichier", importer("medecins", CENTRE_A, true,
                "nom,prenom\nDoublon,Un\n DOUBLON ,un\n", admin()));
        trace("ligne trop longue", importer("medecins", CENTRE_A, true, "nom\n" + "x".repeat(256) + "\n", admin()));
        traceSql("etat", ETAT, CENTRE_A);
        verrouiller("import-complement");
    }

    @Test
    void le_modele_d_import_des_medecins() throws Exception {
        trace("modele csv", doGet(A + "/medecins/template?format=csv", admin()));
        verrouiller("modele-d-import");
    }

    @Test
    void les_deux_strategies_d_invalidation_du_cache_des_medecins() throws Exception {
        medecin(CENTRE_A, "A", "Benali", "Karim", null);
        medecin(CENTRE_B, "B", "Ouaret", "Mustapha", null);
        String lectureA = LECTURES + "/medecins?centerId=" + CENTRE_A;
        String lectureB = LECTURES + "/medecins?centerId=" + CENTRE_B;
        trace("mise en cache A", doGet(lectureA, admin()));
        trace("mise en cache B", doGet(lectureB, as(CENTRE_B, "ADMIN")));
        medecin(CENTRE_A, "A2", "Ajout", "DirectA", null);
        medecin(CENTRE_B, "B2", "Ajout", "DirectB", null);
        trace("creation par l'administration dans A", creer("medecins", CENTRE_A, "{'nom':'Admin','prenom':'A'}", admin()));
        trace("A relu (liste legacy : tous les actifs)", doGet(lectureA, admin()));
        trace("B relu : cache du centre B intact apres ecriture administration", doGet(lectureB, as(CENTRE_B, "ADMIN")));
        trace("creation par l'organisation dans A", doPost(ORG + "/medecins", "{'centreId':'" + CENTRE_A + "','nom':'Org','prenom':'A'}", admin()));
        trace("B relu : cache du centre B vide apres ecriture Organisation", doGet(lectureB, as(CENTRE_B, "ADMIN")));
        verrouiller("cache-des-medecins");
    }

    @Test
    void l_organisation_n_applique_pas_l_unicite_de_l_administration() throws Exception {
        trace("organisation : creation", doPost(ORG + "/medecins", "{'centreId':'" + CENTRE_A + "','nom':'Benali','prenom':'Karim'}", admin()));
        trace("organisation : meme identite", doPost(ORG + "/medecins", "{'centreId':'" + CENTRE_A + "','nom':'Benali','prenom':'Karim'}", admin()));
        trace("administration : meme identite", creer("medecins", CENTRE_A, "{'nom':'Benali','prenom':'Karim'}", admin()));
        traceSql("etat : deux lignes identiques", ETAT, CENTRE_A);
        Resp liste = lister("medecins", CENTRE_A, null, admin());
        traceNote("liste de l'administration : statut", String.valueOf(liste.status()));
        traceNote("liste de l'administration : total (l'ordre de deux homonymes n'est pas fixe)", mapper.readTree(liste.raw()).path("total").asString());
        verrouiller("unicite-organisation-et-administration");
    }

    @Test
    void les_ecritures_sont_tracees_par_le_journal_d_audit() throws Exception {
        UUID m = medecin(CENTRE_A, "M", "Benali", "Karim", null);
        UUID utilise = medecin(CENTRE_A, "U", "Utilise", "Doc", null);
        patient(CENTRE_A, null, null, utilise);
        trace("creation", creer("medecins", CENTRE_A, "{'nom':'Nouveau'}", admin()));
        trace("modification", modifier("medecins", m, CENTRE_A, "{'nom':'Benali','prenom':'Karim','specialite':'X'}", admin()));
        trace("suppression refusee", supprimer("medecins", utilise, CENTRE_A, admin()));
        trace("suppression (desactivation)", supprimer("medecins", m, CENTRE_A, admin()));
        trace("import reel", importer("medecins", CENTRE_A, true, "nom\nImporte\n", admin()));
        trace("desactivation par l'organisation", doDelete(ORG + "/medecins/" + utilise + "?centreId=" + CENTRE_A, admin()));
        traceAudit("journal d'audit");
        verrouiller("audit_nouveau_code"); // PL11-1 : identifiant créé et type de référentiel tracés (ancien : voir _ecarts-legacy)
    }
}
