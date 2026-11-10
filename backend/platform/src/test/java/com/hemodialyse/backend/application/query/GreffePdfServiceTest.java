package com.hemodialyse.backend.application.query;

import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.DossierDeGreffePublie.DossierDeGreffe;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients.CarteDuPatient;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.reporting.DocumentIdentityProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Le dossier de greffe lit l'identité du patient par le langage publié de Patient &amp; couverture (ADR 0133 S4). */
class GreffePdfServiceTest {

    private final UUID centre = UUID.randomUUID();
    private final UUID patient = UUID.randomUUID();

    private String htmlProduit(Optional<CarteDuPatient> carte) {
        FichesDesPatients fiches = mock(FichesDesPatients.class);
        when(fiches.carte(centre, patient)).thenReturn(carte);
        DossierDeGreffePublie dossiers = mock(DossierDeGreffePublie.class);
        when(dossiers.duPatient(centre, patient)).thenReturn(new DossierDeGreffe(null, List.of(), List.of(), List.of(), List.of()));
        DocumentIdentityProvider identity = mock(DocumentIdentityProvider.class);
        when(identity.decorateHtml(any(), eq(centre))).thenAnswer(i -> i.getArgument(0));

        byte[] pdf = new GreffePdfService(fiches, dossiers, identity).exportPdf(CenterId.of(centre), patient);

        assertThat(pdf).startsWith("%PDF".getBytes());
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(identity).decorateHtml(html.capture(), eq(centre));
        return html.getValue();
    }

    @Test
    void the_header_names_the_patient_with_code_and_declared_blood_group() {
        String html = htmlProduit(Optional.of(new CarteDuPatient("PAT-ROU00008", "Benali", "Karim", "M", null, "A+", null, null)));

        assertThat(html).contains("<div class='muted'>Patient : Benali Karim (PAT-ROU00008) — Groupe sanguin déclaré : A+</div>");
    }

    @Test
    void a_missing_blood_group_is_printed_as_a_dash() {
        String html = htmlProduit(Optional.of(new CarteDuPatient("PAT-1", "Benali", "Karim", null, null, null, null, null)));

        assertThat(html).contains("(PAT-1) — Groupe sanguin déclaré : -</div>");
    }

    @Test
    void a_patient_card_that_is_absent_leaves_the_header_out() {
        String html = htmlProduit(Optional.empty());

        assertThat(html).doesNotContain("Patient :").contains("Dossier de préparation à la greffe rénale");
    }
}
