package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.doubles.Doubles.ModelesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.Doubles.SessionFixe;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.GabaritsLivresEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.HistoriquesEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.ModelesDuCentreEnMemoire;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.UniteImmediate;
import com.hemodialyse.backend.contexts.documents.doubles.DoublesDesGabarits.VerificationFixe;
import com.hemodialyse.backend.contexts.documents.domain.DefinitionDeModele;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCatalogue;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCentre;
import com.hemodialyse.backend.contexts.documents.domain.RechercheDeModeles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La gestion des modèles de document avec des doubles en mémoire : centre de la session, identifiant attribué, doublons, suppression avec les versions. */
class GererLesModelesTest {

    private static final UUID D = new UUID(0L, 0xdL);
    private static final UUID E = new UUID(0L, 0xeL);
    private static final String CHEMIN = "reports/attestation.jrxml";

    private final ModelesEnMemoire modeles = new ModelesEnMemoire();
    private final ModelesDuCentreEnMemoire modelesDesGabarits = new ModelesDuCentreEnMemoire();
    private final HistoriquesEnMemoire historiques = new HistoriquesEnMemoire();
    private final UniteImmediate unite = new UniteImmediate();
    private GererLesGabarits gabarits;
    private GererLesModeles gestion;

    @BeforeEach
    void preparer() throws Exception {
        var livres = new GabaritsLivresEnMemoire();
        try (var in = new ClassPathResource(CHEMIN).getInputStream()) {
            livres.livrer(CHEMIN, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
        SessionFixe session = new SessionFixe(D);
        gabarits = new GererLesGabarits(session, modelesDesGabarits, livres, historiques, new VerificationFixe(), unite, Clock.systemUTC(),
                UUID::randomUUID);
        gestion = new GererLesModeles(session, modeles, gabarits, unite, () -> new UUID(7L, 7L));
    }

    private static DefinitionDeModele definition(UUID centre, String code) {
        return DefinitionDeModele.definir(centre, code, "Libelle " + code, "ATTESTATION", CHEMIN, "PDF", null, true);
    }

    @Test
    void should_use_the_centre_of_the_session_and_refuse_another_one() {
        assertThat(gestion.exigerLeCentre(null).valeur()).isEqualTo(D);
        assertThat(gestion.exigerLeCentre(D).valeur()).isEqualTo(D);
        assertThatThrownBy(() -> gestion.exigerLeCentre(E)).isInstanceOf(AccesRefuse.class);
    }

    @Test
    void should_create_a_model_under_the_requested_identifier_or_under_a_new_one() {
        UUID demande = new UUID(1L, 1L);

        assertThat(gestion.creer(demande, definition(D, "A"))).isEqualTo(demande);
        assertThat(gestion.creer(null, definition(D, "B"))).isEqualTo(new UUID(7L, 7L));
        assertThat(gestion.lister(D, null)).extracting(ModeleDuCatalogue::code).containsExactly("A", "B");
    }

    @Test
    void should_refuse_a_second_model_with_the_same_code_in_the_centre() {
        gestion.creer(null, definition(D, "A"));

        assertThatThrownBy(() -> gestion.creer(new UUID(2L, 2L), definition(D, "A"))).isInstanceOf(ModeleEnDoublon.class);
        gestion.creer(new UUID(3L, 3L), definition(E, "A"));
    }

    @Test
    void should_replace_the_definition_and_ignore_an_unknown_model() {
        UUID id = gestion.creer(null, definition(D, "A"));

        gestion.modifier(id, definition(D, "A2"));
        gestion.modifier(new UUID(5L, 5L), definition(D, "Z"));

        assertThat(gestion.lister(D, null)).extracting(ModeleDuCatalogue::code).containsExactly("A2");
    }

    @Test
    void should_search_a_page_of_the_centre() {
        gestion.creer(new UUID(1L, 1L), definition(D, "A"));
        gestion.creer(new UUID(2L, 2L), definition(D, "B"));

        assertThat(gestion.rechercher(RechercheDeModeles.de(D, null, 1, 1))).extracting(ModeleDuCatalogue::code).containsExactly("B");
    }

    @Test
    void should_delete_a_model_together_with_the_history_of_its_versions_in_one_unit_of_work() throws Exception {
        UUID id = new UUID(1L, 1L);
        gestion.creer(id, definition(D, "A"));
        modelesDesGabarits.ajouter(new ModeleDuCentre(id, D, "A", "ATTESTATION", CHEMIN));
        String source;
        try (var in = new ClassPathResource(CHEMIN).getInputStream()) {
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        gabarits.deposer(D, id, "modele.jrxml", source.replace("topMargin=\"40\"", "topMargin=\"36\"").getBytes(StandardCharsets.UTF_8), null);
        assertThat(historiques.contenuEnVigueur(D, id)).isPresent();
        int avant = unite.executions;

        gestion.supprimer(D, id);

        assertThat(historiques.contenuEnVigueur(D, id)).isEmpty();
        assertThat(gestion.lister(D, null)).isEmpty();
        assertThat(unite.executions).isGreaterThan(avant);
    }

    @Test
    void should_delete_nothing_for_an_unknown_model_or_the_model_of_another_centre() {
        gestion.creer(new UUID(1L, 1L), definition(E, "A"));

        gestion.supprimer(D, new UUID(1L, 1L));
        gestion.supprimer(D, new UUID(9L, 9L));

        assertThat(gestion.lister(E, null)).hasSize(1);
    }
}
