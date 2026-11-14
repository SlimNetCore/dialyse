package com.hemodialyse.backend.characterization.identite.idnlecture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * IDN-lecture, comptes d'une société créés par le propriétaire : comptes direction
 * ({@code DirectionAccountService}, {@code /societes/{id}/direction-accounts}) et administrateurs de centres
 * ({@code SocieteAdminAccountService}, {@code /societes/{id}/admin-accounts}). Comportement ACTUEL : existence de la société, appartenance
 * d'un centre à la société, listes avec les noms des centres, rattachements remplacés, sessions coupées.
 */
class ComptesDeSocieteIdnLectureCharacterizationIntegrationTest extends IdnLectureSupport {

    private static final String CLE = "Cle-Fictive-Z9-2026";

    private UUID alpha, beta;
    private UUID alpha1, alpha2, betaNord, betaSud;
    private UUID proprietaire, directionAlpha, adminAlpha, adminDeuxSocietes;

    @Override
    protected String dossier() {
        return "comptes";
    }

    @BeforeEach
    void monde() {
        alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        beta = societeNommee("SOCIETE_BETA", "BETA", "Beta Clinique", true);
        alpha1 = centreNomme("CENTRE_ALPHA_1", alpha, "A1", "Alpha 1", true);
        alpha2 = centreNomme("CENTRE_ALPHA_2", alpha, "A2", "Alpha 2", false);
        betaNord = centreNomme("CENTRE_BETA_NORD", beta, "BN", "Beta Nord", true);
        betaSud = centreNomme("CENTRE_BETA_SUD", beta, "BS", "Beta Sud", true);
        proprietaire = compte("COMPTE_PROPRIETAIRE", "proprietaire", true, "SUPERADMIN");
        directionAlpha = compte("COMPTE_DIRECTION_ALPHA", "direction-alpha", true, "DIRECTION");
        rattacherALaSociete(directionAlpha, alpha);
        adminAlpha = compte("COMPTE_ADMIN_ALPHA", "admin-alpha", true, "ADMIN");
        rattacher(adminAlpha, alpha1);
        adminDeuxSocietes = compte("COMPTE_ADMIN_DEUX_SOCIETES", "admin-deux", true, "ADMIN");
        rattacher(adminDeuxSocietes, alpha2);
        rattacher(adminDeuxSocietes, alpha1);
        rattacher(adminDeuxSocietes, betaSud);
        role("DIRECTION");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor proprio() {
        return session(proprietaire, alpha1, null, "SUPERADMIN");
    }

    private String admins(UUID societe) {
        return "/api/v1/societes/" + societe + "/admin-accounts";
    }

    private String directions(UUID societe) {
        return "/api/v1/societes/" + societe + "/direction-accounts";
    }

    private void jeton(UUID compte, UUID centre, String hash) {
        jdbc.update("INSERT INTO auth_refresh_token (id, token_hash, user_id, center_id, societe_id, expires_at, revoked, created_at) "
                        + "VALUES (?, ?, ?, ?, NULL, ?, FALSE, ?)", UUID.randomUUID(), hash, compte, centre,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), OffsetDateTime.now(ZoneOffset.UTC));
    }

    @Test
    void les_administrateurs_d_une_societe_sont_listes_avec_les_noms_de_leurs_centres() throws Exception {
        trace("societe Alpha : tri par identifiant puis par nom de centre, centres de la seule societe", doGet(admins(alpha), proprio()));
        trace("societe Beta : l'administrateur n'y porte que son centre de Beta", doGet(admins(beta), proprio()));
        trace("societe inconnue", doGet(admins(UUID.nameUUIDFromBytes("inconnue".getBytes())), proprio()));
        trace("administrateur desactive : toujours liste", doPost(admins(alpha) + "/" + adminAlpha + "/desactiver", null, proprio()));
        trace("liste apres desactivation", doGet(admins(alpha), proprio()));
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "Alpha 1 renomme", alpha1);
        trace("liste apres renommage d'Alpha 1", doGet(admins(alpha), proprio()));
        jdbc.update("UPDATE centers SET societe_id = ? WHERE id = ?", beta, alpha2);
        trace("liste Alpha apres le transfert du centre Alpha 2 vers Beta", doGet(admins(alpha), proprio()));
        trace("liste Beta apres ce transfert", doGet(admins(beta), proprio()));
        verrouiller("administrateurs_listes");
    }

    @Test
    void la_creation_d_un_administrateur_exige_un_centre_de_la_societe() throws Exception {
        String corps = "{'centerId':'" + alpha1 + "','username':'" + PREFIXE_COMPTE + "cree','fullName':'Administrateur cree','email':'cree@example.test','password':'" + CLE + "'}";
        trace("creation dans un centre de la societe", doPost(admins(alpha), corps, proprio()));
        trace("identifiant deja pris", doPost(admins(alpha), corps, proprio()));
        trace("centre d'une autre societe", doPost(admins(alpha), "{'centerId':'" + betaNord + "','username':'" + PREFIXE_COMPTE + "autre','password':'" + CLE + "'}", proprio()));
        trace("centre inconnu", doPost(admins(alpha), "{'centerId':'" + UUID.nameUUIDFromBytes("inconnu".getBytes())
                + "','username':'" + PREFIXE_COMPTE + "autre','password':'" + CLE + "'}", proprio()));
        trace("centre absent", doPost(admins(alpha), "{'username':'" + PREFIXE_COMPTE + "autre','password':'" + CLE + "'}", proprio()));
        trace("societe inconnue, corps valide", doPost(admins(UUID.nameUUIDFromBytes("inconnue".getBytes())),
                "{'centerId':'" + alpha1 + "','username':'" + PREFIXE_COMPTE + "autre','password':'" + CLE + "'}", proprio()));
        trace("societe inconnue et centre d'une autre societe : la societe est controlee en premier",
                doPost(admins(UUID.nameUUIDFromBytes("inconnue".getBytes())), "{'centerId':'" + betaNord + "','username':'" + PREFIXE_COMPTE + "autre','password':'" + CLE + "'}", proprio()));
        trace("centre de la societe desactive : accepte", doPost(admins(alpha), "{'centerId':'" + alpha2 + "','username':'" + PREFIXE_COMPTE + "ferme','password':'" + CLE + "'}", proprio()));
        trace("identifiant invalide", doPost(admins(alpha), "{'centerId':'" + alpha1 + "','username':'AB','password':'" + CLE + "'}", proprio()));
        trace("mot de passe faible", doPost(admins(alpha), "{'centerId':'" + alpha1 + "','username':'" + PREFIXE_COMPTE + "faible','password':'court1'}", proprio()));
        trace("adresse invalide", doPost(admins(alpha), "{'centerId':'" + alpha1 + "','username':'" + PREFIXE_COMPTE + "mail','email':'pas-un-mail','password':'" + CLE + "'}", proprio()));
        for (String role : new String[]{"ADMIN", "DIRECTION", "MEDECIN"}) {
            // CONSTAT : la validation du corps (@Valid) precede le controle du role par @PreAuthorize : 400 et non 403 (voir rapport).
            trace("role " + role + ", corps invalide", doPost(admins(alpha), "{'username':'AB'}", session(proprietaire, alpha1, null, role)));
            trace("role " + role + ", corps valide", doPost(admins(alpha), corps.replace("cree", "refuse"), session(proprietaire, alpha1, null, role)));
        }
        trace("sans session", doPost(admins(alpha), corps, null));
        traceSql("comptes crees et leurs centres", "SELECT u.username, u.active, uc.center_id FROM app_user u LEFT JOIN app_user_center uc ON uc.user_id = u.id "
                + "WHERE u.username IN (?, ?) ORDER BY u.username", PREFIXE_COMPTE + "cree", PREFIXE_COMPTE + "ferme");
        verrouiller("administrateurs_creation");
    }

    @Test
    void la_modification_d_un_administrateur_remplace_ses_centres_de_la_societe_seulement() throws Exception {
        jeton(adminDeuxSocietes, alpha1, "idn-jeton-alpha1");
        jeton(adminDeuxSocietes, alpha2, "idn-jeton-alpha2");
        jeton(adminDeuxSocietes, betaSud, "idn-jeton-beta");
        String cheminDeux = admins(alpha) + "/" + adminDeuxSocietes;
        trace("retrait d'Alpha 2 : le centre de Beta n'est pas touche", doPut(cheminDeux,
                "{'fullName':'Admin deux','email':'deux@example.test','centerIds':['" + alpha1 + "']}", proprio()));
        traceSql("rattachements apres le retrait", "SELECT center_id FROM app_user_center WHERE user_id = ? ORDER BY center_id", adminDeuxSocietes);
        traceSql("jetons : seul celui d'Alpha 2 est coupe", "SELECT token_hash, revoked FROM auth_refresh_token WHERE user_id = ? ORDER BY token_hash", adminDeuxSocietes);
        trace("ajout d'un centre de la societe", doPut(cheminDeux, "{'fullName':'','email':'','centerIds':['" + alpha1 + "','" + alpha2 + "']}", proprio()));
        trace("centre d'une autre societe", doPut(cheminDeux, "{'fullName':'Admin deux','centerIds':['" + betaNord + "']}", proprio()));
        trace("liste de centres vide", doPut(cheminDeux, "{'fullName':'Admin deux','centerIds':[]}", proprio()));
        trace("adresse invalide", doPut(cheminDeux, "{'fullName':'Admin deux','email':'pas-un-mail','centerIds':['" + alpha1 + "']}", proprio()));
        trace("administrateur d'une autre societe", doPut(admins(beta) + "/" + adminAlpha, "{'fullName':'x','centerIds':['" + betaNord + "']}", proprio()));
        trace("compte direction sur la route des administrateurs", doPut(admins(alpha) + "/" + directionAlpha, "{'fullName':'x','centerIds':['" + alpha1 + "']}", proprio()));
        trace("compte inconnu", doPut(admins(alpha) + "/" + UUID.nameUUIDFromBytes("inconnu".getBytes()), "{'fullName':'x','centerIds':['" + alpha1 + "']}", proprio()));
        trace("l'administrateur ne se modifie pas lui-meme (403)", doPut(cheminDeux, "{'fullName':'x','centerIds':['" + alpha1 + "']}", session(adminDeuxSocietes, alpha1, null, "ADMIN")));
        verrouiller("administrateurs_modification");
    }

    @Test
    void l_activation_et_le_mot_de_passe_d_un_administrateur_coupent_ses_sessions() throws Exception {
        jeton(adminAlpha, alpha1, "idn-jeton-a");
        trace("activation d'un compte deja actif", doPost(admins(alpha) + "/" + adminAlpha + "/activer", null, proprio()));
        traceSql("jetons apres l'activation", "SELECT token_hash, revoked FROM auth_refresh_token WHERE user_id = ?", adminAlpha);
        trace("mot de passe faible", doPut(admins(alpha) + "/" + adminAlpha + "/password", "{'password':'court1'}", proprio()));
        trace("mot de passe valide", doPut(admins(alpha) + "/" + adminAlpha + "/password", "{'password':'" + CLE + "'}", proprio()));
        traceSql("jetons apres le mot de passe", "SELECT token_hash, revoked FROM auth_refresh_token WHERE user_id = ?", adminAlpha);
        trace("la connexion avec le nouveau mot de passe", connecter(alpha, alpha1, PREFIXE_COMPTE + "admin-alpha", CLE));
        trace("administrateur d'une autre societe", doPost(admins(beta) + "/" + adminAlpha + "/desactiver", null, proprio()));
        trace("desactivation", doPost(admins(alpha) + "/" + adminAlpha + "/desactiver", null, proprio()));
        traceSql("etat apres la desactivation", "SELECT active FROM app_user WHERE id = ?", adminAlpha);
        trace("compte direction sur la route des administrateurs", doPost(admins(alpha) + "/" + directionAlpha + "/desactiver", null, proprio()));
        verrouiller("administrateurs_activation");
    }

    @Test
    void les_comptes_direction_d_une_societe() throws Exception {
        String corps = "{'username':'" + PREFIXE_COMPTE + "direction-cree','fullName':'Direction creee','email':'direction@example.test','password':'" + CLE + "'}";
        trace("liste de la societe Alpha", doGet(directions(alpha), proprio()));
        trace("liste de la societe Beta (aucun compte)", doGet(directions(beta), proprio()));
        trace("liste d'une societe inconnue", doGet(directions(UUID.nameUUIDFromBytes("inconnue".getBytes())), proprio()));
        trace("creation", doPost(directions(beta), corps, proprio()));
        trace("identifiant deja pris", doPost(directions(beta), corps, proprio()));
        trace("societe inconnue, corps valide", doPost(directions(UUID.nameUUIDFromBytes("inconnue".getBytes())), corps, proprio()));
        trace("identifiant invalide", doPost(directions(beta), "{'username':'AB','password':'" + CLE + "'}", proprio()));
        trace("mot de passe faible", doPost(directions(beta), "{'username':'" + PREFIXE_COMPTE + "faible','password':'court1'}", proprio()));
        trace("adresse invalide", doPost(directions(beta), "{'username':'" + PREFIXE_COMPTE + "mail','email':'pas-un-mail','password':'" + CLE + "'}", proprio()));
        for (String role : new String[]{"ADMIN", "DIRECTION"}) {
            trace("role " + role + ", corps invalide", doPost(directions(beta), "{'username':'AB'}", session(proprietaire, alpha1, null, role)));
            trace("role " + role + ", corps valide", doPost(directions(beta), corps.replace("direction-cree", "direction-refuse"), session(proprietaire, alpha1, null, role)));
        }
        jdbc.update("INSERT INTO auth_refresh_token (id, token_hash, user_id, center_id, societe_id, expires_at, revoked, created_at) "
                        + "VALUES (?, ?, ?, NULL, ?, ?, FALSE, ?)", UUID.randomUUID(), "idn-jeton-direction", directionAlpha, alpha,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), OffsetDateTime.now(ZoneOffset.UTC));
        trace("compte direction d'une autre societe", doPost(directions(beta) + "/" + directionAlpha + "/desactiver", null, proprio()));
        trace("desactivation", doPost(directions(alpha) + "/" + directionAlpha + "/desactiver", null, proprio()));
        traceSql("etat et jetons apres la desactivation", "SELECT u.active, t.token_hash, t.revoked FROM app_user u JOIN auth_refresh_token t ON t.user_id = u.id WHERE u.id = ?", directionAlpha);
        trace("reactivation", doPost(directions(alpha) + "/" + directionAlpha + "/activer", null, proprio()));
        trace("mot de passe faible", doPut(directions(alpha) + "/" + directionAlpha + "/password", "{'password':'court1'}", proprio()));
        trace("mot de passe valide", doPut(directions(alpha) + "/" + directionAlpha + "/password", "{'password':'" + CLE + "'}", proprio()));
        trace("administrateur de centre sur la route des directions", doPost(directions(alpha) + "/" + adminAlpha + "/desactiver", null, proprio()));
        trace("liste finale de la societe Beta", doGet(directions(beta), proprio()));
        verrouiller("direction_comptes");
    }
}
