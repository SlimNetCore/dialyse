package com.hemodialyse.backend.contexts.gmao.domain;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.planmaintenance.PlanMaintenanceId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les identifiants typés refusent l'absence de valeur et se comparent par valeur. */
class IdentifiantsTypesTest {

    private static final UUID VALEUR = UUID.fromString("00000000-0000-0000-0000-000000000042");

    @Test
    void should_reject_a_missing_value_for_every_typed_identifier() {
        assertThatThrownBy(() -> new CentreId(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Centre requis");
        assertThatThrownBy(() -> new UtilisateurId(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Utilisateur requis");
        assertThatThrownBy(() -> new EquipementId(null)).isInstanceOf(IllegalArgumentException.class).hasMessage("Équipement requis");
        assertThatThrownBy(() -> new PlanMaintenanceId(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Plan de maintenance requis");
    }

    @Test
    void should_compare_identifiers_by_value() {
        assertThat(new CentreId(VALEUR)).isEqualTo(new CentreId(VALEUR));
        assertThat(new PlanMaintenanceId(VALEUR)).isNotEqualTo(new PlanMaintenanceId(UUID.randomUUID()));
    }
}
