package com.hemodialyse.backend.characterization.identite.idnlecture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * IDN-lecture, administration des utilisateurs ({@code UserRestController}, {@code /api/v1/users}) : listes avec les noms des centres
 * rattachés, fiche d'un utilisateur, remplacement des rattachements, création, suppression. Comportement ACTUEL ; les listes sont
 * filtrées sur les comptes de ce filet (la base de test est partagée).
 */
class UtilisateursEtRattachementsIdnLectureCharacterizationIntegrationTest extends IdnLectureSupport {

    private static final String U = "/api/v1/users";

    private UUID alpha, beta;
    private UUID alpha1, alpha2, alphaFerme, betaNord;
    private UUID administrateur, marie, paul, isole;

    @Override
    protected String dossier() {
        return "utilisateurs";
    }

    @BeforeEach
    void monde() {
        alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        beta = societeNommee("SOCIETE_BETA", "BETA", "Beta Clinique", true);
        alpha1 = centreNomme("CENTRE_ALPHA_1", alpha, "A1", "Alpha 1", true);
        alpha2 = centreNomme("CENTRE_ALPHA_2", alpha, "A2", "Alpha 2", true);
        alphaFerme = centreNomme("CENTRE_ALPHA_FERME", alpha, "AF", "Alpha ferme", false);
        betaNord = centreNomme("CENTRE_BETA_NORD", beta, "BN", "Beta Nord", true);
        administrateur = compte("COMPTE_ADMIN", "admin", true, "ADMIN");
        rattacher(administrateur, alpha1);
        marie = compte("COMPTE_MARIE", "marie", true, "INFIRMIER");
        rattacher(marie, alpha1);
        rattacher(marie, alpha2);
        rattacher(marie, alphaFerme);
        paul = compte("COMPTE_PAUL", "paul", false, "MEDECIN", "INFIRMIER");
        rattacher(paul, betaNord);
        isole = compte("COMPTE_ISOLE", "isole", true);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return session(administrateur, alpha1, null, "ADMIN");
    }

    @Test
    void les_listes_d_utilisateurs_portent_les_noms_des_centres_rattaches() throws Exception {
        traceFiltre("tous les utilisateurs, par identifiant", doGet(U, admin()), comptesDeLaFixture());
        traceFiltre("filtre sur le centre Alpha 1", doGet(U + "?centerId=" + alpha1, admin()), comptesDeLaFixture());
        traceFiltre("filtre sur le centre Beta Nord", doGet(U + "?centerId=" + betaNord, admin()), comptesDeLaFixture());
        traceFiltre("filtre sur un centre inconnu", doGet(U + "?centerId=" + UUID.nameUUIDFromBytes("inconnu".getBytes()), admin()),
                comptesDeLaFixture());
        traceFiltre("recherche : centre Alpha 2, page 0", doPost(U + "/search",
                "{'centerId':'" + alpha2 + "','page':0,'size':50}", admin()), comptesDeLaFixture());
        traceFiltre("recherche : texte libre sur les identifiants du filet", doPost(U + "/search",
                "{'page':0,'size':50,'search':'idn-'}", admin()), comptesDeLaFixture());
        traceFiltre("recherche : nom de centre contenant ferme", doPost(U + "/search",
                "{'page':0,'size':50,'username':'idn-','centers':'ferme'}", admin()), comptesDeLaFixture());
        traceFiltre("recherche : nom de centre contenant beta, comptes inactifs", doPost(U + "/search",
                "{'page':0,'size':50,'username':'idn-','centers':'beta','active':false}", admin()), comptesDeLaFixture());
        traceFiltre("recherche : role infirmier", doPost(U + "/search",
                "{'page':0,'size':50,'username':'idn-','roles':'infirmier'}", admin()), comptesDeLaFixture());
        trace("recherche : taille hors bornes", doPost(U + "/search", "{'page':0,'size':500}", admin()));
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "Alpha 1 renomme", alpha1);
        traceFiltre("apres renommage du centre Alpha 1 : les listes relisent le nom",
                doGet(U + "?centerId=" + alpha1, admin()), comptesDeLaFixture());
        verrouiller("listes");
    }

    @Test
    void la_fiche_d_un_utilisateur_et_les_acces() throws Exception {
        trace("fiche de marie : roles et centres (y compris le centre desactive)", doGet(U + "/" + marie, admin()));
        trace("fiche de paul : deux roles, un centre d'une autre societe", doGet(U + "/" + paul, admin()));
        trace("fiche d'un compte sans centre ni role", doGet(U + "/" + isole, admin()));
        trace("fiche d'un utilisateur inconnu", doGet(U + "/" + UUID.nameUUIDFromBytes("inconnu".getBytes()), admin()));
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "Alpha 2 renomme", alpha2);
        trace("fiche de marie apres renommage d'Alpha 2", doGet(U + "/" + marie, admin()));
        trace("sans session", doGet(U + "/" + marie, null));
        trace("role COMPTABLE", doGet(U + "/" + marie, session(administrateur, alpha1, null, "COMPTABLE")));
        trace("role DIRECTION", doGet(U, session(administrateur, null, alpha, "DIRECTION")));
        trace("role SUPERADMIN seul (la route est reservee a ADMIN)", doGet(U, session(administrateur, alpha1, null, "SUPERADMIN")));
        verrouiller("fiche");
    }

    @Test
    void les_rattachements_se_remplacent_a_la_modification_et_a_la_creation() throws Exception {
        UUID infirmier = jdbc.queryForObject("SELECT id FROM app_role WHERE code = 'INFIRMIER'", UUID.class);
        Resp cree = doPost(U, "{'username':'" + PREFIXE_COMPTE + "nouveau','password':'Cle-Fictive-Z9-2026','email':'nouveau@example.test',"
                + "'fullName':'Nouveau compte','active':true,'roleIds':['" + infirmier + "'],'centerIds':['" + alpha1 + "','" + betaNord + "']}", admin());
        trace("creation avec deux centres (dont un d'une autre societe)", cree);
        UUID nouveau = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", UUID.class, PREFIXE_COMPTE + "nouveau");
        aliasNommes.put(nouveau.toString(), "<COMPTE_NOUVEAU>");
        trace("fiche du nouveau compte", doGet(U + "/" + nouveau, admin()));
        trace("creation d'un identifiant deja pris", doPost(U, "{'username':'" + PREFIXE_COMPTE + "nouveau','password':'Cle-Fictive-Z9-2026',"
                + "'active':true,'centerIds':['" + alpha1 + "']}", admin()));
        trace("modification : les centres sont remplaces", doPut(U + "/" + nouveau, "{'email':'nouveau@example.test','fullName':'Nouveau compte',"
                + "'active':true,'centerIds':['" + alpha2 + "','" + alphaFerme + "']}", admin()));
        trace("fiche apres remplacement", doGet(U + "/" + nouveau, admin()));
        trace("modification sans liste de centres : rattachements conserves", doPut(U + "/" + nouveau,
                "{'email':'nouveau@example.test','fullName':'Nouveau compte renomme','active':true}", admin()));
        trace("fiche apres modification sans liste", doGet(U + "/" + nouveau, admin()));
        trace("modification : liste vide de centres", doPut(U + "/" + nouveau,
                "{'email':'nouveau@example.test','fullName':'Nouveau compte','active':true,'centerIds':[]}", admin()));
        trace("fiche apres liste vide", doGet(U + "/" + nouveau, admin()));
        trace("rattachement a un centre inconnu", doPut(U + "/" + nouveau,
                "{'email':'nouveau@example.test','fullName':'Nouveau compte','active':true,'centerIds':['"
                        + UUID.nameUUIDFromBytes("centre-inconnu".getBytes()) + "']}", admin()));
        traceSql("rattachements en base apres le refus", "SELECT center_id FROM app_user_center WHERE user_id = ?", nouveau);
        // La suppression d'un compte portant des roles repose sur les ON DELETE CASCADE de PostgreSQL, absents du schema H2 de test : seul
        // un compte sans role ni centre est caracterise ici.
        trace("suppression d'un compte sans role ni centre", doDelete(U + "/" + isole, admin()));
        trace("fiche apres suppression", doGet(U + "/" + isole, admin()));
        verrouiller("rattachements");
    }
}
