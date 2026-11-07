package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles.ArticleDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.application.SaisirUneAdministration.Saisie;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AdministrationsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.NotificationsDeLAnemie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** La saisie d'une administration, avec les doubles en mémoire des ports (la preuve transactionnelle réelle est dans {@code SaisieEtStockTransactionTest}). */
class SaisirUneAdministrationTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ARTICLE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID SEANCE = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final LocalDate JOUR = LocalDate.of(2026, 1, 5);

    private final AdministrationsEnMemoire administrations = new AdministrationsEnMemoire();
    private final List<String> ordre = new ArrayList<>();
    private final List<BigDecimal> sorties = new ArrayList<>();
    private final List<LocalDate> joursDeSortie = new ArrayList<>();
    private final List<LocalDate> signaux = new ArrayList<>();
    private boolean stockRefuse;
    private int sequence;

    private final SessionCourante session = demande -> CENTRE;
    private final UniteDeTravail unite = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T independante(Supplier<T> action) {
            return action.get();
        }
    };
    private final CatalogueDesArticles catalogue = new CatalogueDesArticles() {
        @Override
        public Optional<ArticleDeTraitement> article(CentreId centre, UUID article) {
            return Optional.of(new ArticleDeTraitement(article, "EPO-FICT", "Epo fictive"));
        }

        @Override
        public void verifierLaConversionDeLaDose(CentreId centre, UUID article, int dose, String unite) {
        }

        @Override
        public BigDecimal quantitePourLaDose(CentreId centre, UUID article, BigDecimal dose, String unite) {
            ordre.add("conversion");
            return dose.divide(new BigDecimal("4000"));
        }
    };
    private final SortiesDeStockDesAdministrations stock = (centre, seance, patient, jour, article, quantite, auteur) -> {
        ordre.add("sortie");
        if (stockRefuse) throw new RegleDeGestionViolee("SEANCE_FACTUREE_NON_MODIFIABLE", "La seance facturee ne peut plus etre modifiee");
        sorties.add(quantite);
        joursDeSortie.add(jour);
    };
    private final NotificationsDeLAnemie notifications = new NotificationsDeLAnemie() {
        @Override
        public void administrationSaisie(UUID centre, UUID patient, LocalDate jour) {
            signaux.add(jour);
        }

        @Override
        public void observanceNonRespectee(UUID centre, UUID patient, String message, Map<String, String> details) {
        }
    };
    // Le jour du centre n'a pas d'importance ici : on contrôle seulement que la sortie de stock est datée du jour de l'horloge.
    private final HorlogeDuCentre horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-09T10:00:00Z"), ZoneOffset.UTC));
    private final SaisirUneAdministration saisir = new SaisirUneAdministration(session, (centre, patient) -> PATIENT.equals(patient.valeur()), administrations,
            catalogue, stock, unite, () -> new UUID(0, ++sequence), horloge, notifications);

    private static Saisie effective(UUID article, UUID seance, BigDecimal dose) {
        return new Saisie(null, "EPO", "Epoetine", dose, dose == null ? null : "UI", "SC", JOUR, seance, "inf", true, null, article, null);
    }

    @Test
    void une_administration_effective_avec_article_et_seance_deduit_la_quantite_de_la_fiche_sort_du_stock_puis_s_enregistre_et_previent() {
        var saisie = new Saisie(null, "EPO", "Epoetine", new BigDecimal("8000"), "UI", "SC", JOUR, SEANCE, "inf", true, null, ARTICLE, new BigDecimal("99"));
        var enregistree = saisir.executer(null, PATIENT, saisie);
        assertThat(ordre).containsExactly("conversion", "sortie");
        assertThat(sorties).singleElement().satisfies(q -> assertThat(q).isEqualByComparingTo("2"));
        assertThat(joursDeSortie).containsExactly(LocalDate.of(2026, 1, 9));
        assertThat(enregistree.quantiteArticle()).isEqualByComparingTo("2");
        assertThat(administrations.lesPlusRecentes(CENTRE, new PatientId(PATIENT), 5)).hasSize(1);
        assertThat(signaux).containsExactly(JOUR);
    }

    @Test
    void un_refus_du_stock_ne_laisse_ni_administration_ni_signal() {
        stockRefuse = true;
        assertThatThrownBy(() -> saisir.executer(null, PATIENT, effective(ARTICLE, SEANCE, new BigDecimal("8000"))))
                .isInstanceOf(RegleDeGestionViolee.class).extracting("code").isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE");
        assertThat(administrations.lesPlusRecentes(CENTRE, new PatientId(PATIENT), 5)).isEmpty();
        assertThat(signaux).isEmpty();
    }

    @Test
    void le_stock_est_interroge_avant_la_validation_de_la_saisie_comme_le_legacy() {
        // Sans dose : la validation refuse (ADMINISTRATION_DOSE_REQUISE) mais la quantité du client suffit à déclencher la sortie de stock, qui passe d'abord.
        var sansDose = new Saisie(null, "EPO", "Epoetine", null, null, "SC", JOUR, SEANCE, "inf", true, null, ARTICLE, new BigDecimal("1"));
        assertThatThrownBy(() -> saisir.executer(null, PATIENT, sansDose)).isInstanceOf(RegleDeGestionViolee.class).extracting("code")
                .isEqualTo("ADMINISTRATION_DOSE_REQUISE");
        assertThat(ordre).containsExactly("sortie");
        assertThat(administrations.lesPlusRecentes(CENTRE, new PatientId(PATIENT), 5)).isEmpty();
        assertThat(signaux).isEmpty();
    }

    @Test
    void une_administration_non_faite_ne_touche_pas_au_stock_et_perd_dose_article_et_quantite() {
        var saisie = new Saisie(null, "FER_INJECTABLE", "Fer", new BigDecimal("100"), "mg", "IV", JOUR, SEANCE, "inf", false, "Patient absent", ARTICLE, new BigDecimal("1"));
        var enregistree = saisir.executer(null, PATIENT, saisie);
        assertThat(ordre).isEmpty();
        assertThat(enregistree.dose()).isNull();
        assertThat(enregistree.article()).isNull();
        assertThat(enregistree.quantiteArticle()).isNull();
        assertThat(signaux).hasSize(1);
    }

    @Test
    void sans_seance_ou_sans_article_aucune_sortie_de_stock() {
        saisir.executer(null, PATIENT, effective(ARTICLE, null, new BigDecimal("4000")));
        saisir.executer(null, PATIENT, effective(null, SEANCE, new BigDecimal("4000")));
        assertThat(sorties).isEmpty();
        assertThat(administrations.lesPlusRecentes(CENTRE, new PatientId(PATIENT), 5)).hasSize(2);
    }

    @Test
    void un_patient_inconnu_du_centre_est_refuse_et_un_type_ou_un_indicateur_absent_ou_inconnu_est_une_saisie_invalide() {
        assertThatThrownBy(() -> saisir.executer(null, UUID.randomUUID(), effective(null, null, new BigDecimal("1")))).isInstanceOf(PatientIntrouvable.class);
        var sansType = new Saisie(null, null, null, BigDecimal.ONE, "UI", null, JOUR, null, null, true, null, null, null);
        var typeInconnu = new Saisie(null, "epo", null, BigDecimal.ONE, "UI", null, JOUR, null, null, true, null, null, null);
        var sansIndicateur = new Saisie(null, "EPO", null, BigDecimal.ONE, "UI", null, JOUR, null, null, null, null, null, null);
        for (Saisie invalide : List.of(sansType, typeInconnu, sansIndicateur)) {
            assertThatThrownBy(() -> saisir.executer(null, PATIENT, invalide)).isInstanceOf(SaisieInvalide.class);
        }
        assertThat(administrations.lesPlusRecentes(CENTRE, new PatientId(PATIENT), 5)).isEmpty();
    }
}
