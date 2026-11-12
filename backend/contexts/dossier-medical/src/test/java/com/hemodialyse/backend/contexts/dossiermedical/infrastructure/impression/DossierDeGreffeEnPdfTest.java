package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.impression;

import com.hemodialyse.backend.contexts.dossiermedical.application.CartesDesPatients.Carte;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.AntecedentActif;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.DossierDeGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.Donneur;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** PL2 (ex {@code GreffePdfServiceTest}) : le PDF du dossier de greffe, en-tête du patient et contenus échappés. */
class DossierDeGreffeEnPdfTest {

    private final CentreId centre = new CentreId(UUID.randomUUID());
    private final AtomicReference<String> html = new AtomicReference<>();
    private final AtomicReference<UUID> centreHabille = new AtomicReference<>();

    private final DossierDeGreffeEnPdf impression = new DossierDeGreffeEnPdf((corps, c) -> {
        html.set(corps);
        centreHabille.set(c);
        return corps;
    });

    private byte[] imprimer(Optional<Carte> carte, DossierDeGreffe dossier) {
        return impression.enPdf(centre, carte, dossier);
    }

    private static DossierDeGreffe vide() {
        return new DossierDeGreffe(null, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void the_header_names_the_patient_with_code_and_declared_blood_group() {
        byte[] pdf = imprimer(Optional.of(new Carte("PAT-ROU00008", "Benali", "Karim", "A+")), vide());

        assertThat(pdf).startsWith("%PDF".getBytes());
        assertThat(centreHabille.get()).isEqualTo(centre.valeur());
        assertThat(html.get()).contains("<div class='muted'>Patient : Benali Karim (PAT-ROU00008) — Groupe sanguin déclaré : A+</div>");
    }

    @Test
    void a_missing_blood_group_is_printed_as_a_dash() {
        imprimer(Optional.of(new Carte("PAT-1", "Benali", "Karim", null)), vide());

        assertThat(html.get()).contains("(PAT-1) — Groupe sanguin déclaré : -</div>");
    }

    @Test
    void a_patient_card_that_is_absent_leaves_the_header_out() {
        imprimer(Optional.empty(), vide());

        assertThat(html.get()).doesNotContain("Patient :").contains("Dossier de préparation à la greffe rénale")
                .contains("Bilan non débuté.").contains("Aucune étape enregistrée.").contains("Aucun donneur candidat enregistré.");
    }

    @Test
    void the_contents_are_escaped_and_missing_values_are_printed_as_a_dash() {
        DossierDeGreffe dossier = new DossierDeGreffe(null, List.of(), List.of(), List.of(new AntecedentActif("<b>HTA</b> & 'diabète'"),
                new AntecedentActif(null)), List.of(new Donneur("Benali", null, "FRERE", null, "EN_COURS", "NON_FAIT")));

        imprimer(Optional.empty(), dossier);

        assertThat(html.get()).contains("&lt;b&gt;HTA&lt;/b&gt; &amp; &#39;diabète&#39;")
                .contains("<tr><td>-</td></tr>")
                .contains("<tr><td>Benali -</td><td>FRERE</td><td>-</td><td>EN_COURS</td><td>NON_FAIT</td></tr>");
    }
}
