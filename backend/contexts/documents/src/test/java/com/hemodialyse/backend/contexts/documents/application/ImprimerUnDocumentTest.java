package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.doubles.Doubles.IdentitesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.ModelesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.MoteurEspion;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.ReglesEspion;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.documents.domain.DemandeInvalide;
import com.hemodialyse.backend.contexts.documents.domain.FormatDeSortie;
import com.hemodialyse.backend.contexts.documents.domain.IdentiteImprimable;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Les deux voies d'impression (par type, par modèle) sur des doubles en mémoire : gardes, paramètres posés par le serveur, rendu, refus. */
class ImprimerUnDocumentTest {

    private static final UUID D = new UUID(0L, 0xdL);
    private static final UUID E = new UUID(0L, 0xeL);
    private static final UUID PATIENT = new UUID(0L, 0x101L);
    private static final ModeleDeDocument LISTE = new ModeleDeDocument(new UUID(1L, 1L), "reports/liste_patients.jrxml", "EXCEL", "LISTE_PATIENTS");

    private final ModelesEnMemoire modeles = new ModelesEnMemoire();
    private final MoteurEspion moteur = new MoteurEspion();
    private final ReglesEspion regles = new ReglesEspion();
    private final SessionFixe session = new SessionFixe(D);
    private ImprimerUnDocument imprimer;

    @BeforeEach
    void preparer() {
        var identites = new IdentitesEnMemoire().centre(D, new IdentiteImprimable("Centre Papier", "", "", "", "", "", "",
                new IdentiteImprimable.Societe("Societe Papier", "", "", "", "", "", "", "", "", "", "Pied", null)));
        imprimer = new ImprimerUnDocument(session, modeles, new PapierEnTeteDesDocuments(identites), moteur, regles);
        modeles.actif(D, "LISTE_PATIENTS", LISTE);
    }

    @Test
    void should_render_the_active_model_of_the_centre_in_the_format_of_the_model() throws Exception {
        var document = imprimer.parType(D, " liste_patients ", null, Map.of());

        assertThat(document.octets()).isEqualTo(new byte[]{1, 2, 3});
        assertThat(document.format()).isEqualTo(FormatDeSortie.TABLEUR);
        assertThat(document.nomDeFichier()).isEqualTo("liste-patients.xlsx");
        assertThat(moteur.modele).isEqualTo(LISTE.id());
        assertThat(moteur.chemin).isEqualTo("reports/liste_patients.jrxml");
        assertThat(moteur.format).isEqualTo("EXCEL");
    }

    @Test
    void should_prefer_the_format_asked_by_the_caller_even_when_unknown_and_then_serve_a_pdf() {
        var document = imprimer.parType(D, "LISTE_PATIENTS", "DOCX", Map.of());

        assertThat(moteur.format).isEqualTo("DOCX");
        assertThat(document.format()).isEqualTo(FormatDeSortie.PDF);
    }

    @Test
    void should_set_the_server_parameters_and_ignore_the_values_sent_by_the_client() {
        Map<String, String> client = new HashMap<>();
        client.put("patientId", PATIENT.toString());
        client.put("CENTER_ID", E.toString());
        client.put("SOCIETE_NOM", "Usurpation SA");
        client.put("REPORT_LOCALE", "ar");
        client.put("TOTAL_HT_FACTURES", "999");
        regles.donnees = Map.of("TOTAL_HT_FACTURES", "12");

        imprimer.parType(D, "LISTE_PATIENTS", null, client);

        assertThat(moteur.parametres).containsEntry("patientId", PATIENT.toString()).containsEntry("CENTER_ID", D.toString())
                .containsEntry("SOCIETE_NOM", "Societe Papier").containsEntry("CENTRE_NOM", "Centre Papier")
                .containsEntry("TOTAL_HT_FACTURES", "12").doesNotContainKey("REPORT_LOCALE");
    }

    @Test
    void should_ask_the_sensitive_type_rules_before_validating_the_request() {
        regles.refus = new AccessDeniedException("Profil insuffisant");

        assertThatThrownBy(() -> imprimer.parType(D, "ORDONNANCE", null, Map.of())).isInstanceOf(AccessDeniedException.class);

        assertThat(regles.appels).containsExactly("type:ORDONNANCE");
        assertThat(modeles.recherches).isZero();
    }

    @Test
    void should_refuse_a_centre_other_than_the_session_one_before_reading_any_model() {
        modeles.actif(E, "LISTE_PATIENTS", new ModeleDeDocument(new UUID(2L, 2L), "reports/liste_patients.jrxml", "PDF", "LISTE_PATIENTS"));

        assertThatThrownBy(() -> imprimer.parType(E, "LISTE_PATIENTS", null, Map.of())).isInstanceOf(AccesRefuse.class);

        assertThat(modeles.recherches).isZero();
        assertThat(moteur.modele).isNull();
    }

    @Test
    void should_validate_the_request_before_checking_the_centre_so_that_a_missing_centre_is_a_400() {
        assertThatThrownBy(() -> imprimer.parType(null, "LISTE_PATIENTS", null, Map.of())).isInstanceOf(DemandeInvalide.class)
                .hasMessage("centerId est obligatoire");
        assertThat(session.demandes).isEmpty();
    }

    @Test
    void should_refuse_with_a_readable_message_when_the_centre_has_no_active_model_for_the_type() {
        assertThatThrownBy(() -> imprimer.parType(D, "inconnu", null, Map.of())).isInstanceOf(DemandeInvalide.class)
                .hasMessage("Aucun modèle actif trouvé pour ce centre/type: INCONNU (centre=" + D + ")");
    }

    @Test
    void should_wrap_an_engine_failure_and_an_engine_error_in_a_failed_rendering() {
        moteur.echec = new IllegalStateException("gabarit cassé");
        assertThatThrownBy(() -> imprimer.parType(D, "LISTE_PATIENTS", null, Map.of())).isInstanceOf(RenduEchoue.class)
                .hasCauseInstanceOf(IllegalStateException.class);

        moteur.echec = new NoClassDefFoundError("Jasper");
        assertThatThrownBy(() -> imprimer.parType(D, "LISTE_PATIENTS", null, Map.of())).isInstanceOf(RenduEchoue.class)
                .hasCauseInstanceOf(NoClassDefFoundError.class);
    }

    @Test
    void should_render_a_model_by_identifier_for_its_own_centre_with_the_file_name_of_the_model_type() {
        var document = imprimer.parModele(LISTE.id(), D, null, Map.of());

        assertThat(moteur.modele).isEqualTo(LISTE.id());
        assertThat(document.nomDeFichier()).isEqualTo("liste-patients.xlsx");
        assertThat(regles.appels).containsExactly("modele:LISTE_PATIENTS", "donnees:LISTE_PATIENTS");
    }

    @Test
    void should_refuse_a_model_of_another_centre_and_an_unknown_model_with_the_same_message() {
        var autre = new ModeleDeDocument(new UUID(3L, 3L), "reports/x.jrxml", "PDF", "CUSTOM");
        modeles.actif(E, "CUSTOM", autre);

        for (UUID modele : List.of(autre.id(), new UUID(9L, 9L))) {
            assertThatThrownBy(() -> imprimer.parModele(modele, D, null, Map.of())).isInstanceOf(DemandeInvalide.class)
                    .hasMessage("Modèle introuvable pour ce centre");
        }
    }

    @Test
    void should_refuse_by_model_a_centre_other_than_the_session_one() {
        modeles.actif(E, "LISTE_PATIENTS", new ModeleDeDocument(new UUID(2L, 2L), "reports/liste_patients.jrxml", "PDF", "LISTE_PATIENTS"));

        assertThatThrownBy(() -> imprimer.parModele(new UUID(2L, 2L), E, null, Map.of())).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_run_the_type_rule_of_the_model_before_validating_when_printing_by_model() {
        regles.refus = new AccessDeniedException("Profil insuffisant pour la synthèse de facturation");

        assertThatThrownBy(() -> imprimer.parModele(LISTE.id(), null, null, Map.of())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void should_require_the_centre_when_printing_by_model() {
        assertThatThrownBy(() -> imprimer.parModele(LISTE.id(), null, null, Map.of())).isInstanceOf(DemandeInvalide.class)
                .hasMessage("centerId est obligatoire");
    }

    @Test
    void should_give_custom_as_the_file_name_of_a_model_without_type() {
        var sans = new ModeleDeDocument(new UUID(4L, 4L), "reports/x.jrxml", "PDF", "CUSTOM");
        modeles.actif(D, "CUSTOM", sans);

        assertThat(imprimer.parModele(sans.id(), D, null, Map.of()).nomDeFichier()).isEqualTo("custom.pdf");
    }
}
