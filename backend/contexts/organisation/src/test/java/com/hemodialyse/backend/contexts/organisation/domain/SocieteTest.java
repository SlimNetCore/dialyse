package com.hemodialyse.backend.contexts.organisation.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.SocieteId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SocieteTest {
    @Test
    void creation_invalide() {
        assertThatThrownBy(() -> new Societe(null, "C", "R", true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Societe(new SocieteId(UUID.randomUUID()), "", "R", true)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void modification() {
        Societe s = new Societe(new SocieteId(UUID.randomUUID()), "C", "R", true);
        s.modifier("C2", "R2", false);
        assertThat(s.code()).isEqualTo("C2");
        assertThat(s.actif()).isFalse();
    }
}
