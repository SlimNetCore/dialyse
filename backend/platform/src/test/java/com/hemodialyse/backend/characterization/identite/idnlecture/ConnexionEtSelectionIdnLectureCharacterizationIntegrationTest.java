package com.hemodialyse.backend.characterization.identite.idnlecture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * IDN-lecture, connexion et sélection société/centre ({@code AuthService}) : annuaires publics de connexion, connexion à un centre,
 * reconstitution de la session (rafraîchissement, {@code /auth/me}), connexion sans centre (direction, propriétaire) et centres
 * accessibles à l'utilisateur connecté. Comportement ACTUEL, lu sur les tables {@code centers} et {@code societes}.
 */
class ConnexionEtSelectionIdnLectureCharacterizationIntegrationTest extends IdnLectureSupport {

    private static final String AUTH = "/api/v1/auth";

    private UUID alpha, beta, dormante, vide, inactive;
    private UUID alpha2, alpha1, alphaFerme, betaNord, betaAlpha, dormanteFerme, inactiveCentre;
    private UUID admin, inactif, sansDroit, direction, proprietaire;

    @Override
    protected String dossier() {
        return "connexion";
    }

    @BeforeEach
    void monde() {
        beta = societeNommee("SOCIETE_BETA", "BETA", "Beta Clinique", true);
        alpha = societeNommee("SOCIETE_ALPHA", "ALPHA", "Alpha Sante", true);
        dormante = societeNommee("SOCIETE_DORMANTE", "DORM", "Delta Dormante", true);
        vide = societeNommee("SOCIETE_VIDE", "VIDE", "Vide Sans Centre", true);
        inactive = societeNommee("SOCIETE_INACTIVE", "INAC", "Zeta Inactive", false);
        betaNord = centreNomme("CENTRE_BETA_NORD", beta, "BN", "Beta Nord", true);
        betaAlpha = centreNomme("CENTRE_BETA_ALPHA", beta, "BA", "Beta Alpha", true);
        alpha2 = centreNomme("CENTRE_ALPHA_2", alpha, "A2", "Alpha 2", true);
        alpha1 = centreNomme("CENTRE_ALPHA_1", alpha, "A1", "Alpha 1", true);
        alphaFerme = centreNomme("CENTRE_ALPHA_FERME", alpha, "AF", "Alpha 0 ferme", false);
        dormanteFerme = centreNomme("CENTRE_DORMANTE_FERME", dormante, "DF", "Dormante fermee", false);
        inactiveCentre = centreNomme("CENTRE_SOCIETE_INACTIVE", inactive, "IC", "Zeta centre", true);

        admin = compte("COMPTE_ADMIN", "admin", true, "ADMIN");
        for (UUID c : new UUID[]{alpha1, alpha2, alphaFerme, betaNord, dormanteFerme, inactiveCentre}) rattacher(admin, c);
        inactif = compte("COMPTE_INACTIF", "inactif", false, "ADMIN");
        rattacher(inactif, alpha1);
        sansDroit = compte("COMPTE_SANS_DROIT", "sans-droit", true, "ADMIN");
        direction = compte("COMPTE_DIRECTION", "direction", true, "DIRECTION");
        rattacherALaSociete(direction, alpha);
        proprietaire = compte("COMPTE_PROPRIETAIRE", "proprietaire", true, "SUPERADMIN");
    }

    @Test
    void les_societes_et_les_centres_proposes_a_la_connexion() throws Exception {
        traceFiltre("societes proposees (actives, avec au moins un centre actif, par raison sociale)",
                doGet(AUTH + "/societes", null), identifiantsDeLaFixture());
        trace("centres de la societe Alpha (actifs, par nom)", doGet(AUTH + "/societes/" + alpha + "/centres", null));
        trace("centres de la societe Beta", doGet(AUTH + "/societes/" + beta + "/centres", null));
        trace("societe dormante : seul un centre inactif", doGet(AUTH + "/societes/" + dormante + "/centres", null));
        trace("societe sans centre", doGet(AUTH + "/societes/" + vide + "/centres", null));
        trace("societe inactive : son centre actif n'est pas propose", doGet(AUTH + "/societes/" + inactive + "/centres", null));
        trace("societe inconnue", doGet(AUTH + "/societes/" + UUID.nameUUIDFromBytes("inconnue".getBytes()) + "/centres", null));
        trace("identifiant de societe illisible", doGet(AUTH + "/societes/pas-un-identifiant/centres", null));

        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", alpha2);
        trace("Alpha apres desactivation du centre Alpha 2", doGet(AUTH + "/societes/" + alpha + "/centres", null));
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", alpha1);
        traceFiltre("societes proposees : Alpha n'a plus aucun centre actif", doGet(AUTH + "/societes", null), identifiantsDeLaFixture());
        jdbc.update("UPDATE centers SET actif = TRUE WHERE id IN (?, ?)", alpha1, alpha2);
        jdbc.update("UPDATE societes SET actif = FALSE WHERE id = ?", alpha);
        traceFiltre("societes proposees : Alpha desactivee", doGet(AUTH + "/societes", null), identifiantsDeLaFixture());
        trace("centres d'une societe desactivee", doGet(AUTH + "/societes/" + alpha + "/centres", null));
        jdbc.update("UPDATE societes SET actif = TRUE WHERE id = ?", inactive);
        traceFiltre("societes proposees : la societe reactivee reapparait", doGet(AUTH + "/societes", null), identifiantsDeLaFixture());
        verrouiller("annuaires_publics_de_connexion");
    }

    @Test
    void la_connexion_a_un_centre_et_ses_refus() throws Exception {
        trace("centre et societe concordants", connecter(alpha, alpha1, PREFIXE_COMPTE + "admin", MDP));
        trace("centre seul, sans societe", connecter(null, alpha2, PREFIXE_COMPTE + "admin", MDP));
        trace("centre d'une autre societe que celle choisie", connecter(beta, alpha1, PREFIXE_COMPTE + "admin", MDP));
        trace("centre desactive (le compte y est rattache)", connecter(alpha, alphaFerme, PREFIXE_COMPTE + "admin", MDP));
        trace("societe desactivee (le compte est rattache a son centre)", connecter(inactive, inactiveCentre, PREFIXE_COMPTE + "admin", MDP));
        trace("centre non rattache au compte", connecter(alpha, betaAlpha, PREFIXE_COMPTE + "admin", MDP));
        trace("centre inconnu", connecter(null, UUID.nameUUIDFromBytes("centre-inconnu".getBytes()), PREFIXE_COMPTE + "admin", MDP));
        trace("centre d'une societe dormante, centre desactive", connecter(dormante, dormanteFerme, PREFIXE_COMPTE + "admin", MDP));
        trace("mot de passe errone", connecter(alpha, alpha1, PREFIXE_COMPTE + "admin", "Mauvais-mdp-Idn-2026"));
        trace("compte inconnu", connecter(alpha, alpha1, PREFIXE_COMPTE + "inconnu", MDP));
        trace("compte desactive, bon mot de passe", connecter(alpha, alpha1, PREFIXE_COMPTE + "inactif", MDP));
        trace("compte desactive, mauvais mot de passe", connecter(alpha, alpha1, PREFIXE_COMPTE + "inactif", "Mauvais-mdp-Idn-2026"));
        trace("compte sans aucun centre", connecter(alpha, alpha1, PREFIXE_COMPTE + "sans-droit", MDP));
        trace("le nom du centre est lu a la connexion", renommerPuisConnecter());
        traceSql("jetons de rafraichissement poses par les connexions reussies (compte admin)",
                "SELECT revoked, COUNT(*) AS nombre FROM auth_refresh_token WHERE user_id = ? GROUP BY revoked", admin);
        verrouiller("connexion_a_un_centre");
    }

    private Connexion renommerPuisConnecter() throws Exception {
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "Alpha 1 renomme", alpha1);
        return connecter(alpha, alpha1, PREFIXE_COMPTE + "admin", MDP);
    }

    @Test
    void la_session_de_centre_se_reconstitue_au_rafraichissement_et_sur_me() throws Exception {
        Connexion c = connecter(alpha, alpha1, PREFIXE_COMPTE + "admin", MDP);
        trace("connexion", c);
        trace("/me d'une session de centre", doGet(AUTH + "/me", session(admin, alpha1, null, "ADMIN")));
        trace("rafraichissement", rafraichir(c.rafraichissement()));
        trace("rafraichissement avec le jeton deja consomme", rafraichir(c.rafraichissement()));
        jdbc.update("UPDATE centers SET name = ? WHERE id = ?", "Alpha 1 renomme", alpha1);
        trace("/me : le nom du centre est relu", doGet(AUTH + "/me", session(admin, alpha1, null, "ADMIN")));
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", alpha1);
        trace("/me : centre desactive depuis", doGet(AUTH + "/me", session(admin, alpha1, null, "ADMIN")));
        jdbc.update("UPDATE centers SET actif = TRUE WHERE id = ?", alpha1);
        jdbc.update("UPDATE societes SET actif = FALSE WHERE id = ?", alpha);
        trace("/me : societe desactivee depuis", doGet(AUTH + "/me", session(admin, alpha1, null, "ADMIN")));
        jdbc.update("UPDATE societes SET actif = TRUE WHERE id = ?", alpha);
        trace("/me : centre non rattache", doGet(AUTH + "/me", session(admin, betaAlpha, null, "ADMIN")));
        trace("/me : compte inconnu", doGet(AUTH + "/me", session(UUID.nameUUIDFromBytes("compte-inconnu".getBytes()), alpha1, null, "ADMIN")));
        trace("/me : sans session", doGet(AUTH + "/me", null));
        Connexion d = connecter(alpha, alpha1, PREFIXE_COMPTE + "admin", MDP);
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", alpha1);
        trace("rafraichissement apres desactivation du centre", rafraichir(d.rafraichissement()));
        jdbc.update("UPDATE centers SET actif = TRUE WHERE id = ?", alpha1);
        verrouiller("session_de_centre");
    }

    @Test
    void la_connexion_sans_centre_de_la_direction_et_du_proprietaire() throws Exception {
        Connexion d = connecter(alpha, null, PREFIXE_COMPTE + "direction", MDP);
        trace("direction de sa societe", d);
        trace("direction : societe inconnue", connecter(UUID.nameUUIDFromBytes("societe-inconnue".getBytes()), null, PREFIXE_COMPTE + "direction", MDP));
        trace("direction : societe a laquelle le compte n'est pas rattache", connecter(beta, null, PREFIXE_COMPTE + "direction", MDP));
        trace("direction sans societe (reserve au proprietaire)", connecter(null, null, PREFIXE_COMPTE + "direction", MDP));
        trace("proprietaire sans societe", connecter(null, null, PREFIXE_COMPTE + "proprietaire", MDP));
        trace("proprietaire avec une societe (il n'a pas le role direction)", connecter(alpha, null, PREFIXE_COMPTE + "proprietaire", MDP));
        trace("administrateur de centre sans centre ni societe", connecter(null, null, PREFIXE_COMPTE + "admin", MDP));
        trace("mot de passe errone", connecter(alpha, null, PREFIXE_COMPTE + "direction", "Mauvais-mdp-Idn-2026"));
        trace("/me de la direction", doGet(AUTH + "/me", session(direction, null, alpha, "DIRECTION")));
        trace("rafraichissement de la direction", rafraichir(d.rafraichissement()));
        trace("/me du proprietaire", doGet(AUTH + "/me", session(proprietaire, null, null, "SUPERADMIN")));
        jdbc.update("UPDATE societes SET raison_sociale = ? WHERE id = ?", "Alpha Sante renommee", alpha);
        trace("/me de la direction : la raison sociale est relue", doGet(AUTH + "/me", session(direction, null, alpha, "DIRECTION")));
        jdbc.update("UPDATE societes SET actif = FALSE WHERE id = ?", alpha);
        trace("direction : societe desactivee", connecter(alpha, null, PREFIXE_COMPTE + "direction", MDP));
        trace("/me de la direction : societe desactivee", doGet(AUTH + "/me", session(direction, null, alpha, "DIRECTION")));
        jdbc.update("UPDATE societes SET actif = TRUE WHERE id = ?", alpha);
        jdbc.update("UPDATE app_user SET active = FALSE WHERE id = ?", direction);
        trace("direction : compte desactive, bon mot de passe", connecter(alpha, null, PREFIXE_COMPTE + "direction", MDP));
        trace("direction : compte desactive, mauvais mot de passe", connecter(alpha, null, PREFIXE_COMPTE + "direction", "Mauvais-mdp-Idn-2026"));
        verrouiller("connexion_sans_centre");
    }

    @Test
    void les_centres_accessibles_a_l_utilisateur_connecte() throws Exception {
        UUID autreAdmin = compte("COMPTE_ADMIN_BETA", "admin-beta", true, "ADMIN");
        rattacher(autreAdmin, betaNord);
        traceFiltre("proprietaire : tous les centres, par nom, actifs ou non",
                doGet(AUTH + "/centres", session(proprietaire, alpha1, null, "SUPERADMIN")), identifiantsDeLaFixture());
        traceFiltre("administrateur d'Alpha : les centres actifs de sa societe, par nom",
                doGet(AUTH + "/centres", session(admin, alpha1, null, "ADMIN")), identifiantsDeLaFixture());
        traceFiltre("administrateur de Beta", doGet(AUTH + "/centres", session(autreAdmin, betaNord, null, "ADMIN")), identifiantsDeLaFixture());
        traceFiltre("centre courant desactive : il disparait de sa propre liste",
                doGet(AUTH + "/centres", session(admin, alphaFerme, null, "ADMIN")), identifiantsDeLaFixture());
        traceFiltre("centre courant inconnu", doGet(AUTH + "/centres",
                session(admin, UUID.nameUUIDFromBytes("centre-courant-inconnu".getBytes()), null, "ADMIN")), identifiantsDeLaFixture());
        jdbc.update("UPDATE centers SET actif = FALSE WHERE id = ?", alpha2);
        traceFiltre("apres desactivation d'Alpha 2", doGet(AUTH + "/centres", session(admin, alpha1, null, "ADMIN")), identifiantsDeLaFixture());
        trace("session sans centre (direction)", doGet(AUTH + "/centres", session(direction, null, alpha, "DIRECTION")));
        trace("sans session", doGet(AUTH + "/centres", null));
        verrouiller("centres_accessibles");
    }
}
