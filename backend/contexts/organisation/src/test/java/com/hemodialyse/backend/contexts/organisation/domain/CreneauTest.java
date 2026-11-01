package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreneauTest {
    @Test
    void creation_invalide() {
        CentreId c = new CentreId(UUID.randomUUID());
        assertThatThrownBy(() -> new Creneau(null, c, "C1", "Matin")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Creneau(new CreneauId(UUID.randomUUID()), c, "", "Matin")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modification() {
        Creneau c = new Creneau(new CreneauId(UUID.randomUUID()), new CentreId(UUID.randomUUID()), "C1", "Matin");
        c.modifier("C2", "Soir");
        assertThat(c.code()).isEqualTo("C2");
        assertThat(c.libelle()).isEqualTo("Soir");
    }
}
