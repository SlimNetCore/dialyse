package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.InterventionsHotes;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.StatutIntervention;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.InterventionHote;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat partagé du port {@code InterventionsHotes} : exécuté contre le double in-memory et l'adapter JPA sur H2 (puis PostgreSQL
 * 16). Une intervention se lit avec son centre, son statut (y compris {@code EN_ATTENTE_VALIDATION}) et son état supprimé
 * logiquement ; l'identifiant inconnu est absent ; verrouiller rend la même chose que trouver.
 */
public abstract class InterventionsHotesContrat {

    protected static final CentreId CENTRE = new CentreId(UUID.fromString("99997600-0000-0000-0000-00000000000a"));

    protected abstract InterventionsHotes hotes();

    /** Pose l'intervention dans le stockage tel qu'une commande du module G4 le ferait. */
    protected abstract void semer(InterventionHote hote);

    private InterventionHote hote(StatutIntervention statut, boolean supprimee) {
        return new InterventionHote(new InterventionId(UUID.randomUUID()), CENTRE, statut, supprimee);
    }

    @ParameterizedTest
    @EnumSource(StatutIntervention.class)
    void should_read_the_centre_and_the_status_of_an_intervention(StatutIntervention statut) {
        InterventionHote hote = hote(statut, false);
        semer(hote);

        assertThat(hotes().trouver(hote.id())).contains(hote);
        assertThat(hotes().verrouiller(hote.id())).contains(hote);
    }

    @Test
    void should_still_find_a_logically_deleted_intervention_and_say_so() {
        InterventionHote hote = hote(StatutIntervention.PLANIFIEE, true);
        semer(hote);

        assertThat(hotes().trouver(hote.id())).contains(hote);
        assertThat(hotes().verrouiller(hote.id())).contains(hote);
    }

    @Test
    void should_find_nothing_for_an_unknown_intervention() {
        InterventionId inconnue = new InterventionId(UUID.randomUUID());

        assertThat(hotes().trouver(inconnue)).isEmpty();
        assertThat(hotes().verrouiller(inconnue)).isEmpty();
    }
}
