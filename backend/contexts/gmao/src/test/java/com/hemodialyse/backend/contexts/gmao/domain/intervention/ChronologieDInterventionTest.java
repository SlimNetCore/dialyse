package com.hemodialyse.backend.contexts.gmao.domain.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.EvenementDeChronologie.Type;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** RG-GMA-031 : la chronologie d'une intervention, et l'événement « dernière modification » reproduit (KB-GMAO-79). */
class ChronologieDInterventionTest {

    private static final UtilisateurId CREATEUR = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    private static final UtilisateurId TECHNICIEN = new UtilisateurId(UUID.fromString("00000000-0000-0000-0000-0000000000f2"));
    private static final Instant T1 = Instant.parse("2026-03-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2026-03-01T09:00:00Z");
    private static final Instant T3 = Instant.parse("2026-03-01T10:00:00Z");
    private static final Instant T4 = Instant.parse("2026-03-01T11:00:00Z");
    private static final Instant T5 = Instant.parse("2026-03-01T12:00:00Z");

    private static Intervention intervention(Jalon creation, Jalon modification, Jalon demarrage, Jalon cloture,
                                             Jalon annulation, List<Rectification> rectifications) {
        return Intervention.reconstituer(new InterventionId(UUID.randomUUID()), new EquipementId(UUID.randomUUID()),
                new CentreId(UUID.randomUUID()), TypeIntervention.CURATIVE, StatutIntervention.TERMINEE, T2, T3, null,
                "Panne", null, null, null, StatutEquipement.EN_MAINTENANCE, null, PrioriteIntervention.NORMALE, null, null,
                null, creation, modification, demarrage, cloture, annulation, List.of(), rectifications);
    }

    private static List<Type> types(Intervention intervention) {
        return intervention.chronologie().stream().map(EvenementDeChronologie::type).toList();
    }

    @Test
    void should_list_creation_start_and_closure_in_chronological_order() {
        Intervention i = intervention(new Jalon(CREATEUR, T1), new Jalon(TECHNICIEN, T3), new Jalon(TECHNICIEN, T2),
                new Jalon(TECHNICIEN, T3), null, List.of());

        assertThat(i.chronologie()).containsExactly(
                new EvenementDeChronologie(Type.CREEE, T1, CREATEUR, null),
                new EvenementDeChronologie(Type.DEMARREE, T2, TECHNICIEN, null),
                new EvenementDeChronologie(Type.TERMINEE, T3, TECHNICIEN, null));
    }

    @Test
    void should_show_the_cancellation() {
        Intervention i = intervention(new Jalon(CREATEUR, T1), new Jalon(CREATEUR, T2), null, null,
                new Jalon(CREATEUR, T2), List.of());

        assertThat(types(i)).containsExactly(Type.CREEE, Type.ANNULEE);
    }

    @Test
    void should_show_each_rectification_with_the_closure_it_cancelled() {
        Rectification premiere = new Rectification(UUID.randomUUID(), "Erreur de saisie", CREATEUR, T4, T3, TECHNICIEN);
        Rectification seconde = new Rectification(UUID.randomUUID(), "Encore", CREATEUR, T5, null, null);
        Intervention i = intervention(new Jalon(CREATEUR, T1), new Jalon(CREATEUR, T5), new Jalon(TECHNICIEN, T2), null,
                null, List.of(premiere, seconde));

        assertThat(i.chronologie()).containsExactly(
                new EvenementDeChronologie(Type.CREEE, T1, CREATEUR, null),
                new EvenementDeChronologie(Type.DEMARREE, T2, TECHNICIEN, null),
                new EvenementDeChronologie(Type.TERMINEE, T3, TECHNICIEN, null),
                new EvenementDeChronologie(Type.RECTIFIEE, T4, CREATEUR, "Erreur de saisie"),
                new EvenementDeChronologie(Type.RECTIFIEE, T5, CREATEUR, "Encore"));
    }

    @Test
    void should_add_the_last_modification_only_when_it_is_after_every_other_event() {
        Intervention modifieeApres = intervention(new Jalon(CREATEUR, T1), new Jalon(TECHNICIEN, T4), new Jalon(TECHNICIEN, T2),
                new Jalon(TECHNICIEN, T3), null, List.of());
        Intervention modifieeEnMemeTemps = intervention(new Jalon(CREATEUR, T1), new Jalon(TECHNICIEN, T3),
                new Jalon(TECHNICIEN, T2), new Jalon(TECHNICIEN, T3), null, List.of());

        assertThat(modifieeApres.chronologie()).last()
                .isEqualTo(new EvenementDeChronologie(Type.MODIFIEE, T4, TECHNICIEN, null));
        assertThat(types(modifieeEnMemeTemps)).doesNotContain(Type.MODIFIEE);
    }

    @Test
    void should_keep_the_construction_order_for_events_at_the_same_instant() {
        Intervention i = intervention(new Jalon(CREATEUR, T1), null, new Jalon(TECHNICIEN, T1), new Jalon(TECHNICIEN, T1), null,
                List.of());

        assertThat(types(i)).containsExactly(Type.CREEE, Type.DEMARREE, Type.TERMINEE);
    }

    @Test
    void should_not_list_the_tasks() {
        Intervention i = Intervention.planifier(new InterventionId(UUID.randomUUID()), new EquipementId(UUID.randomUUID()),
                new CentreId(UUID.randomUUID()), TypeIntervention.CURATIVE, T2, "Panne", null,
                StatutEquipement.EN_MAINTENANCE, null, null, null, CREATEUR, T1);
        i.ajouterTache(new TacheId(UUID.randomUUID()), "Purger le circuit", CREATEUR, T2);

        assertThat(types(i)).containsExactly(Type.CREEE, Type.MODIFIEE);
    }

    @Test
    void should_accept_an_author_that_is_unknown() {
        Intervention i = intervention(new Jalon(null, T1), null, null, null, null, List.of());

        assertThat(i.chronologie()).containsExactly(new EvenementDeChronologie(Type.CREEE, T1, null, null));
    }
}
