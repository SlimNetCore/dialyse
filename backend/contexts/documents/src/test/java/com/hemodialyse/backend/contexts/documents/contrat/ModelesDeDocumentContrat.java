package com.hemodialyse.backend.contexts.documents.contrat;

import com.hemodialyse.backend.contexts.documents.application.ModelesDeDocument;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDeDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void should_give_the_type_of_a_model_of_any_centre() {
        donnees();

        assertThat(modeles().typeDuModele(DE_E)).contains("LISTE_PATIENTS");
        assertThat(modeles().typeDuModele(RECENT)).contains("liste_patients");
        assertThat(modeles().typeDuModele(INCONNU)).isEmpty();
    }
}
