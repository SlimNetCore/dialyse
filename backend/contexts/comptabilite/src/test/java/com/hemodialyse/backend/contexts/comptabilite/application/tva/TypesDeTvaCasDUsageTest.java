package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.doubles.ReglesTvaEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.DatesDeValiditeInversees;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaEnChevauchement;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cas d'usage de l'écran « Types de TVA » (slice F1, ADR 0030) contre le double en mémoire.
 */
class TypesDeTvaCasDUsageTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());

    private final ReglesTvaEnMemoire regles = new ReglesTvaEnMemoire();
    private final GererLesTypesDeTva types = new GererLesTypesDeTva(regles, regles, new UniteDeTravailDirecte());

    private static LocalDate jour(String date) {
        return LocalDate.parse(date);
    }

    private static GererLesTypesDeTva.Saisie saisie(String libelle, String taux, String debut, String fin) {
        return new GererLesTypesDeTva.Saisie(libelle, "HEMODIALYSE", new BigDecimal(taux), false, jour(debut),
                fin == null ? null : jour(fin), null);
    }

    private RegleTva enBase(CentreId centre, String debut, String fin, boolean active) {
        RegleTva r = ReglesTvaEnMemoire.regle(centre, "HEMODIALYSE", "7.00", false, jour(debut), fin == null ? null : jour(fin),
                null, active);
        regles.enBase(r);
        return r;
    }

    // ------------------------------------------------------------------ création

    @Test
    void un_type_cree_est_actif_porte_son_libelle_et_son_auteur() {
        TypeDeTvaVue cree = types.creer(CENTRE, saisie("TVA hémodialyse", "19.00", "2026-01-01", null), "admin");

        assertThat(cree.libelle()).isEqualTo("TVA hémodialyse");
        assertThat(cree.actif()).isTrue();
        assertThat(cree.creePar()).isEqualTo("admin");
        assertThat(cree.centre()).isEqualTo(CENTRE.valeur());
        assertThat(regles.duCentre(CENTRE)).hasSize(1);
    }

    @Test
    void la_creation_accepte_une_fin_egale_au_debut_et_refuse_une_fin_anterieure() {
        types.creer(CENTRE, saisie("T", "19", "2026-01-01", "2026-01-01"), "admin");

        assertThatThrownBy(() -> types.creer(CENTRE, saisie("T", "19", "2026-03-02", "2026-03-01"), "admin"))
                .isInstanceOf(DatesDeValiditeInversees.class);
        assertThat(regles.nombre()).isEqualTo(1);
    }

    @Test
    void la_creation_refuse_un_chevauchement_d_un_type_actif_et_un_taux_au_dela_de_cent() {
        types.creer(CENTRE, saisie("T", "19", "2026-01-01", null), "admin");

        assertThatThrownBy(() -> types.creer(CENTRE, saisie("T", "9", "2026-06-01", "2026-12-31"), "admin"))
                .isInstanceOf(RegleTvaEnChevauchement.class);
        assertThatThrownBy(() -> types.creer(CENTRE, saisie("T", "100.01", "2020-01-01", "2020-12-31"), "admin"))
                .hasMessage("Le taux TVA ne peut pas dépasser 100%");
        assertThat(regles.nombre()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ lecture

    @Test
    void la_liste_est_paginee_triee_par_debut_decroissant_et_limitee_au_centre() {
        enBase(CENTRE, "2024-01-01", "2024-12-31", true);
        enBase(CENTRE, "2026-01-01", null, true);
        enBase(CENTRE, "2023-01-01", "2023-12-31", false);
        enBase(AUTRE_CENTRE, "2026-01-01", null, true);

        var page = types.lister(CENTRE, 1, 2);

        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).extracting(TypeDeTvaVue::dateDebutValidite).containsExactly(jour("2023-01-01"));
    }

    // ------------------------------------------------------------------ modification

    @Test
    void la_modification_garde_l_etat_desactive_et_l_auteur_d_origine() {
        RegleTva desactivee = enBase(CENTRE, "2023-01-01", "2023-12-31", false);

        TypeDeTvaVue modifie = types.modifier(CENTRE, desactivee.id(), saisie("Modifié", "9.00", "2023-01-01", "2023-12-31"))
                .orElseThrow();

        assertThat(modifie.actif()).isFalse();
        assertThat(modifie.libelle()).isEqualTo("Modifié");
        assertThat(modifie.taux()).isEqualByComparingTo("9");
        assertThat(regles.auteur(desactivee.id())).isEqualTo("comptabilite");
    }

    @Test
    void la_modification_d_un_type_inconnu_ou_d_un_autre_centre_ne_rend_rien_et_n_ecrit_rien() {
        RegleTva deA = enBase(CENTRE, "2026-01-01", null, true);

        assertThat(types.modifier(CENTRE, new RegleTvaId(UUID.randomUUID()), saisie("X", "5", "2026-01-01", null))).isEmpty();
        assertThat(types.modifier(AUTRE_CENTRE, deA.id(), saisie("Pris", "5", "2026-01-01", null))).isEmpty();
        assertThat(regles.nombre()).isEqualTo(1);
        assertThat(regles.duCentre(CENTRE, deA.id()).orElseThrow().taux().valeur()).isEqualByComparingTo("7");
    }

    @Test
    void la_modification_ne_se_bloque_pas_elle_meme_mais_respecte_les_autres_types_actifs() {
        RegleTva ancienne = enBase(CENTRE, "2024-01-01", "2024-12-31", true);
        RegleTva recente = enBase(CENTRE, "2026-01-01", null, true);

        assertThat(types.modifier(CENTRE, recente.id(), saisie("Avancée", "19", "2025-01-01", null))).isPresent();
        assertThatThrownBy(() -> types.modifier(CENTRE, recente.id(), saisie("Trop tôt", "19", "2024-06-01", null)))
                .isInstanceOf(RegleTvaEnChevauchement.class);
        assertThat(regles.duCentre(CENTRE, recente.id()).orElseThrow().debutValidite()).isEqualTo(jour("2025-01-01"));
        assertThat(regles.duCentre(CENTRE, ancienne.id()).orElseThrow().active()).isTrue();
    }

    // ------------------------------------------------------------------ désactivation

    @Test
    void la_desactivation_est_idempotente_et_limitee_au_centre() {
        RegleTva r = enBase(CENTRE, "2026-01-01", null, true);

        types.desactiver(AUTRE_CENTRE, r.id());
        assertThat(regles.duCentre(CENTRE, r.id()).orElseThrow().active()).isTrue();

        types.desactiver(CENTRE, r.id());
        types.desactiver(CENTRE, r.id());
        types.desactiver(CENTRE, new RegleTvaId(UUID.randomUUID()));
        assertThat(regles.duCentre(CENTRE, r.id()).orElseThrow().active()).isFalse();
    }
}

