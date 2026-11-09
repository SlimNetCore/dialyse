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
    static final UUID C1 = UUID.fromString("99998200-0000-0000-0000-000000000001");
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

    @Test
    void une_societe_inconnue_est_absente() {
        donnees();
        assertThat(societes().societe(UUID.fromString("99998200-0000-0000-0000-0000000000ff"))).isEmpty();
    }
}
