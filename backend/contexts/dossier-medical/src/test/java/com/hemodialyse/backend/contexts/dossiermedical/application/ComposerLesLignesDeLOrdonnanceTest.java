package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.OrdonnancesAImprimer.LigneImprimable;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.OrdonnancesAImprimerEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** DOC5d (ADR 0196) : l'état imprimé d'une ordonnance compose en Java ce que l'ancienne requête joignait sur quatre tables. */
class ComposerLesLignesDeLOrdonnanceTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-0000000d5a01");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000d5a02");
    private static final UUID ORDONNANCE = UUID.fromString("00000000-0000-0000-0000-0000000d5a03");

    private final OrdonnancesAImprimerEnMemoire ordonnances = new OrdonnancesAImprimerEnMemoire();
    private Optional<IdentitesDesPatients.Identite> identite = Optional.of(new IdentitesDesPatients.Identite("Dupré", "Zoé", "F",
            LocalDate.of(1961, 3, 7), "ASS-1"));
    private Optional<String> centre = Optional.of("Centre fictif");

    private ComposerLesLignesDeLOrdonnance composer() {
        return new ComposerLesLignesDeLOrdonnance(ordonnances, (c, p) -> identite, c -> centre);
    }

    private void poser(LigneImprimable... lignes) {
        ordonnances.poser(new CentreId(CENTRE), ORDONNANCE, PATIENT, "ORD-00001", LocalDate.of(2026, 1, 4), "dr fictif", List.of(lignes));
    }

    @Test
    void should_compose_one_line_per_medication_with_patient_centre_and_header() {
        poser(new LigneImprimable(null, "Zinc", "1 par jour", "orale", 10, 2, "à jeun"),
                new LigneImprimable(null, "Alpha", "2 par jour", null, null, null, null));

        var lignes = composer().executer(CENTRE, ORDONNANCE);

        assertThat(lignes).containsExactly(
                new LigneDeLOrdonnanceImprimee("Dupré", "Zoé", LocalDate.of(1961, 3, 7), "ASS-1", "Centre fictif", "ORD-00001",
                        LocalDate.of(2026, 1, 4), "dr fictif", "Zinc", "1 par jour", "orale", 10, 2, "à jeun"),
                new LigneDeLOrdonnanceImprimee("Dupré", "Zoé", LocalDate.of(1961, 3, 7), "ASS-1", "Centre fictif", "ORD-00001",
                        LocalDate.of(2026, 1, 4), "dr fictif", "Alpha", "2 par jour", null, null, null, null));
    }

    @Test
    void should_print_the_code_label_before_the_typed_name_and_nothing_when_both_are_missing() {
        poser(new LigneImprimable("Libellé du code", "Nom saisi", "1", null, null, null, null),
                new LigneImprimable(null, "Nom saisi seul", "1", null, null, null, null),
                new LigneImprimable(null, null, "1", null, null, null, null));

        assertThat(composer().executer(CENTRE, ORDONNANCE)).extracting(LigneDeLOrdonnanceImprimee::medicament)
                .containsExactly("Libellé du code", "Nom saisi seul", null);
    }

    @Test
    void should_print_nothing_when_the_prescription_is_unknown_or_has_no_line() {
        assertThat(composer().executer(CENTRE, ORDONNANCE)).isEmpty();
        poser();
        assertThat(composer().executer(CENTRE, ORDONNANCE)).isEmpty();
    }

    @Test
    void should_print_nothing_when_the_patient_or_the_centre_is_unknown_as_the_inner_joins_did() {
        poser(new LigneImprimable(null, "Zinc", "1", null, null, null, null));

        identite = Optional.empty();
        assertThat(composer().executer(CENTRE, ORDONNANCE)).isEmpty();

        identite = Optional.of(new IdentitesDesPatients.Identite("Dupré", "Zoé", null, null, null));
        centre = Optional.empty();
        assertThat(composer().executer(CENTRE, ORDONNANCE)).isEmpty();
    }

    @Test
    void should_print_nothing_without_an_identifier_or_for_another_centre() {
        poser(new LigneImprimable(null, "Zinc", "1", null, null, null, null));

        assertThat(composer().executer(CENTRE, null)).isEmpty();
        assertThat(composer().executer(UUID.randomUUID(), ORDONNANCE)).isEmpty();
    }
}
