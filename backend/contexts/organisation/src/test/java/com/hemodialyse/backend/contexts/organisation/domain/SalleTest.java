package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SalleTest {
    @Test
    void creation_invalide() {
        CentreId c = new CentreId(UUID.randomUUID());
        assertThatThrownBy(() -> new Salle(null, c, "S1", "Salle", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Salle(new SalleId(UUID.randomUUID()), null, "S1", "Salle", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Salle(new SalleId(UUID.randomUUID()), c, "", "Salle", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ouvrir_fermer() {
        Salle s = new Salle(new SalleId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), "S1", "Salle", false);
        s.ouvrir();
        assertThat(s.ouverte()).isTrue();
        s.fermer();
        assertThat(s.ouverte()).isFalse();
    }
}
