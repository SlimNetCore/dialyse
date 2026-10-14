package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.EquipementExiste;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrat du port {@link EquipementExiste} : double in-memory ET repository JPA des équipements. */
public abstract class EquipementExisteContrat {

    protected static final EquipementId CONNU = new EquipementId(UUID.fromString("99998400-0000-0000-0000-0000000000e1"));
    protected static final EquipementId INCONNU = new EquipementId(UUID.fromString("99998400-0000-0000-0000-0000000000e9"));
    protected static final EquipementId SUPPRIME_LOGIQUEMENT = new EquipementId(UUID.fromString("99998400-0000-0000-0000-0000000000e2"));

    protected abstract EquipementExiste equipements();

    /** Prépare le référentiel : CONNU présent, SUPPRIME_LOGIQUEMENT présent mais marqué supprimé, INCONNU absent. */
    protected abstract void preparerLeReferentiel();

    @Test
    void should_know_an_equipement_of_the_referential() {
        preparerLeReferentiel();

        assertThat(equipements().existe(CONNU)).isTrue();
    }

    @Test
    void should_not_know_an_equipement_absent_from_the_referential() {
        preparerLeReferentiel();

        assertThat(equipements().existe(INCONNU)).isFalse();
    }

    @Test
    void should_still_know_a_logically_deleted_equipement_like_the_legacy_foreign_key_does() {
        preparerLeReferentiel();

        assertThat(equipements().existe(SUPPRIME_LOGIQUEMENT)).isTrue();
    }
}
