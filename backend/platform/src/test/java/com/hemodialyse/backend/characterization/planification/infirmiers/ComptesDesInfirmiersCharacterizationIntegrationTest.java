package com.hemodialyse.backend.characterization.planification.infirmiers;

import com.hemodialyse.backend.characterization.planification.InfirmiersCharacterizationSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Filet de caractérisation P2 : comptes utilisateurs liés aux fiches (RG-INF-003, 010 à 012) :
 * {@code GET /comptes-liables}, {@code POST /{id}/compte/lier|creer}, {@code DELETE /{id}/compte}, synchronisation
 * fiche vers compte (activation) et compte vers fiche ({@code PUT|DELETE /api/v1/users/{id}} de l'administration).
 * Points d'observation : HTTP et état SQL de {@code infirmier}, {@code app_user}, {@code app_user_role}, {@code app_user_center}.
 */
class ComptesDesInfirmiersCharacterizationIntegrationTest extends InfirmiersCharacterizationSupport {

    @Override
    protected String theme() {
        return "p2-infirmiers/comptes";
    }

    private static String lier(UUID userId) {
        return json("{'userId':" + (userId == null ? "null" : "'" + userId + "'") + "}");
    }

    private static String creer(String identifiant, String email) {
        return json("{'identifiant':" + (identifiant == null ? "null" : "'" + identifiant + "'") + ",'email':"
                + (email == null ? "null" : "'" + email + "'") + "}");
    }

    private static String miseAJourCompte(boolean actif) {
        return json("{'email':null,'fullName':'Nom modifie par l administration','active':" + actif + "}");
    }

    // ------------------------------------------------------------------ comptes liables

    @Test
    void les_comptes_liables_sont_les_comptes_infirmier_du_centre_non_relies_tries_par_identifiant() throws Exception {
        compte("C_ZETA", "zeta", "Zeta", "INFIRMIER", true, CENTRE_A);
        compte("C_ALPHA_INACTIF", "alpha", "Alpha", "INFIRMIER", false, CENTRE_A);
        compte("C_MEDECIN", "medecin", "Medecin", "MEDECIN", true, CENTRE_A);
        compte("C_SANS_ROLE", "sansrole", "Sans role", null, true, CENTRE_A);
        compte("C_AUTRE_CENTRE", "autrecentre", "Autre centre", "INFIRMIER", true, CENTRE_B);
        compte("C_DEUX_CENTRES", "deuxcentres", "Deux centres", "INFIRMIER", true, CENTRE_A, CENTRE_B);
        UUID lie = compte("C_LIE", "lie", "Deja lie", "INFIRMIER", true, CENTRE_A);
        UUID lieAilleurs = compte("C_LIE_AILLEURS", "lieailleurs", "Lie dans l autre centre", "INFIRMIER", true, CENTRE_A, CENTRE_B);
        UUID mixte = compte("C_MIXTE", "mixte", "Deux roles", "INFIRMIER", true, CENTRE_A);
        jdbc.update("INSERT INTO app_user_role (user_id, role_id) SELECT ?, id FROM app_role WHERE code = 'MEDECIN'", mixte);
        fiche(CENTRE_A, "INF_LIEE", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, lie);
        fiche(CENTRE_B, "INF_B", "M2", "Zerrouki", "Rania", null, "INFIRMIER", false, true, lieAilleurs);
        golden("comptes_liables_centre_A_200", doGet(INFIRMIERS + "/comptes-liables", adminA()));
        golden("comptes_liables_centre_B_200", doGet(INFIRMIERS + "/comptes-liables", adminB()));
        golden("comptes_liables_pagination_page_1_taille_2_200", doGet(INFIRMIERS + "/comptes-liables?page=1&size=2", adminA()));
        goldenSimple("comptes_liables_taille_negative", doGet(INFIRMIERS + "/comptes-liables?size=-1", adminA()));
        goldenSimple("comptes_liables_centre_explicite_autre_centre",
                doGet(INFIRMIERS + "/comptes-liables?centerId=" + CENTRE_B, adminA()));
    }

    @Test
    void sans_aucun_compte_liable_la_liste_est_vide() throws Exception {
        golden("comptes_liables_vide_200", doGet(INFIRMIERS + "/comptes-liables", adminA()));
    }

    // ------------------------------------------------------------------ liaison d'un compte existant

    @Test
    void lier_un_compte_infirmier_actif_a_une_fiche_active_relie_les_deux_sans_modifier_le_compte() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", true, CENTRE_A);
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        goldenSimple("liaison_nominale_200", doPost(INFIRMIERS + "/" + fiche + "/compte/lier", lier(compte), adminA()));
        etatInfirmiers("liaison_etat_fiche");
        etatComptes("liaison_etat_compte_inchange");
        golden("liaison_liste_porte_le_compte_200", doGet(INFIRMIERS, adminA()));
        golden("liaison_le_compte_n_est_plus_liable_200", doGet(INFIRMIERS + "/comptes-liables", adminA()));
    }

    @Test
    void lier_un_compte_et_une_fiche_tous_deux_inactifs_est_accepte() throws Exception {
        UUID compte = compte("C_1", "inactif", "Compte inactif", "INFIRMIER", false, CENTRE_A);
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", false);
        goldenSimple("liaison_deux_inactifs_200", doPost(INFIRMIERS + "/" + fiche + "/compte/lier", lier(compte), adminA()));
        etatInfirmiers("liaison_deux_inactifs_etat_base");
    }

    @Test
    void la_liaison_refuse_un_etat_incoherent_un_compte_inconnu_deja_utilise_ou_une_fiche_deja_liee_sans_rien_ecrire() throws Exception {
        UUID compteInactif = compte("C_INACTIF", "inactif", "Compte inactif", "INFIRMIER", false, CENTRE_A);
        UUID compteActif = compte("C_ACTIF", "actif", "Compte actif", "INFIRMIER", true, CENTRE_A);
        UUID compteMedecin = compte("C_MEDECIN", "medecin", "Medecin", "MEDECIN", true, CENTRE_A);
        UUID compteAutreCentre = compte("C_AUTRE", "autre", "Autre centre", "INFIRMIER", true, CENTRE_B);
        UUID compteUtilise = compte("C_UTILISE", "utilise", "Deja utilise", "INFIRMIER", true, CENTRE_A);
        UUID active = fiche(CENTRE_A, "INF_ACTIVE", "M1", "Amrani", "Sara", true);
        UUID inactive = fiche(CENTRE_A, "INF_INACTIVE", "M2", "Benali", "Karim", false);
        UUID liee = fiche(CENTRE_A, "INF_LIEE", "M3", "Chikhi", "Nadia", null, "INFIRMIER", false, true, compteUtilise);
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        String base = INFIRMIERS + "/";
        goldenSimple("liaison_compte_inactif_fiche_active_422", doPost(base + active + "/compte/lier", lier(compteInactif), adminA()));
        goldenSimple("liaison_compte_actif_fiche_inactive_422", doPost(base + inactive + "/compte/lier", lier(compteActif), adminA()));
        goldenSimple("liaison_compte_sans_role_infirmier_422", doPost(base + active + "/compte/lier", lier(compteMedecin), adminA()));
        goldenSimple("liaison_compte_d_un_autre_centre_422", doPost(base + active + "/compte/lier", lier(compteAutreCentre), adminA()));
        goldenSimple("liaison_compte_inexistant_422", doPost(base + active + "/compte/lier", lier(inconnu), adminA()));
        goldenSimple("liaison_compte_deja_utilise_422", doPost(base + active + "/compte/lier", lier(compteUtilise), adminA()));
        goldenSimple("liaison_fiche_deja_liee_422", doPost(base + liee + "/compte/lier", lier(compteActif), adminA()));
        goldenSimple("liaison_fiche_inconnue_422", doPost(base + inconnu + "/compte/lier", lier(compteActif), adminA()));
        goldenSimple("liaison_fiche_d_un_autre_centre_422", doPost(base + fiche(CENTRE_B, "INF_B", "M9", "Zerrouki", "Rania", true)
                + "/compte/lier", lier(compteActif), adminA()));
        goldenSimple("liaison_user_id_absent_400", doPost(base + active + "/compte/lier", json("{}"), adminA()));
        goldenSimple("liaison_user_id_null_400", doPost(base + active + "/compte/lier", lier(null), adminA()));
        goldenSimple("liaison_user_id_non_uuid", doPost(base + active + "/compte/lier", json("{'userId':'abc'}"), adminA()));
        etatInfirmiers("liaison_refus_etat_fiches");
        etatComptes("liaison_refus_etat_comptes");
    }

    @Test
    void l_ordre_des_controles_de_liaison_est_fiche_puis_liaison_existante_puis_compte_puis_utilisation_puis_etat() throws Exception {
        UUID compteUtilise = compte("C_UTILISE", "utilise", "Deja utilise", "INFIRMIER", false, CENTRE_A);
        UUID liee = fiche(CENTRE_A, "INF_LIEE", "M3", "Chikhi", "Nadia", null, "INFIRMIER", false, true, compteUtilise);
        UUID active = fiche(CENTRE_A, "INF_ACTIVE", "M1", "Amrani", "Sara", true);
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        goldenSimple("ordre_fiche_inconnue_avant_compte_inconnu", doPost(INFIRMIERS + "/" + inconnu + "/compte/lier", lier(inconnu), adminA()));
        goldenSimple("ordre_fiche_liee_avant_compte_inconnu", doPost(INFIRMIERS + "/" + liee + "/compte/lier", lier(inconnu), adminA()));
        goldenSimple("ordre_compte_utilise_avant_etat_incoherent", doPost(INFIRMIERS + "/" + active + "/compte/lier", lier(compteUtilise), adminA()));
    }

    // ------------------------------------------------------------------ création d'un compte depuis la fiche

    @Test
    void creer_un_compte_cree_un_compte_actif_infirmier_du_centre_avec_un_mot_de_passe_temporaire_affiche_une_seule_fois() throws Exception {
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        Resp r = doPost(INFIRMIERS + "/" + fiche + "/compte/creer", creer("p2.sara", "sara@example.dz"), adminA());
        golden("creation_compte_nominale_201", sansMotDePasse(r));
        String mdp = motDePasse(r);
        assertEquals(14, mdp.length(), "longueur");
        assertTrue(mdp.matches(".*[A-Z].*") && mdp.matches(".*[a-z].*") && mdp.matches(".*[0-9].*")
                && mdp.matches(".*[!#$%&*+=?@-].*"), "majuscule, minuscule, chiffre et symbole");
        assertFalse(mdp.matches(".*[0O1lI].*"), "sans caractères ambigus");
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username = 'p2.sara'", String.class);
        assertTrue(passwordEncoder.matches(mdp, hash), "le hachage correspond au mot de passe renvoyé");
        etatInfirmiers("creation_compte_etat_fiche");
        etatComptes("creation_compte_etat_compte");
        Resp liste = doGet(INFIRMIERS, adminA());
        assertFalse(liste.raw().contains(mdp), "le mot de passe n'est jamais relisible");
        golden("creation_compte_liste_200", liste);
        Resp second = doPost(INFIRMIERS + "/" + fiche + "/compte/creer", creer("p2.sara2", null), adminA());
        goldenSimple("creation_compte_seconde_fois_422", second);
        assertEquals(1, nombreDeComptes());
    }

    @Test
    void deux_mots_de_passe_temporaires_successifs_different() throws Exception {
        UUID un = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        UUID deux = fiche(CENTRE_A, "INF_2", "M2", "Benali", "Karim", true);
        String a = motDePasse(doPost(INFIRMIERS + "/" + un + "/compte/creer", creer("p2.un", null), adminA()));
        String b = motDePasse(doPost(INFIRMIERS + "/" + deux + "/compte/creer", creer("p2.deux", null), adminA()));
        assertFalse(a.equals(b));
    }

    @Test
    void le_nom_complet_du_compte_est_prenom_nom_ou_le_nom_seul_et_l_email_est_normalise() throws Exception {
        UUID avecPrenom = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        UUID sansPrenom = fiche(CENTRE_A, "INF_2", "M2", "Benali", null, true);
        UUID emailBlanc = fiche(CENTRE_A, "INF_3", "M3", "Chikhi", "Nadia", true);
        UUID emailSpacieux = fiche(CENTRE_A, "INF_4", "M4", "Dahmani", "Lina", true);
        UUID emailInvalide = fiche(CENTRE_A, "INF_5", "M5", "Essaid", "Omar", true);
        goldenSimple("compte_avec_prenom_201", sansMotDePasse(doPost(INFIRMIERS + "/" + avecPrenom + "/compte/creer", creer("p2.a", null), adminA())));
        goldenSimple("compte_sans_prenom_201", sansMotDePasse(doPost(INFIRMIERS + "/" + sansPrenom + "/compte/creer", creer("p2.b", null), adminA())));
        goldenSimple("compte_email_blanc_201", sansMotDePasse(doPost(INFIRMIERS + "/" + emailBlanc + "/compte/creer", creer("p2.c", "   "), adminA())));
        goldenSimple("compte_email_avec_espaces_201", sansMotDePasse(doPost(INFIRMIERS + "/" + emailSpacieux + "/compte/creer",
                creer("p2.d", "  lina@example.dz  "), adminA())));
        goldenSimple("compte_email_sans_format_201", sansMotDePasse(doPost(INFIRMIERS + "/" + emailInvalide + "/compte/creer",
                creer("p2.e", "pas-un-email"), adminA())));
        etatComptes("compte_noms_et_emails_etat_comptes");
    }

    @Test
    void creer_un_compte_valide_l_identifiant_apres_suppression_des_espaces_de_bord_et_refuse_les_doublons_sans_tenir_compte_de_la_casse() throws Exception {
        compte("C_EXISTANT", "existant", "Existant", "MEDECIN", true, CENTRE_B);
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        String url = INFIRMIERS + "/" + fiche + "/compte/creer";
        Map<String, Object> matrice = new LinkedHashMap<>();
        for (String id : new String[]{"", "   ", "ab", "p2", "p2.avec espace", "p2.accentué", "p2.slash/", "p2.a@b", "p2." + "x".repeat(48)}) {
            matrice.put("[" + id + "]", statutEtCode(doPost(url, creer(id, null), adminA())));
        }
        matrice.put("absent", statutEtCode(doPost(url, json("{}"), adminA())));
        matrice.put("null", statutEtCode(doPost(url, creer(null, null), adminA())));
        matrice.put("doublon_meme_casse", statutEtCode(doPost(url, creer("p2.existant", null), adminA())));
        matrice.put("doublon_autre_casse", statutEtCode(doPost(url, creer("P2.EXISTANT", null), adminA())));
        matrice.put("doublon_avec_espaces", statutEtCode(doPost(url, creer("  p2.existant  ", null), adminA())));
        goldenNode("creation_compte_identifiants_refuses", mapper.valueToTree(matrice));
        goldenSimple("creation_compte_identifiant_invalide_detail", doPost(url, creer("ab", null), adminA()));
        goldenSimple("creation_compte_identifiant_existant_detail", doPost(url, creer("p2.existant", null), adminA()));
        assertEquals(1, nombreDeComptes());
        goldenSimple("creation_compte_identifiant_avec_espaces_de_bord_201",
                sansMotDePasse(doPost(url, creer("  p2.bord  ", null), adminA())));
        etatComptes("creation_compte_identifiants_etat_comptes");
    }

    @Test
    void creer_un_compte_accepte_un_identifiant_de_50_caracteres_et_les_majuscules_mais_refuse_51() throws Exception {
        UUID un = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        UUID deux = fiche(CENTRE_A, "INF_2", "M2", "Benali", "Karim", true);
        String cinquante = "p2." + "x".repeat(47);
        goldenSimple("identifiant_50_caracteres_201", sansMotDePasse(doPost(INFIRMIERS + "/" + un + "/compte/creer", creer(cinquante, null), adminA())));
        goldenSimple("identifiant_51_caracteres_422", doPost(INFIRMIERS + "/" + deux + "/compte/creer", creer(cinquante + "y", null), adminA()));
        goldenSimple("identifiant_majuscules_201", sansMotDePasse(doPost(INFIRMIERS + "/" + deux + "/compte/creer", creer("P2.MAJ_et-min.9", null), adminA())));
        etatComptes("identifiants_limites_etat_comptes");
    }

    @Test
    void creer_un_compte_refuse_une_fiche_inactive_deja_liee_ou_inconnue_et_controle_l_etat_avant_l_identifiant() throws Exception {
        UUID inactive = fiche(CENTRE_A, "INF_INACTIVE", "M1", "Amrani", "Sara", false);
        UUID compte = compte("C_1", "lie", "Lie", "INFIRMIER", true, CENTRE_A);
        UUID liee = fiche(CENTRE_A, "INF_LIEE", "M2", "Benali", "Karim", null, "INFIRMIER", false, true, compte);
        UUID inconnu = UUID.nameUUIDFromBytes("inconnu".getBytes());
        goldenSimple("creation_compte_fiche_inactive_422", doPost(INFIRMIERS + "/" + inactive + "/compte/creer", creer("p2.ok", null), adminA()));
        goldenSimple("creation_compte_fiche_inactive_et_identifiant_invalide_422", doPost(INFIRMIERS + "/" + inactive + "/compte/creer", creer("ab", null), adminA()));
        goldenSimple("creation_compte_fiche_deja_liee_422", doPost(INFIRMIERS + "/" + liee + "/compte/creer", creer("p2.ok", null), adminA()));
        goldenSimple("creation_compte_fiche_inconnue_422", doPost(INFIRMIERS + "/" + inconnu + "/compte/creer", creer("p2.ok", null), adminA()));
        goldenSimple("creation_compte_fiche_d_un_autre_centre_422", doPost(INFIRMIERS + "/" + fiche(CENTRE_B, "INF_B", "M9", "Zerrouki", "Rania", true)
                + "/compte/creer", creer("p2.ok", null), adminA()));
        etatComptes("creation_compte_refus_etat_comptes");
        assertEquals(1, nombreDeComptes());
    }

    // ------------------------------------------------------------------ déliaison

    @Test
    void delier_un_compte_conserve_le_compte_tel_quel_et_le_rend_de_nouveau_liable_et_delier_une_fiche_sans_compte_est_un_succes() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", true, CENTRE_A);
        UUID liee = fiche(CENTRE_A, "INF_LIEE", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        UUID libre = fiche(CENTRE_A, "INF_LIBRE", "M2", "Benali", "Karim", true);
        goldenSimple("deliaison_nominale_200", doDelete(INFIRMIERS + "/" + liee + "/compte", adminA()));
        etatInfirmiers("deliaison_etat_fiches");
        etatComptes("deliaison_etat_compte_inchange");
        golden("deliaison_compte_de_nouveau_liable_200", doGet(INFIRMIERS + "/comptes-liables", adminA()));
        goldenSimple("deliaison_repetee_sans_compte_200", doDelete(INFIRMIERS + "/" + liee + "/compte", adminA()));
        goldenSimple("deliaison_fiche_jamais_liee_200", doDelete(INFIRMIERS + "/" + libre + "/compte", adminA()));
        goldenSimple("deliaison_fiche_inconnue_422", doDelete(INFIRMIERS + "/" + UUID.nameUUIDFromBytes("inconnu".getBytes()) + "/compte", adminA()));
        goldenSimple("deliaison_fiche_d_un_autre_centre_422", doDelete(INFIRMIERS + "/" + fiche(CENTRE_B, "INF_B", "M9", "Zerrouki", "Rania", true)
                + "/compte", adminA()));
    }

    @Test
    void delier_est_possible_meme_quand_la_fiche_est_inactive_et_le_compte_actif() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", true, CENTRE_A);
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, false, compte);
        goldenSimple("deliaison_fiche_inactive_compte_actif_200", doDelete(INFIRMIERS + "/" + fiche + "/compte", adminA()));
        etatInfirmiers("deliaison_incoherente_etat_fiche");
        etatComptes("deliaison_incoherente_etat_compte");
    }

    // ------------------------------------------------------------------ fiche vers compte

    @Test
    void desactiver_puis_reactiver_une_fiche_reliee_desactive_puis_reactive_son_compte() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", true, CENTRE_A);
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        goldenSimple("desactivation_fiche_reliee_200", doPost(INFIRMIERS + "/" + fiche + "/desactiver", "{}", adminA()));
        etatInfirmiers("desactivation_fiche_reliee_etat_fiche");
        etatComptes("desactivation_fiche_reliee_etat_compte");
        goldenSimple("reactivation_fiche_reliee_200", doPost(INFIRMIERS + "/" + fiche + "/reactiver", "{}", adminA()));
        etatInfirmiers("reactivation_fiche_reliee_etat_fiche");
        etatComptes("reactivation_fiche_reliee_etat_compte");
    }

    @Test
    void l_activation_force_le_compte_meme_quand_la_fiche_etait_deja_dans_cet_etat_et_ignore_un_compte_disparu() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", false, CENTRE_A);
        UUID deja = fiche(CENTRE_A, "INF_ACTIVE", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        UUID fantome = UUID.nameUUIDFromBytes("fantome".getBytes());
        aliasNommes.put(fantome.toString(), "<COMPTE_SUPPRIME>");
        UUID orpheline = fiche(CENTRE_A, "INF_ORPHELINE", "M2", "Benali", "Karim", null, "INFIRMIER", false, true, fantome);
        goldenSimple("reactivation_fiche_deja_active_reactive_le_compte_200", doPost(INFIRMIERS + "/" + deja + "/reactiver", "{}", adminA()));
        etatComptes("reactivation_forcee_etat_compte");
        goldenSimple("desactivation_fiche_dont_le_compte_a_disparu_200", doPost(INFIRMIERS + "/" + orpheline + "/desactiver", "{}", adminA()));
        etatInfirmiers("desactivation_compte_disparu_etat_fiches");
    }

    @Test
    void creer_un_compte_pour_une_fiche_puis_la_desactiver_desactive_le_compte_cree() throws Exception {
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        doPost(INFIRMIERS + "/" + fiche + "/compte/creer", creer("p2.sara", null), adminA());
        goldenSimple("desactivation_apres_creation_du_compte_200", doPost(INFIRMIERS + "/" + fiche + "/desactiver", "{}", adminA()));
        etatComptes("desactivation_apres_creation_etat_compte");
        goldenSimple("creation_de_compte_sur_fiche_desormais_inactive_422",
                doPost(INFIRMIERS + "/" + fiche(CENTRE_A, "INF_2", "M2", "Benali", "Karim", false) + "/compte/creer", creer("p2.x", null), adminA()));
    }

    // ------------------------------------------------------------------ compte vers fiche (administration des utilisateurs)

    @Test
    void desactiver_ou_reactiver_un_compte_dans_l_administration_des_utilisateurs_fait_suivre_la_fiche_reliee() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", true, CENTRE_A);
        UUID autre = compte("C_2", "karim", "Compte de Karim", "INFIRMIER", true, CENTRE_A);
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        fiche(CENTRE_A, "INF_2", "M2", "Benali", "Karim", null, "INFIRMIER", false, true, autre);
        goldenSimple("administration_desactive_le_compte_200", doPut(UTILISATEURS + "/" + compte, miseAJourCompte(false), adminA()));
        etatInfirmiers("administration_desactivation_etat_fiches");
        etatComptes("administration_desactivation_etat_comptes");
        goldenSimple("administration_reactive_le_compte_200", doPut(UTILISATEURS + "/" + compte, miseAJourCompte(true), adminA()));
        etatInfirmiers("administration_reactivation_etat_fiches");
    }

    @Test
    void l_administration_des_utilisateurs_ecrit_aussi_le_nom_complet_sans_effet_sur_la_fiche_et_une_mise_a_jour_sans_changement_d_etat_est_neutre() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", true, CENTRE_A);
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        doPut(UTILISATEURS + "/" + compte, miseAJourCompte(true), adminA());
        etatInfirmiers("administration_mise_a_jour_neutre_etat_fiche_KB_PLN_14");
        etatComptes("administration_mise_a_jour_neutre_etat_compte_KB_PLN_14");
    }

    @Test
    void un_compte_relie_a_des_fiches_de_deux_centres_les_desactive_toutes_les_deux() throws Exception {
        UUID compte = compte("C_MULTI", "multi", "Compte multi-centres", "INFIRMIER", true, CENTRE_A, CENTRE_B);
        UUID chezA = fiche(CENTRE_A, "INF_A", "M1", "Amrani", "Sara", true);
        UUID chezB = fiche(CENTRE_B, "INF_B", "M1", "Amrani", "Sara", true);
        goldenSimple("liaison_centre_A_200", doPost(INFIRMIERS + "/" + chezA + "/compte/lier", lier(compte), adminA()));
        goldenSimple("liaison_centre_B_du_meme_compte_200", doPost(INFIRMIERS + "/" + chezB + "/compte/lier", lier(compte), adminB()));
        doPut(UTILISATEURS + "/" + compte, miseAJourCompte(false), adminA());
        etatInfirmiers("compte_multi_centres_desactivation_etat_fiches_KB_PLN_15");
    }

    @Test
    void supprimer_un_compte_dans_l_administration_delie_d_abord_les_fiches_meme_si_la_suppression_echoue_500_KB_PLN_16() throws Exception {
        // Le compte porte un rôle et un centre : en base de test, les clés étrangères refusent la suppression (500) ;
        // la fiche a pourtant déjà été déliée (aucune transaction commune).
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", true, CENTRE_A);
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        goldenSimple("administration_suppression_refusee_par_la_base_500", doDelete(UTILISATEURS + "/" + compte, adminA()));
        etatInfirmiers("administration_suppression_refusee_etat_fiches");
        etatComptes("administration_suppression_refusee_etat_comptes");
    }

    @Test
    void supprimer_un_compte_sans_role_ni_centre_delie_les_fiches_sans_les_supprimer() throws Exception {
        UUID compte = compte("C_1", "sara", "Compte de Sara", null, true);
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        goldenSimple("administration_supprime_le_compte_200", doDelete(UTILISATEURS + "/" + compte, adminA()));
        etatInfirmiers("administration_suppression_etat_fiches");
        etatComptes("administration_suppression_etat_comptes");
        golden("administration_suppression_liste_sans_compte_200", doGet(INFIRMIERS, adminA()));
        goldenSimple("administration_suppression_d_un_compte_inconnu_200", doDelete(UTILISATEURS + "/"
                + UUID.nameUUIDFromBytes("inconnu".getBytes()), adminA()));
    }

    @Test
    void la_synchronisation_compte_vers_fiche_ne_touche_que_le_centre_de_l_administrateur_sur_des_donnees_historiques_multi_centres() throws Exception {
        // Ajouté par la refonte (26/10/2026) : le legacy permettait de lier un compte à deux centres ; ces données existent encore.
        UUID compte = compte("C_MULTI", "multi", "Compte multi-centres", "INFIRMIER", true, CENTRE_A, CENTRE_B);
        fiche(CENTRE_A, "INF_A", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        fiche(CENTRE_B, "INF_B", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        goldenSimple("administration_desactive_le_compte_multi_centres_200", doPut(UTILISATEURS + "/" + compte, miseAJourCompte(false), adminA()));
        etatInfirmiers("administration_multi_centres_etat_fiches");
    }

    @Test
    void supprimer_un_compte_lie_a_une_fiche_inactive_delie_la_fiche_et_supprime_le_compte_en_une_transaction() throws Exception {
        // Ajouté par la refonte (26/10/2026) : la suppression n'est plus refusée qu'avec une fiche ACTIVE.
        UUID compte = compte("C_1", "sara", "Compte de Sara", null, false);
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, false, compte);
        goldenSimple("administration_supprime_le_compte_d_une_fiche_inactive_200", doDelete(UTILISATEURS + "/" + compte, adminA()));
        etatInfirmiers("administration_suppression_fiche_inactive_etat_fiches");
        etatComptes("administration_suppression_fiche_inactive_etat_comptes");
    }

    @Test
    void la_suppression_refusee_par_la_base_laisse_la_fiche_inactive_liee_car_tout_est_dans_une_transaction() throws Exception {
        // Ajouté par la refonte (26/10/2026) : KB-PLN-16 corrigée, la déliaison est annulée avec la suppression.
        UUID compte = compte("C_1", "sara", "Compte de Sara", "INFIRMIER", false, CENTRE_A);
        fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", null, "INFIRMIER", false, false, compte);
        goldenSimple("administration_suppression_d_une_fiche_inactive_refusee_par_la_base_500",
                doDelete(UTILISATEURS + "/" + compte, adminA()));
        etatInfirmiers("administration_suppression_fiche_inactive_refusee_etat_fiches");
        etatComptes("administration_suppression_fiche_inactive_refusee_etat_comptes");
    }

    @Test
    void desactiver_la_fiche_d_un_centre_desactive_le_compte_partage_mais_pas_la_fiche_de_l_autre_centre_KB_PLN_15() throws Exception {
        UUID compte = compte("C_MULTI", "multi", "Compte multi-centres", "INFIRMIER", true, CENTRE_A, CENTRE_B);
        UUID chezA = fiche(CENTRE_A, "INF_A", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        fiche(CENTRE_B, "INF_B", "M1", "Amrani", "Sara", null, "INFIRMIER", false, true, compte);
        goldenSimple("desactivation_fiche_centre_A_200", doPost(INFIRMIERS + "/" + chezA + "/desactiver", "{}", adminA()));
        etatComptes("compte_partage_desactive_par_la_fiche_du_centre_A_KB_PLN_15");
        etatInfirmiers("compte_partage_fiche_du_centre_B_inchangee_KB_PLN_15");
    }

    @Test
    void le_mot_de_passe_temporaire_est_melange_les_quatre_classes_requises_ne_sont_pas_toujours_en_tete() throws Exception {
        int pasEnMajusculeEnTete = 0;
        for (int i = 0; i < 15; i++) {
            UUID fiche = fiche(CENTRE_A, "INF_" + i, "M" + i, "Nom" + i, "Prenom", true);
            String mdp = motDePasse(doPost(INFIRMIERS + "/" + fiche + "/compte/creer", creer("p2.melange" + i, null), adminA()));
            if (!Character.isUpperCase(mdp.charAt(0))) pasEnMajusculeEnTete++;
        }
        assertTrue(pasEnMajusculeEnTete > 0, "le mélange place parfois autre chose qu'une majuscule en tête");
    }

    @Test
    void creer_un_compte_sans_role_infirmier_dans_le_referentiel_des_roles_donne_422_business_rule_violation_sans_rien_ecrire() throws Exception {
        UUID fiche = fiche(CENTRE_A, "INF_1", "M1", "Amrani", "Sara", true);
        jdbc.update("UPDATE app_role SET code = 'INFIRMIER_TEMPORAIREMENT_ABSENT' WHERE code = 'INFIRMIER'");
        try {
            goldenSimple("creation_compte_role_infirmier_absent_du_referentiel", doPost(INFIRMIERS + "/" + fiche + "/compte/creer",
                    creer("p2.sansrole", null), adminA()));
        } finally {
            jdbc.update("UPDATE app_role SET code = 'INFIRMIER' WHERE code = 'INFIRMIER_TEMPORAIREMENT_ABSENT'");
        }
        assertEquals(0, nombreDeComptes());
        etatInfirmiers("creation_compte_role_absent_etat_fiche");
    }
}
