package com.hemodialyse.backend.contexts.facturation.application.reglement;

import com.hemodialyse.backend.contexts.facturation.application.reglement.pagination.PaginationInvalide;
import com.hemodialyse.backend.contexts.facturation.doubles.ReglementsEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RG-REG-009 : saisie des règlements paginée côté serveur, en mémoire sur les situations calculées — filtres de colonne en ET,
 * tri de la liste blanche (valeurs absentes en dernier, départage stable), découpage avec le total filtré, codes 400 stables.
 */
class ConsulterLesReglementsPagineesTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("99995810-0000-0000-0000-0000000000a1"));
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("99995810-0000-0000-0000-0000000000b1"));
    private static final CriteresDeSituation ANNEE_2026 = new CriteresDeSituation(CENTRE, 2026, null, null, null, null);

    private ConsulterLesReglements consulter;

    @BeforeEach
    void preparer() {
        SituationsDesFacturesFixes situations = new SituationsDesFacturesFixes();
        situations.poser(CENTRE, "FAC-0001", "2026-01-10", "BENALI", "Karim", "CNAS", "1000.00", "0.00", null);
        situations.poser(CENTRE, "FAC-0002", "2026-01-20", "amrani", "Lina", "CASNOS", "500.00", "200.00", "REG-2");
        situations.poser(CENTRE, "FAC-0003", "2026-02-05", "Chikhi", "Nadia", "CNAS", "800.00", "800.00", "REG-3");
        situations.poser(CENTRE, "FAC-0004", "2026-02-05", "Zitouni", "Ali", null, "300.00", "350.00", "REG-4");
        situations.poser(AUTRE_CENTRE, "FAC-B-0001", "2026-01-15", "Autre", "Centre", "CNAS", "900.00", "0.00", null);
        consulter = new ConsulterLesReglements(situations, new ReglementsEnMemoire());
    }

    private PageDeSituations page(Integer page, Integer taille, String tri, Map<String, String> filtres) {
        return consulter.paginer(ANNEE_2026, TriDesSituations.demande(page, taille, tri), FiltresDeSituation.lire(filtres));
    }

    private PageDeSituations triee(String tri) {
        return page(null, null, tri, Map.of());
    }

    private PageDeSituations filtree(Map<String, String> filtres) {
        return page(null, null, null, filtres);
    }

    @Test
    void sans_tri_ni_filtre_la_page_suit_l_ordre_de_la_recherche_du_centre_avec_les_valeurs_par_defaut() {
        PageDeSituations page = page(null, null, null, Map.of());

        assertThat(page.items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0004", "FAC-0003", "FAC-0002", "FAC-0001");
        assertThat(page.total()).isEqualTo(4);
        assertThat(page.page()).isZero();
        assertThat(page.size()).isEqualTo(20);
    }

    @Test
    void le_tri_texte_est_insensible_a_la_casse_et_les_valeurs_absentes_vont_en_dernier_dans_les_deux_sens() {
        assertThat(triee("patientNom").items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0002", "FAC-0001", "FAC-0003", "FAC-0004");
        assertThat(triee("caisse,asc").items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0002", "FAC-0003", "FAC-0001", "FAC-0004");
        assertThat(triee("caisse,DESC").items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0003", "FAC-0001", "FAC-0002", "FAC-0004");
        assertThat(triee("codeReglement,desc").items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0004", "FAC-0003", "FAC-0002", "FAC-0001");
    }

    @Test
    void le_tri_par_montant_est_numerique_et_le_tri_par_etat_suit_l_ordre_de_l_enumeration() {
        assertThat(triee("montantFacture").items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0004", "FAC-0002", "FAC-0003", "FAC-0001");
        assertThat(triee("etat,desc").items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0004", "FAC-0003", "FAC-0002", "FAC-0001");
        assertThat(triee("dateFacturation,asc").items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0001", "FAC-0002", "FAC-0004", "FAC-0003");
    }

    @Test
    void les_filtres_de_colonne_se_combinent_en_et_texte_contient_numerique_avec_operateur_et_enumeration_en_ou() {
        assertThat(filtree(Map.of("patientNom", "  AMR ")).items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0002");
        assertThat(filtree(Map.of("montantFacture", ">=800")).items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0003", "FAC-0001");
        assertThat(filtree(Map.of("montantRegle", "200,00")).items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0002");
        assertThat(filtree(Map.of("etat", "REGLEE,NON_REGLEE")).items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0004", "FAC-0003", "FAC-0001");
        assertThat(filtree(Map.of("soldeType", "TROP_PERCU")).items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0004");
        assertThat(filtree(Map.of("codeReglement", "reg", "caisse", "cnas")).items()).extracting(LigneDeSituation::numeroFacture)
                .containsExactly("FAC-0003");
        assertThat(filtree(Map.of("numeroFacture", " ", "caisse", "")).total()).isEqualTo(4);
    }

    @Test
    void la_page_est_decoupee_apres_filtre_et_tri_et_le_total_est_celui_des_lignes_filtrees() {
        PageDeSituations deuxieme = page(1, 3, null, Map.of());
        assertThat(deuxieme.items()).extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0001");
        assertThat(deuxieme.total()).isEqualTo(4);

        PageDeSituations auDela = page(5, 3, null, Map.of());
        assertThat(auDela.items()).isEmpty();
        assertThat(auDela.total()).isEqualTo(4);

        PageDeSituations filtreePaginee = page(0, 1, "montantFacture,desc", Map.of("soldeType", "RESTE"));
        assertThat(filtreePaginee.items()).extracting(LigneDeSituation::numeroFacture).containsExactly("FAC-0001");
        assertThat(filtreePaginee.total()).isEqualTo(2);
        assertThat(filtreePaginee.size()).isEqualTo(1);
    }

    @Test
    void la_page_ne_contient_jamais_les_factures_d_un_autre_centre() {
        assertThat(filtree(Map.of("patientNom", "autre")).total()).isZero();
    }

    @Test
    void la_pagination_le_tri_et_les_filtres_illisibles_sont_refuses_avec_un_code_stable() {
        refuse(PaginationInvalide.PAGE_INVALIDE, () -> page(-1, null, null, Map.of()));
        refuse(PaginationInvalide.TAILLE_INVALIDE, () -> page(null, 0, null, Map.of()));
        refuse(PaginationInvalide.TAILLE_INVALIDE, () -> page(null, 101, null, Map.of()));
        refuse(PaginationInvalide.TRI_INVALIDE, () -> triee("reste"));
        refuse(PaginationInvalide.TRI_INVALIDE, () -> triee("etat,haut"));
        refuse(PaginationInvalide.TRI_INVALIDE, () -> triee("etat,asc,desc"));
        refuse(PaginationInvalide.TRI_INVALIDE, () -> triee("numeroFacture; DROP TABLE factures"));
        refuse(PaginationInvalide.FILTRE_INVALIDE, () -> filtree(Map.of("montantFacture", "abc")));
        refuse(PaginationInvalide.FILTRE_INVALIDE, () -> filtree(Map.of("montantRegle", "!=0")));
        refuse(PaginationInvalide.FILTRE_INVALIDE, () -> filtree(Map.of("etat", "reglee")));
        refuse(PaginationInvalide.FILTRE_INVALIDE, () -> filtree(Map.of("soldeType", "SOLDE")));
        assertThat(page(null, 100, null, Map.of()).size()).isEqualTo(100);
    }

    private static void refuse(String code, Runnable appel) {
        assertThatThrownBy(appel::run).isInstanceOf(PaginationInvalide.class)
                .satisfies(e -> assertThat(((PaginationInvalide) e).code()).isEqualTo(code));
    }
}
