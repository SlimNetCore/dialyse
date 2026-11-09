package com.hemodialyse.backend.contexts.pilotagedirection.contrat;

import com.hemodialyse.backend.contexts.pilotagedirection.application.InstantanesMensuels;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ContenuFige;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDInstantane;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat du port {@link InstantanesMensuels} : le MÊME jeu de tests passe sur le double en mémoire ({@code InstantanesEnMemoire}) et sur
 * l'adaptateur JDBC (H2 en mode PostgreSQL, puis PostgreSQL 16), pour que le double des tests d'application soit fidèle.
 */
public abstract class InstantanesMensuelsContrat {

    private static final YearMonth COURANT = YearMonth.of(2026, 11);

    protected abstract InstantanesMensuels instantanes();

    private InstantaneMensuel instantane(UUID societe, String mois, String contenu) {
        return InstantaneMensuel.figer(societe, MoisDInstantane.lire(mois), COURANT, new ContenuFige(contenu));
    }

    @Test
    void une_societe_sans_instantane_a_une_liste_vide_et_rien_a_relire() {
        UUID societe = UUID.randomUUID();

        assertThat(instantanes().lister(societe)).isEmpty();
        assertThat(instantanes().trouver(societe, MoisDInstantane.lire("2026-10"))).isEmpty();
    }

    @Test
    void un_instantane_enregistre_est_relu_tel_quel_avec_son_horodatage() {
        UUID societe = UUID.randomUUID();

        boolean cree = instantanes().enregistrer(instantane(societe, "2026-10", "{\"overview\":{}}"));

        assertThat(cree).isTrue();
        var relu = instantanes().trouver(societe, MoisDInstantane.lire("2026-10")).orElseThrow();
        assertThat(relu.mois()).isEqualTo("2026-10");
        assertThat(relu.contenu()).isEqualTo("{\"overview\":{}}");
        assertThat(relu.genereLe()).isNotNull();
    }

    @Test
    void un_second_instantane_du_meme_mois_est_refuse_sans_rien_changer() {
        UUID societe = UUID.randomUUID();
        instantanes().enregistrer(instantane(societe, "2026-10", "premier"));

        boolean cree = instantanes().enregistrer(instantane(societe, "2026-10", "second"));

        assertThat(cree).isFalse();
        assertThat(instantanes().lister(societe)).hasSize(1);
        assertThat(instantanes().trouver(societe, MoisDInstantane.lire("2026-10")).orElseThrow().contenu()).isEqualTo("premier");
    }

    @Test
    void la_liste_va_du_plus_recent_au_plus_ancien_et_ne_lit_pas_le_contenu() {
        UUID societe = UUID.randomUUID();
        instantanes().enregistrer(instantane(societe, "2026-08", "pas du json"));
        instantanes().enregistrer(instantane(societe, "2026-10", "{}"));
        instantanes().enregistrer(instantane(societe, "2026-09", "{}"));

        assertThat(instantanes().lister(societe)).extracting(InstantanesMensuels.InstantaneListe::mois)
                .containsExactly("2026-10", "2026-09", "2026-08");
    }

    @Test
    void les_instantanes_sont_cloisonnes_par_societe() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        instantanes().enregistrer(instantane(a, "2026-10", "{}"));

        assertThat(instantanes().lister(b)).isEmpty();
        assertThat(instantanes().trouver(b, MoisDInstantane.lire("2026-10"))).isEmpty();
        assertThat(instantanes().enregistrer(instantane(b, "2026-10", "{}"))).isTrue();
    }
}
