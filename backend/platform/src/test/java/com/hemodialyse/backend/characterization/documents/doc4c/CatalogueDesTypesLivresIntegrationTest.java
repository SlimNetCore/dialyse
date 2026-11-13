package com.hemodialyse.backend.characterization.documents.doc4c;

import com.hemodialyse.backend.contexts.documents.application.CatalogueDesTypesLivres;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DOC4c (ADR 0166) : les 14 types livrés sont déclarés par leur contexte propriétaire, dans l'ordre historique ; tout gabarit livré est
 * déclaré (sinon il ne serait ni provisionné pour les centres ni personnalisable). Remplace le test du catalogue statique de Documents.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class CatalogueDesTypesLivresIntegrationTest {

    @Autowired
    private CatalogueDesTypesLivres catalogue;

    @Test
    void les_quatorze_types_livres_sont_declares_par_leurs_contextes_dans_l_ordre_historique() {
        assertThat(catalogue.types()).extracting(TypeDeDocumentDeclare::code).containsExactly(
                "FICHE_PATIENT", "ATTESTATION", "PEC", "LISTE_PATIENTS", "LISTE_PEC", "LISTE_ATTESTATIONS",
                "SYNTHESE_FACTURATION_MENSUELLE", "ORDONNANCE", "INVENTAIRE_STOCK", "BON_INTERVENTION",
                "PLANNING_PRESENCE_INFIRMIERS", "PLANNING_OPTIMISE", "CAHIER_DIALYSE", "RAPPORT_DIRECTION");
    }

    @Test
    void tout_gabarit_livre_est_declare_et_tout_type_declare_a_son_gabarit() throws Exception {
        Resource[] livres = new PathMatchingResourcePatternResolver().getResources("classpath*:reports/*.jrxml");
        Set<String> livresParChemin = java.util.Arrays.stream(livres).map(r -> "reports/" + r.getFilename()).collect(Collectors.toSet());
        Set<String> declares = catalogue.types().stream().map(TypeDeDocumentDeclare::gabaritParDefaut).collect(Collectors.toSet());

        assertThat(declares).as("gabarits livrés mais absents des types déclarés").containsAll(livresParChemin);
        assertThat(livresParChemin).as("types déclarés sans gabarit livré").containsAll(declares);
        assertThat(catalogue.types()).allSatisfy(t -> assertThat(t.description()).isNotBlank());
    }

    @Test
    void un_type_se_retrouve_sans_casse_ni_espaces() {
        assertThat(catalogue.trouver(" inventaire_stock ")).isPresent();
        assertThat(catalogue.trouver("INCONNU")).isEmpty();
        assertThat(List.of("CUSTOM")).noneMatch(c -> catalogue.trouver(c).isPresent());
    }
}
