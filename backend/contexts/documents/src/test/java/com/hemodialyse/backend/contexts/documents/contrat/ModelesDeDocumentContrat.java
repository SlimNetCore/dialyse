package com.hemodialyse.backend.contexts.documents.contrat;

import com.hemodialyse.backend.contexts.documents.application.ModeleEnDoublon;
import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.DefinitionDeModele;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCatalogue;
import com.hemodialyse.backend.contexts.documents.domain.RechercheDeModeles;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat de la lecture des modèles de document : exécuté sur le double en mémoire et sur l'adapter JDBC (H2), pour garantir que le double
 * est fidèle. Reprend les requêtes de DocumentRestController : modèle ACTIF le plus récent du centre pour le type (comparé sans casse),
 * modèle d'un identifiant limité au centre, type d'un modèle de n'importe quel centre.
 */
abstract class ModelesDeDocumentContrat {

    static final UUID D = UUID.fromString("99999140-0000-0000-0000-00000000000d");
    static final UUID E = UUID.fromString("99999140-0000-0000-0000-00000000000e");
    static final UUID ANCIEN = UUID.fromString("99999140-0000-0000-0000-000000000a01");
    static final UUID RECENT = UUID.fromString("99999140-0000-0000-0000-000000000a02");
    static final UUID DESACTIVE = UUID.fromString("99999140-0000-0000-0000-000000000a03");
    static final UUID DE_E = UUID.fromString("99999140-0000-0000-0000-000000000a04");
    static final UUID INCONNU = UUID.fromString("99999140-0000-0000-0000-000000000aff");

    protected abstract ModelesDeDocument modeles();

    /** Insère un modèle tel que la base le stocke (le type est enregistré tel quel, la casse comprise). */
    protected abstract void modele(UUID id, UUID centre, String type, String chemin, String format, boolean actif, Instant creeLe);

    private void donnees() {
        modele(ANCIEN, D, "LISTE_PATIENTS", "reports/ancien.jrxml", "PDF", true, Instant.parse("2026-01-01T00:00:00Z"));
        modele(RECENT, D, "liste_patients", "reports/recent.jrxml", "EXCEL", true, Instant.parse("2026-06-01T00:00:00Z"));
        modele(DESACTIVE, D, "PEC", "reports/pec.jrxml", "PDF", false, Instant.parse("2026-03-01T00:00:00Z"));
        modele(DE_E, E, "LISTE_PATIENTS", "reports/e.jrxml", "PDF", true, Instant.parse("2026-02-01T00:00:00Z"));
    }

    @Test
    void should_give_the_most_recent_active_model_of_the_centre_for_the_type_ignoring_case() {
        donnees();

        ModeleDeDocument modele = modeles().actifDuCentre(D, "LISTE_PATIENTS").orElseThrow();

        assertThat(modele.id()).isEqualTo(RECENT);
        assertThat(modele.cheminDuGabarit()).isEqualTo("reports/recent.jrxml");
        assertThat(modele.formatDImpression()).isEqualTo("EXCEL");
        assertThat(modele.type()).isEqualTo("liste_patients");
    }

    @Test
    void should_ignore_inactive_models_other_centres_and_unknown_types() {
        donnees();

        assertThat(modeles().actifDuCentre(D, "PEC")).isEmpty();
        assertThat(modeles().actifDuCentre(E, "PEC")).isEmpty();
        assertThat(modeles().actifDuCentre(D, "INCONNU")).isEmpty();
        assertThat(modeles().actifDuCentre(E, "LISTE_PATIENTS").orElseThrow().id()).isEqualTo(DE_E);
    }

    @Test
    void should_give_a_model_by_identifier_only_for_its_own_centre_active_or_not() {
        donnees();

        assertThat(modeles().duCentre(DESACTIVE, D)).isPresent();
        assertThat(modeles().duCentre(ANCIEN, D).orElseThrow().cheminDuGabarit()).isEqualTo("reports/ancien.jrxml");
        assertThat(modeles().duCentre(DE_E, D)).isEmpty();
        assertThat(modeles().duCentre(INCONNU, D)).isEmpty();
    }

    private static DefinitionDeModele definition(UUID centre, String code, String libelle, String type, boolean actif) {
        return DefinitionDeModele.definir(centre, code, libelle, type, "reports/" + code.toLowerCase() + ".jrxml", "PDF", null, actif);
    }

    private static final UUID N1 = UUID.fromString("99999140-0000-0000-0000-000000000c01");
    private static final UUID N2 = UUID.fromString("99999140-0000-0000-0000-000000000c02");
    private static final UUID N3 = UUID.fromString("99999140-0000-0000-0000-000000000c03");
    private static final UUID N4 = UUID.fromString("99999140-0000-0000-0000-000000000c04");

    @Test
    void should_list_the_models_of_the_centre_by_label_with_an_exact_type_filter() {
        modeles().creer(N1, definition(D, "B", "Beta", "CUSTOM", true));
        modeles().creer(N2, definition(D, "A", "Alpha", "ATTESTATION", false));
        modeles().creer(N3, definition(E, "A", "Alpha E", "ATTESTATION", true));

        assertThat(modeles().lister(D, null)).extracting(ModeleDuCatalogue::id).containsExactly(N2, N1);
        assertThat(modeles().lister(D, "ATTESTATION")).extracting(ModeleDuCatalogue::id).containsExactly(N2);
        assertThat(modeles().lister(D, "attestation")).isEmpty();
        assertThat(modeles().lister(D, "")).isEmpty();
        ModeleDuCatalogue alpha = modeles().lister(D, "ATTESTATION").get(0);
        assertThat(alpha.centre()).isEqualTo(D);
        assertThat(alpha.code()).isEqualTo("A");
        assertThat(alpha.cheminDuGabarit()).isEqualTo("reports/a.jrxml");
        assertThat(alpha.format()).isEqualTo("PDF");
        assertThat(alpha.description()).isNull();
        assertThat(alpha.actif()).isFalse();
        assertThat(modeles().lister(E, null)).extracting(ModeleDuCatalogue::id).containsExactly(N3);
    }

    @Test
    void should_search_a_page_of_models_by_label() {
        modeles().creer(N1, definition(D, "A", "Alpha", "CUSTOM", true));
        modeles().creer(N2, definition(D, "B", "Beta", "CUSTOM", true));
        modeles().creer(N3, definition(D, "C", "Charlie", "ATTESTATION", true));

        assertThat(modeles().rechercher(RechercheDeModeles.de(D, null, 0, 2))).extracting(ModeleDuCatalogue::id).containsExactly(N1, N2);
        assertThat(modeles().rechercher(RechercheDeModeles.de(D, null, 1, 2))).extracting(ModeleDuCatalogue::id).containsExactly(N3);
        assertThat(modeles().rechercher(RechercheDeModeles.de(D, null, 5, 2))).isEmpty();
        assertThat(modeles().rechercher(RechercheDeModeles.de(D, "CUSTOM", 0, 10))).extracting(ModeleDuCatalogue::id).containsExactly(N1, N2);
        assertThat(modeles().rechercher(RechercheDeModeles.de(E, null, 0, 10))).isEmpty();
    }

    @Test
    void should_refuse_a_code_already_taken_in_the_centre_or_an_identifier_already_given_but_not_the_same_code_elsewhere() {
        modeles().creer(N1, definition(D, "A", "Alpha", "CUSTOM", true));

        assertThatThrownBy(() -> modeles().creer(N2, definition(D, "A", "Autre", "CUSTOM", true))).isInstanceOf(ModeleEnDoublon.class);
        assertThatThrownBy(() -> modeles().creer(N1, definition(D, "Z", "Autre", "CUSTOM", true))).isInstanceOf(ModeleEnDoublon.class);
        modeles().creer(N2, definition(E, "A", "Alpha E", "CUSTOM", true));

        assertThat(modeles().lister(D, null)).hasSize(1);
        assertThat(modeles().lister(E, null)).hasSize(1);
    }

    @Test
    void should_replace_the_definition_of_a_model_of_the_centre_only() {
        modeles().creer(N1, definition(D, "A", "Alpha", "CUSTOM", true));
        modeles().creer(N2, definition(D, "B", "Beta", "CUSTOM", true));
        modeles().creer(N3, definition(E, "C", "Charlie", "CUSTOM", true));

        modeles().modifier(N1, DefinitionDeModele.definir(D, "A2", "Alpha 2", "ATTESTATION", "reports/a2.jrxml", "EXCEL", "Texte", false));
        modeles().modifier(N3, definition(D, "X", "Pirate", "CUSTOM", true));
        modeles().modifier(N4, definition(D, "Y", "Inconnu", "CUSTOM", true));

        ModeleDuCatalogue modifie = modeles().lister(D, "ATTESTATION").get(0);
        assertThat(modifie.id()).isEqualTo(N1);
        assertThat(modifie.code()).isEqualTo("A2");
        assertThat(modifie.libelle()).isEqualTo("Alpha 2");
        assertThat(modifie.cheminDuGabarit()).isEqualTo("reports/a2.jrxml");
        assertThat(modifie.format()).isEqualTo("EXCEL");
        assertThat(modifie.description()).isEqualTo("Texte");
        assertThat(modifie.actif()).isFalse();
        assertThat(modeles().lister(E, null).get(0).code()).isEqualTo("C");
        assertThat(modeles().lister(D, null)).hasSize(2);
    }

    @Test
    void should_refuse_to_rename_a_model_to_the_code_of_another_model_of_the_centre() {
        modeles().creer(N1, definition(D, "A", "Alpha", "CUSTOM", true));
        modeles().creer(N2, definition(D, "B", "Beta", "CUSTOM", true));

        assertThatThrownBy(() -> modeles().modifier(N2, definition(D, "A", "Beta", "CUSTOM", true))).isInstanceOf(ModeleEnDoublon.class);
        modeles().modifier(N2, definition(D, "B", "Beta bis", "CUSTOM", true));

        assertThat(modeles().lister(D, null)).extracting(ModeleDuCatalogue::libelle).containsExactly("Alpha", "Beta bis");
    }

    @Test
    void should_delete_a_model_of_the_centre_and_ignore_an_unknown_or_foreign_one() {
        modeles().creer(N1, definition(D, "A", "Alpha", "CUSTOM", true));
        modeles().creer(N2, definition(E, "B", "Beta", "CUSTOM", true));

        modeles().supprimer(D, N2);
        modeles().supprimer(D, N4);
        assertThat(modeles().lister(E, null)).hasSize(1);

        modeles().supprimer(D, N1);
        assertThat(modeles().lister(D, null)).isEmpty();
    }

    @Test
    void should_give_the_type_of_a_model_of_any_centre() {
        donnees();

        assertThat(modeles().typeDuModele(DE_E)).contains("LISTE_PATIENTS");
        assertThat(modeles().typeDuModele(RECENT)).contains("liste_patients");
        assertThat(modeles().typeDuModele(INCONNU)).isEmpty();
    }
}
