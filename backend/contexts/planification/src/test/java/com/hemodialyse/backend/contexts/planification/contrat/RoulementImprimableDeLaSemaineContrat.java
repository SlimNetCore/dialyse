package com.hemodialyse.backend.contexts.planification.contrat;

import com.hemodialyse.backend.contexts.planification.application.LigneDuPlanningDePresence.Situation;
import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine;
import com.hemodialyse.backend.contexts.planification.application.RoulementImprimableDeLaSemaine.LigneDuRoulement;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du roulement imprimé d'une semaine (DOC5e, ADR 0197) : les mêmes tests passent sur le double en mémoire et sur l'adapter JDBC
 * (H2). Aucun ordre n'est garanti par le port : on compare des ensembles de lignes.
 */
abstract class RoulementImprimableDeLaSemaineContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("5e000000-0000-0000-0000-00000000000a"));
    protected static final CentreId AUTRE = new CentreId(UUID.fromString("5e000000-0000-0000-0000-00000000000b"));
    protected static final UUID SALLE_1 = UUID.fromString("5e000000-0000-0000-0000-000000000101");
    protected static final UUID SALLE_2 = UUID.fromString("5e000000-0000-0000-0000-000000000102");
    protected static final UUID MATIN = UUID.fromString("5e000000-0000-0000-0000-000000000201");
    protected static final UUID SOIR = UUID.fromString("5e000000-0000-0000-0000-000000000202");
    /** Un dimanche : la semaine imprimée va du 8 au 14 novembre 2026. */
    protected static final LocalDate DIMANCHE = LocalDate.of(2026, 11, 8);

    protected abstract RoulementImprimableDeLaSemaine roulement();

    protected abstract void infirmier(CentreId centre, UUID id, String nom, String prenom, boolean actif);

    protected abstract void affectation(CentreId centre, UUID infirmier, UUID salle, UUID creneau, String jours);

    protected abstract void absence(CentreId centre, UUID infirmier, LocalDate debut, LocalDate fin);

    protected abstract void remplacement(CentreId centre, UUID infirmier, LocalDate jour, UUID salle, UUID creneau);

    private UUID infirmierAffecte(CentreId centre, String nom, String prenom, String jours) {
        UUID id = UUID.randomUUID();
        infirmier(centre, id, nom, prenom, true);
        affectation(centre, id, SALLE_1, MATIN, jours);
        return id;
    }

    private static LigneDuRoulement ligne(int rang, UUID salle, UUID creneau, String infirmier, Situation situation) {
        return new LigneDuRoulement(DIMANCHE.plusDays(rang), salle, creneau, infirmier, situation);
    }

    @Test
    void le_roulement_de_l_infirmier_actif_donne_une_ligne_prevue_les_jours_de_son_roulement() {
        infirmierAffecte(CENTRE, "Amrani", "Sara", "LUNDI,MERCREDI");

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).containsExactlyInAnyOrder(
                ligne(1, SALLE_1, MATIN, "Sara Amrani", Situation.PREVU), ligne(3, SALLE_1, MATIN, "Sara Amrani", Situation.PREVU));
    }

    @Test
    void un_infirmier_sans_prenom_est_imprime_par_son_nom_seul() {
        infirmierAffecte(CENTRE, "Zahi", null, "SAMEDI");

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).extracting(LigneDuRoulement::infirmier).containsExactly("Zahi");
    }

    @Test
    void un_infirmier_inactif_n_est_pas_prevu() {
        UUID id = UUID.randomUUID();
        infirmier(CENTRE, id, "Inactif", "Ines", false);
        affectation(CENTRE, id, SALLE_1, MATIN, "LUNDI");

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).isEmpty();
    }

    @Test
    void une_absence_qui_couvre_le_jour_bornes_comprises_rend_l_infirmier_absent() {
        UUID id = infirmierAffecte(CENTRE, "Benali", "Karim", "DIMANCHE,LUNDI,MARDI,MERCREDI,JEUDI");
        absence(CENTRE, id, DIMANCHE.plusDays(1), DIMANCHE.plusDays(3));

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).extracting(LigneDuRoulement::jour, LigneDuRoulement::situation).containsExactlyInAnyOrder(
                org.assertj.core.groups.Tuple.tuple(DIMANCHE, Situation.PREVU), org.assertj.core.groups.Tuple.tuple(DIMANCHE.plusDays(1), Situation.ABSENT),
                org.assertj.core.groups.Tuple.tuple(DIMANCHE.plusDays(2), Situation.ABSENT), org.assertj.core.groups.Tuple.tuple(DIMANCHE.plusDays(3), Situation.ABSENT),
                org.assertj.core.groups.Tuple.tuple(DIMANCHE.plusDays(4), Situation.PREVU));
    }

    @Test
    void une_absence_hors_de_la_semaine_ou_d_un_autre_centre_ne_change_rien() {
        UUID id = infirmierAffecte(CENTRE, "Benali", "Karim", "LUNDI");
        absence(CENTRE, id, DIMANCHE.plusDays(7), DIMANCHE.plusDays(9));
        absence(AUTRE, id, DIMANCHE, DIMANCHE.plusDays(6));

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).extracting(LigneDuRoulement::situation).containsExactly(Situation.PREVU);
    }

    @Test
    void un_remplacement_du_jour_donne_une_ligne_remplacant_meme_pour_un_infirmier_inactif() {
        UUID id = UUID.randomUUID();
        infirmier(CENTRE, id, "Roux", "Paul", false);
        remplacement(CENTRE, id, DIMANCHE.plusDays(2), SALLE_2, SOIR);
        remplacement(CENTRE, id, DIMANCHE.plusDays(7), SALLE_2, SOIR);

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).containsExactly(ligne(2, SALLE_2, SOIR, "Paul Roux", Situation.REMPLACANT));
    }

    @Test
    void rien_d_un_autre_centre_n_est_lu() {
        infirmierAffecte(AUTRE, "Etranger", "Autre", "LUNDI");
        UUID id = UUID.randomUUID();
        infirmier(AUTRE, id, "Roux", "Paul", true);
        remplacement(AUTRE, id, DIMANCHE.plusDays(1), SALLE_1, MATIN);

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).isEmpty();
        assertThat(roulement().lignes(AUTRE, DIMANCHE)).hasSize(2);
    }

    @Test
    void un_infirmier_remplacant_et_prevu_le_meme_jour_donne_deux_lignes() {
        UUID id = infirmierAffecte(CENTRE, "Amrani", "Sara", "LUNDI");
        remplacement(CENTRE, id, DIMANCHE.plusDays(1), SALLE_2, SOIR);

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).containsExactlyInAnyOrder(
                ligne(1, SALLE_1, MATIN, "Sara Amrani", Situation.PREVU), ligne(1, SALLE_2, SOIR, "Sara Amrani", Situation.REMPLACANT));
    }

    @Test
    void les_jours_sont_cherches_par_sous_chaine_sensible_a_la_casse() {
        infirmierAffecte(CENTRE, "Minuscule", "Marc", "lundi,mardi");
        infirmierAffecte(CENTRE, "Colle", "Anne", "JEUDIVENDREDI");

        assertThat(roulement().lignes(CENTRE, DIMANCHE)).extracting(LigneDuRoulement::jour, LigneDuRoulement::infirmier)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple(DIMANCHE.plusDays(4), "Anne Colle"),
                        org.assertj.core.groups.Tuple.tuple(DIMANCHE.plusDays(5), "Anne Colle"));
    }

    @Test
    void une_semaine_sans_roulement_ni_remplacement_est_vide() {
        assertThat(roulement().lignes(CENTRE, DIMANCHE)).isEmpty();
    }
}
