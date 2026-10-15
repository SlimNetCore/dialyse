package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.doubles.ReglesTvaEnMemoire;
import com.hemodialyse.backend.contexts.comptabilite.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReglesDeTvaCasDUsageTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final CentreId AUTRE_CENTRE = new CentreId(UUID.randomUUID());

    private final ReglesTvaEnMemoire regles = new ReglesTvaEnMemoire();
    private final ListerLesReglesDeTva lister = new ListerLesReglesDeTva(regles, new UniteDeTravailDirecte());
    private final EnregistrerUneRegleDeTva enregistrer = new EnregistrerUneRegleDeTva(regles, new UniteDeTravailDirecte());
    private final ConsulterLeParametrageFiscal fiscal = new ConsulterLeParametrageFiscal(regles);

    private static LocalDate jour(String date) {
        return LocalDate.parse(date);
    }

    private static EnregistrerUneRegleDeTva.Commande commande(String type, String taux, String debut, String fin) {
        return new EnregistrerUneRegleDeTva.Commande(type, new BigDecimal(taux), false, jour(debut), fin == null ? null : jour(fin),
                "Loi");
    }

    private ReglesTvaEnMemoire enBase(CentreId centre, String type, String taux, boolean exonere, String debut, String fin,
                                      boolean active) {
        return regles.enBase(RegleTva.reconstituee(centre, type, new BigDecimal(taux), exonere, jour(debut),
                fin == null ? null : jour(fin), null, active));
    }

    // ------------------------------------------------------------------ enregistrement

    @Test
    void une_regle_enregistree_s_ajoute_au_centre_active() {
        enregistrer.executer(CENTRE, commande("HEMODIALYSE", "19.00", "2026-01-01", null));

        assertThat(regles.duCentre(CENTRE)).singleElement().satisfies(r -> {
            assertThat(r.active()).isTrue();
            assertThat(r.texteReference()).isEqualTo("Loi");
            assertThat(r.centre()).isEqualTo(CENTRE);
        });
        assertThat(regles.duCentre(AUTRE_CENTRE)).isEmpty();
    }

    @Test
    void une_regle_dont_la_fin_precede_le_debut_est_refusee_sans_rien_ajouter() {
        assertThatThrownBy(() -> enregistrer.executer(CENTRE, commande("HEMODIALYSE", "19", "2026-06-01", "2026-01-01")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("précède la date de début");
        assertThat(regles.nombre()).isZero();
    }

    @Test
    void une_regle_qui_chevauche_une_regle_active_du_meme_type_est_refusee() {
        enregistrer.executer(CENTRE, commande("HEMODIALYSE", "19", "2026-01-01", null));

        assertThatThrownBy(() -> enregistrer.executer(CENTRE, commande("HEMODIALYSE", "9", "2026-06-01", "2027-06-30")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("chevauche la règle existante");
        assertThat(regles.nombre()).isEqualTo(1);
    }

    @Test
    void la_regle_successeuse_est_acceptee_quand_l_ancienne_est_fermee() {
        enregistrer.executer(CENTRE, commande("HEMODIALYSE", "19", "2024-01-01", "2025-12-31"));
        enregistrer.executer(CENTRE, commande("HEMODIALYSE", "9", "2026-01-01", null));

        assertThat(regles.nombre()).isEqualTo(2);
    }

    @Test
    void les_regles_d_un_autre_centre_ou_d_un_autre_type_ou_desactivees_ne_bloquent_pas() {
        enBase(AUTRE_CENTRE, "HEMODIALYSE", "19", false, "2026-01-01", null, true);
        enBase(CENTRE, "MEDICAMENT", "5", false, "2026-01-01", null, true);
        enBase(CENTRE, "HEMODIALYSE", "7", false, "2026-01-01", null, false);

        enregistrer.executer(CENTRE, commande("HEMODIALYSE", "19", "2026-01-01", null));

        assertThat(regles.duCentre(CENTRE)).hasSize(3);
    }

    @Test
    void les_regles_deja_en_base_qui_se_chevauchent_ne_sont_pas_revalidees() {
        enBase(CENTRE, "HEMODIALYSE", "19", false, "2026-01-01", null, true);
        enBase(CENTRE, "HEMODIALYSE", "9", false, "2026-01-01", null, true);

        assertThat(lister.executer(CENTRE)).hasSize(2);
    }

    @Test
    void les_donnees_invalides_sont_refusees_par_le_domaine() {
        assertThatThrownBy(() -> enregistrer.executer(CENTRE, commande("HEMODIALYSE", "-1", "2026-01-01", null)))
                .hasMessage("Le taux TVA ne peut pas être négatif");
        assertThatThrownBy(() -> enregistrer.executer(CENTRE, commande("  ", "19", "2026-01-01", null)))
                .hasMessage("Le type de prestation est obligatoire");
        assertThat(regles.nombre()).isZero();
    }

    // ------------------------------------------------------------------ liste

    @Test
    void la_liste_va_de_la_plus_recente_a_la_plus_ancienne_avec_les_regles_desactivees_et_les_autres_types() {
        enBase(CENTRE, "HEMODIALYSE", "19", false, "2024-01-01", "2025-12-31", true);
        enBase(CENTRE, "HEMODIALYSE", "9", false, "2026-01-01", null, true);
        enBase(CENTRE, "MEDICAMENT", "0", true, "2025-06-01", null, true);
        enBase(CENTRE, "HEMODIALYSE", "7", false, "2023-01-01", "2023-12-31", false);
        enBase(AUTRE_CENTRE, "HEMODIALYSE", "19", false, "2026-01-01", null, true);

        assertThat(lister.executer(CENTRE)).extracting(RegleTvaVue::dateDebutValidite)
                .containsExactly(jour("2026-01-01"), jour("2025-06-01"), jour("2024-01-01"), jour("2023-01-01"));
        assertThat(lister.executer(CENTRE).get(1)).isEqualTo(new RegleTvaVue("MEDICAMENT", new BigDecimal("0"), true,
                jour("2025-06-01"), null));
    }

    // ------------------------------------------------------------------ règle applicable

    @Test
    void la_regle_applicable_est_la_derniere_commencee_bornes_incluses() {
        enBase(CENTRE, "HEMODIALYSE", "19", false, "2024-01-01", null, true);
        enBase(CENTRE, "HEMODIALYSE", "9", false, "2026-01-01", "2026-06-30", true);

        assertThat(fiscal.regleApplicable(CENTRE.valeur(), "HEMODIALYSE", jour("2026-01-01"))).get()
                .extracting("taux").isEqualTo(new BigDecimal("9"));
        assertThat(fiscal.regleApplicable(CENTRE.valeur(), "HEMODIALYSE", jour("2026-06-30"))).get()
                .extracting("taux").isEqualTo(new BigDecimal("9"));
        assertThat(fiscal.regleApplicable(CENTRE.valeur(), "HEMODIALYSE", jour("2025-12-31"))).get()
                .extracting("taux").isEqualTo(new BigDecimal("19"));
    }

    @Test
    void une_regle_recente_terminee_laisse_revenir_une_ancienne_restee_ouverte() {
        enBase(CENTRE, "HEMODIALYSE", "19", false, "2024-01-01", null, true);
        enBase(CENTRE, "HEMODIALYSE", "9", false, "2026-01-01", "2026-06-30", true);

        assertThat(fiscal.regleApplicable(CENTRE.valeur(), "HEMODIALYSE", jour("2026-09-01"))).get()
                .extracting("taux").isEqualTo(new BigDecimal("19"));
    }

    @Test
    void aucune_regle_applicable_avant_le_debut_pour_un_autre_type_un_autre_centre_ou_une_regle_desactivee() {
        enBase(CENTRE, "HEMODIALYSE", "19", false, "2026-01-01", null, true);
        enBase(CENTRE, "MEDICAMENT", "5", false, "2020-01-01", null, false);

        assertThat(fiscal.regleApplicable(CENTRE.valeur(), "HEMODIALYSE", jour("2025-12-31"))).isEmpty();
        assertThat(fiscal.regleApplicable(CENTRE.valeur(), "hemodialyse", jour("2026-02-01"))).isEmpty();
        assertThat(fiscal.regleApplicable(AUTRE_CENTRE.valeur(), "HEMODIALYSE", jour("2026-02-01"))).isEmpty();
        assertThat(fiscal.regleApplicable(CENTRE.valeur(), "MEDICAMENT", jour("2026-02-01"))).isEmpty();
    }

    @Test
    void une_regle_exoneree_est_publiee_avec_son_taux_saisi_et_toutes_ses_dates() {
        regles.enBase(RegleTva.reconstituee(CENTRE, "HEMODIALYSE", new BigDecimal("19.00"), true, jour("2026-01-01"),
                jour("2026-12-31"), "Article 13", true));

        var publiee = fiscal.regleApplicable(CENTRE.valeur(), "HEMODIALYSE", jour("2026-05-01")).orElseThrow();

        assertThat(publiee.exonere()).isTrue();
        assertThat(publiee.taux()).isEqualByComparingTo("19");
        assertThat(publiee.debutValidite()).isEqualTo(jour("2026-01-01"));
        assertThat(publiee.finValidite()).isEqualTo(jour("2026-12-31"));
        assertThat(publiee.texteReference()).isEqualTo("Article 13");
        assertThat(publiee.typePrestation()).isEqualTo("HEMODIALYSE");
    }
}
