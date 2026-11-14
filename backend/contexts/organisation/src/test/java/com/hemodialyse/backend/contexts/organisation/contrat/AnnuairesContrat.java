package com.hemodialyse.backend.contexts.organisation.contrat;

import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesCentres;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.AnnuaireDesSocietes.CentreDeLaSociete;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du langage publié d'Organisation (O4 lot A, ADR 0107) : exécuté sur le faux en mémoire et sur l'adapter JDBC (H2). Il fixe
 * les comportements des lecteurs SQL retirés des consommateurs : centres actifs (état non renseigné = actif), tous les centres,
 * fuseau absent / vide / illisible / centre inconnu, société d'un centre, société avec ses centres.
 */
abstract class AnnuairesContrat {

    static final UUID SOCIETE_A = UUID.fromString("99998200-0000-0000-0000-0000000000a1");
    static final UUID SOCIETE_B = UUID.fromString("99998200-0000-0000-0000-0000000000b1");
    static final UUID SOCIETE_INACTIVE = UUID.fromString("99998200-0000-0000-0000-0000000000c1");
    static final UUID SOCIETE_SANS_CENTRE_ACTIF = UUID.fromString("99998200-0000-0000-0000-0000000000d1");
    static final UUID C1 =UUID.fromString("99998200-0000-0000-0000-000000000001");
    static final UUID C2 = UUID.fromString("99998200-0000-0000-0000-000000000002");
    static final UUID C3 = UUID.fromString("99998200-0000-0000-0000-000000000003");
    static final UUID C4 = UUID.fromString("99998200-0000-0000-0000-000000000004");
    static final UUID C5 = UUID.fromString("99998200-0000-0000-0000-000000000005");
    static final UUID C6 = UUID.fromString("99998200-0000-0000-0000-000000000006");

    protected abstract AnnuaireDesCentres centres();

    protected abstract AnnuaireDesSocietes societes();

    protected abstract void societe(UUID id, String raisonSociale, boolean actif);

    protected abstract void centre(UUID id, UUID societe, String nom, Boolean actif, String fuseau);

    /** Trois centres de la société A (un actif, un désactivé, un sans état), deux de la société B. */
    protected void donnees() {
        societe(SOCIETE_A, "Alpha", true);
        societe(SOCIETE_B, "Beta", true);
        societe(SOCIETE_INACTIVE, "Gamma", false);
        centre(C3, SOCIETE_A, "Zeta", null, null);
        centre(C1, SOCIETE_A, "Alger", true, "Africa/Algiers");
        centre(C2, SOCIETE_A, "Blida", false, "America/New_York");
        centre(C4, SOCIETE_B, "Oran", true, "   ");
        centre(C5, SOCIETE_B, "Annaba", true, "Pas/UnFuseau");
    }

    @Test
    void les_centres_actifs_sont_tries_par_identifiant_et_un_etat_non_renseigne_vaut_actif() {
        donnees();
        assertThat(centres().actifs()).containsExactly(C1, C3, C4, C5);
    }

    /** KB-ORG-09 corrigé (ADR 0115) : une seule définition de « centre actif » ; la variante stricte du Stock n'existe plus. */
    @Test
    void il_n_existe_qu_une_definition_de_centre_actif_et_elle_inclut_un_etat_non_renseigne() {
        donnees();
        assertThat(centres().actifs()).contains(C3).doesNotContain(C2);
        assertThat(AnnuaireDesCentres.class.getMethods()).extracting(java.lang.reflect.Method::getName)
                .doesNotContain("actifsAuSensStrict");
    }

    @Test
    void tous_les_centres_comprennent_les_centres_desactives() {
        donnees();
        assertThat(centres().tous()).containsExactlyInAnyOrder(C1, C2, C3, C4, C5);
    }

    @Test
    void le_fuseau_d_un_centre_est_celui_de_sa_fiche() {
        donnees();
        assertThat(centres().fuseauHoraireDe(C1)).contains(ZoneId.of("Africa/Algiers"));
        assertThat(centres().fuseauHoraireDe(C2)).contains(ZoneId.of("America/New_York"));
    }

    @Test
    void le_fuseau_est_vide_si_absent_blanc_illisible_ou_si_le_centre_est_inconnu() {
        donnees();
        assertThat(centres().fuseauHoraireDe(C3)).as("absent").isEmpty();
        assertThat(centres().fuseauHoraireDe(C4)).as("blanc").isEmpty();
        assertThat(centres().fuseauHoraireDe(C5)).as("illisible").isEmpty();
        assertThat(centres().fuseauHoraireDe(C6)).as("centre inconnu").isEmpty();
    }

    @Test
    void les_societes_actives_excluent_les_societes_desactivees() {
        donnees();
        assertThat(societes().societesActives()).containsExactlyInAnyOrder(SOCIETE_A, SOCIETE_B);
    }

    @Test
    void la_societe_d_un_centre_est_celle_a_laquelle_il_est_rattache_et_vide_si_le_centre_est_inconnu() {
        donnees();
        assertThat(societes().societeDuCentre(C4)).contains(SOCIETE_B);
        assertThat(societes().societeDuCentre(C6)).isEmpty();
    }

    @Test
    void une_societe_porte_ses_centres_tries_par_nom_et_un_etat_non_renseigne_vaut_actif() {
        donnees();
        var societe = societes().societe(SOCIETE_A).orElseThrow();
        assertThat(societe.raisonSociale()).isEqualTo("Alpha");
        assertThat(societe.centres()).containsExactly(
                new CentreDeLaSociete(C1, "Alger", true),
                new CentreDeLaSociete(C2, "Blida", false),
                new CentreDeLaSociete(C3, "Zeta", true));
    }

    /** ADR 0174 : tous les centres avec leur nom, désactivés compris, triés par nom par Organisation. */
    @Test
    void tous_les_centres_avec_leur_nom_sont_tries_par_nom_et_comprennent_les_centres_desactives() {
        donnees();
        assertThat(centres().tousAvecNom()).containsExactly(
                new AnnuaireDesCentres.CentreNomme(C1, "Alger"),
                new AnnuaireDesCentres.CentreNomme(C5, "Annaba"),
                new AnnuaireDesCentres.CentreNomme(C2, "Blida"),
                new AnnuaireDesCentres.CentreNomme(C4, "Oran"),
                new AnnuaireDesCentres.CentreNomme(C3, "Zeta"));
    }

    @Test
    void le_nom_d_un_centre_est_celui_de_sa_fiche_et_vide_si_le_centre_est_inconnu() {
        donnees();
        assertThat(centres().nomDuCentre(C2)).contains("Blida");
        assertThat(centres().nomDuCentre(C6)).isEmpty();
    }

    /** ADR 0174 : sociétés proposées à la connexion = actives avec au moins un centre actif ; une société sans centre actif est exclue. */
    @Test
    void les_societes_proposees_sont_actives_avec_un_centre_actif_triees_par_raison_sociale() {
        donnees();
        assertThat(societes().societesAvecUnCentreActif()).containsExactly(
                new AnnuaireDesSocietes.SocieteNommee(SOCIETE_A, "Alpha"),
                new AnnuaireDesSocietes.SocieteNommee(SOCIETE_B, "Beta"));
        societe(SOCIETE_SANS_CENTRE_ACTIF, "Aaa", true);
        centre(C6, SOCIETE_SANS_CENTRE_ACTIF, "Ferme", false, null);
        assertThat(societes().societesAvecUnCentreActif()).extracting(AnnuaireDesSocietes.SocieteNommee::id)
                .containsExactly(SOCIETE_A, SOCIETE_B);
    }

    /** Le code que les deux implémentations donnent à chaque centre de fixture. */
    static String code(UUID centre) {
        return "ANNU-" + centre.toString().substring(30);
    }

    /** ADR 0175 (IDN-licence) : le code du centre est celui de sa fiche ; vide si le centre est inconnu. */
    @Test
    void le_code_d_un_centre_est_celui_de_sa_fiche_et_vide_si_le_centre_est_inconnu() {
        donnees();
        assertThat(centres().codeDuCentre(C1)).contains(code(C1));
        assertThat(centres().codeDuCentre(C2)).as("centre desactive").contains(code(C2));
        assertThat(centres().codeDuCentre(C6)).as("centre inconnu").isEmpty();
    }

    /** ADR 0175 : tous les centres avec leur nom et leur société, par nom ; une société inconnue donne une raison sociale absente. */
    @Test
    void tous_les_centres_portent_leur_nom_leur_societe_et_la_raison_sociale_tries_par_nom() {
        donnees();
        centre(C6, UUID.fromString("99998200-0000-0000-0000-0000000000fe"), "Sans societe", true, null);
        assertThat(societes().centresAvecLeurSociete()).containsExactly(
                new AnnuaireDesSocietes.CentreRattache(C1, "Alger", SOCIETE_A, "Alpha"),
                new AnnuaireDesSocietes.CentreRattache(C5, "Annaba", SOCIETE_B, "Beta"),
                new AnnuaireDesSocietes.CentreRattache(C2, "Blida", SOCIETE_A, "Alpha"),
                new AnnuaireDesSocietes.CentreRattache(C4, "Oran", SOCIETE_B, "Beta"),
                new AnnuaireDesSocietes.CentreRattache(C6, "Sans societe", UUID.fromString("99998200-0000-0000-0000-0000000000fe"), null),
                new AnnuaireDesSocietes.CentreRattache(C3, "Zeta", SOCIETE_A, "Alpha"));
    }

    @Test
    void une_societe_inconnue_est_absente() {
        donnees();
        assertThat(societes().societe(UUID.fromString("99998200-0000-0000-0000-0000000000ff"))).isEmpty();
    }
}
