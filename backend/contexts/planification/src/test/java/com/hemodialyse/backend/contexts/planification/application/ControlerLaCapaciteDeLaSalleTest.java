package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.planification.domain.CapaciteDeLaSalle;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.CapaciteDesSalles.CapaciteDeLaSalleDepassee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RG-PLN-061 : la capacité d'une salle borne le nombre de générateurs affectés (ancien {@code CapaciteSalleRegleTest}, repris). */
class ControlerLaCapaciteDeLaSalleTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final UUID SALLE = UUID.randomUUID();

    private static VueDesSalles.GenerateurVue generateur(UUID id) {
        return new VueDesSalles.GenerateurVue(id, "G" + id.toString().substring(0, 4), "Générateur", "EN_SERVICE");
    }

    private static ControlerLaCapaciteDeLaSalle controle(Integer capacite, UUID... generateurs) {
        VueDesSalles.SalleVue salle = new VueDesSalles.SalleVue(SALLE, "S1", "Salle 1", false, capacite,
                java.util.Arrays.stream(generateurs).map(ControlerLaCapaciteDeLaSalleTest::generateur).toList());
        return new ControlerLaCapaciteDeLaSalle((centre, id) -> CENTRE.equals(centre) && SALLE.equals(id)
                ? Optional.of(salle) : Optional.empty());
    }

    @Test
    void sans_capacite_aucune_limite() {
        assertThat(CapaciteDeLaSalle.depasseraitAvecUnGenerateurDePlus(null, 50)).isFalse();
    }

    @Test
    void un_generateur_de_plus_au_dela_de_la_capacite_est_refuse() {
        assertThat(CapaciteDeLaSalle.depasseraitAvecUnGenerateurDePlus(2, 1)).isFalse();
        assertThat(CapaciteDeLaSalle.depasseraitAvecUnGenerateurDePlus(2, 2)).isTrue();
    }

    @Test
    void une_salle_pleine_refuse_un_nouveau_generateur_avec_le_message_du_legacy() {
        ControlerLaCapaciteDeLaSalle controle = controle(1, UUID.randomUUID());
        assertThatThrownBy(() -> controle.verifierAffectation(CENTRE.valeur(), SALLE, null))
                .isInstanceOf(CapaciteDeLaSalleDepassee.class)
                .hasMessage("La salle « Salle 1 » est à sa capacité maximale (1 générateur(s)) : l'affectation est refusée");
    }

    @Test
    void le_generateur_deja_dans_la_salle_n_est_pas_compte() {
        UUID present = UUID.randomUUID();
        assertThatCode(() -> controle(1, present).verifierAffectation(CENTRE.valeur(), SALLE, present)).doesNotThrowAnyException();
    }

    @Test
    void sans_salle_ou_salle_inconnue_ou_sans_capacite_rien_n_est_controle() {
        assertThatCode(() -> controle(1, UUID.randomUUID()).verifierAffectation(CENTRE.valeur(), null, null))
                .doesNotThrowAnyException();
        assertThatCode(() -> controle(1, UUID.randomUUID()).verifierAffectation(CENTRE.valeur(), UUID.randomUUID(), null))
                .doesNotThrowAnyException();
        assertThatCode(() -> controle(null, UUID.randomUUID(), UUID.randomUUID()).verifierAffectation(CENTRE.valeur(), SALLE, null))
                .doesNotThrowAnyException();
    }
}
