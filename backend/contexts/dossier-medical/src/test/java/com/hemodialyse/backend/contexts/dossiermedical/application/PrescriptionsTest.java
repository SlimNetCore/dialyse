package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.application.CatalogueDesArticles.ArticleDeTraitement;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.PrescriptionsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ContenuDePrescription;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PrescriptionIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Traitement;
import com.hemodialyse.backend.contexts.dossiermedical.domain.UniteDeFrequence;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurHorsBorne;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Cas d'usage des prescriptions, avec les doubles en mémoire des ports (le Stock et les notifications sont simulés). */
class PrescriptionsTest {

    private static final CentreId CENTRE = new CentreId(UUID.fromString("00000000-0000-0000-0000-00000000c001"));
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ARTICLE_EPO = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    private static final UUID ARTICLE_SANS_DOSAGE = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

    private final PrescriptionsEnMemoire prescriptions = new PrescriptionsEnMemoire();
    private final List<String> signaux = new ArrayList<>();
    private final Map<UUID, ArticleDeTraitement> articles = new HashMap<>(Map.of(ARTICLE_EPO, new ArticleDeTraitement(ARTICLE_EPO, "EPO-FICT", "Epo fictive"),
            ARTICLE_SANS_DOSAGE, new ArticleDeTraitement(ARTICLE_SANS_DOSAGE, "EPO-SANS", "Epo sans dosage")));
    private int sequence;
    // 31/12/2025 23:30 UTC = 01/01/2026 00:30 à Alger.
    private final HorlogeDuCentre horloge = new HorlogeDuCentre(Clock.fixed(Instant.parse("2025-12-31T23:30:00Z"), ZoneOffset.UTC), ZoneId.of("Africa/Algiers"));
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
            return Optional.ofNullable(articles.get(article));
        }

        @Override
        public java.math.BigDecimal quantitePourLaDose(CentreId centre, UUID article, java.math.BigDecimal dose, String unite) {
            return dose;
        }

        @Override
        public void verifierLaConversionDeLaDose(CentreId centre, UUID article, int dose, String unite) {
            if (article.equals(ARTICLE_SANS_DOSAGE)) throw new RegleDeGestionViolee("ARTICLE_DOSAGE_NON_DEFINI", "dosage manquant");
        }
    };
    private final PrescrireUnTraitement prescrire = new PrescrireUnTraitement(session, (centre, patient) -> PATIENT.equals(patient.valeur()),
            prescriptions, catalogue, unite, () -> new UUID(0, ++sequence), horloge, (centre, patient) -> signaux.add(centre + "/" + patient));
    private final SupprimerUnePrescription supprimer = new SupprimerUnePrescription(session, prescriptions, unite,
            (centre, patient) -> signaux.add(centre + "/" + patient));
    private final ConsulterLesPrescriptionsDuPatient consulter = new ConsulterLesPrescriptionsDuPatient(session, prescriptions, catalogue);
    private final ConsulterLaPrescriptionEnVigueur enVigueur = new ConsulterLaPrescriptionEnVigueur(session, prescriptions, catalogue, horloge);

    private static ContenuDePrescription contenu(LocalDate date, int qb, Traitement epo) {
        return new ContenuDePrescription(date, null, qb, null, null, null, null, null, null, epo, null);
    }

    @Test
    void la_date_absente_est_le_jour_du_centre_et_le_centre_est_prevenu() {
        var p = prescrire.creer(null, PATIENT, contenu(null, 300, null));
        assertThat(p.contenu().date()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(signaux).hasSize(1);
    }

    @Test
    void un_patient_inconnu_du_centre_est_refuse_sans_rien_enregistrer_ni_signaler() {
        assertThatThrownBy(() -> prescrire.creer(null, UUID.randomUUID(), contenu(null, 300, null))).isInstanceOf(PatientIntrouvable.class);
        assertThat(consulter.lister(null, PATIENT, null, null, 0, 20).total()).isZero();
        assertThat(signaux).isEmpty();
    }

    @Test
    void la_dose_d_un_article_connu_doit_se_convertir_mais_un_article_inconnu_ou_une_dose_nulle_est_ignore() {
        var sansDosage = new Traitement(ARTICLE_SANS_DOSAGE, 8000, "SC", 1, UniteDeFrequence.SEMAINE);
        assertThatThrownBy(() -> prescrire.creer(null, PATIENT, contenu(LocalDate.of(2026, 1, 2), 300, sansDosage)))
                .isInstanceOf(RegleDeGestionViolee.class).extracting("code").isEqualTo("ARTICLE_DOSAGE_NON_DEFINI");
        prescrire.creer(null, PATIENT, contenu(LocalDate.of(2026, 1, 2), 300, new Traitement(ARTICLE_SANS_DOSAGE, 0, null, null, null)));
        prescrire.creer(null, PATIENT, contenu(LocalDate.of(2026, 1, 3), 300, new Traitement(UUID.randomUUID(), 8000, null, null, null)));
        assertThat(consulter.lister(null, PATIENT, null, null, 0, 20).total()).isEqualTo(2);
    }

    @Test
    void modifier_une_prescription_inconnue_ou_d_un_autre_patient_est_refuse_sans_creation_ni_reaffectation() {
        var p = prescrire.creer(null, PATIENT, contenu(LocalDate.of(2026, 1, 2), 300, null));
        assertThatThrownBy(() -> prescrire.modifier(null, PATIENT, UUID.randomUUID(), contenu(null, 1, null))).isInstanceOf(PrescriptionIntrouvable.class);
        assertThatThrownBy(() -> prescrire.modifier(null, UUID.randomUUID(), p.id(), contenu(null, 1, null))).isInstanceOf(PrescriptionIntrouvable.class);
        assertThat(consulter.lister(null, PATIENT, null, null, 0, 20).total()).isEqualTo(1);
        prescrire.modifier(null, PATIENT, p.id(), contenu(LocalDate.of(2026, 1, 9), 350, null));
        assertThat(consulter.lister(null, PATIENT, null, null, 0, 20).items().get(0).prescription().contenu().qbCible()).isEqualTo(350);
    }

    @Test
    void la_suppression_sans_correspondance_ne_fait_rien_mais_previent_le_centre() {
        supprimer.executer(null, PATIENT, UUID.randomUUID());
        assertThat(signaux).hasSize(1);
    }

    @Test
    void la_liste_normalise_ses_bornes_et_la_recherche_les_exige() {
        prescrire.creer(null, PATIENT, contenu(LocalDate.of(2026, 1, 2), 300, null));
        assertThat(consulter.lister(null, PATIENT, null, null, -1, 0).taille()).isEqualTo(20);
        assertThat(consulter.lister(null, PATIENT, null, null, 0, 1000).taille()).isEqualTo(200);
        assertThat(consulter.rechercher(CENTRE.valeur(), PATIENT, 0, 200, null, null).total()).isEqualTo(1);
        for (Integer[] hors : new Integer[][]{{null, 2}, {0, null}, {-1, 2}, {0, 0}, {0, 201}}) {
            assertThatThrownBy(() -> consulter.rechercher(CENTRE.valeur(), PATIENT, hors[0], hors[1], null, null)).isInstanceOf(ValeurHorsBorne.class);
        }
        assertThatThrownBy(() -> consulter.rechercher(null, PATIENT, 0, 2, null, null)).isInstanceOf(ValeurHorsBorne.class);
        assertThatThrownBy(() -> consulter.rechercher(CENTRE.valeur(), null, 0, 2, null, null)).isInstanceOf(ValeurHorsBorne.class);
    }

    @Test
    void la_prescription_en_vigueur_est_la_plus_recente_dont_la_date_n_est_pas_posterieure() {
        prescrire.creer(null, PATIENT, contenu(LocalDate.of(2025, 12, 2), 301, null));
        prescrire.creer(null, PATIENT, contenu(LocalDate.of(2025, 12, 20), 302, null));
        prescrire.creer(null, PATIENT, contenu(LocalDate.of(2026, 1, 20), 303, null));
        assertThat(enVigueur.executer(null, PATIENT, LocalDate.of(2025, 12, 1))).isEmpty();
        assertThat(enVigueur.executer(null, PATIENT, LocalDate.of(2025, 12, 25)).orElseThrow().prescription().contenu().qbCible()).isEqualTo(302);
        // Sans date : le jour du centre (01/01/2026) : la prescription du 20/01/2026 n'est pas encore en vigueur.
        assertThat(enVigueur.executer(null, PATIENT, null).orElseThrow().prescription().contenu().qbCible()).isEqualTo(302);
        assertThat(enVigueur.executer(null, new PatientId(UUID.randomUUID()).valeur(), null)).isEmpty();
    }

    @Test
    void le_code_et_le_libelle_des_articles_connus_sont_rendus() {
        prescrire.creer(null, PATIENT, contenu(LocalDate.of(2026, 1, 2), 300, new Traitement(ARTICLE_EPO, 4000, "SC", 1, UniteDeFrequence.SEMAINE)));
        var lue = consulter.lister(null, PATIENT, null, null, 0, 20).items().get(0);
        assertThat(lue.epo()).map(ArticleDeTraitement::code).contains("EPO-FICT");
        assertThat(lue.fer()).isEmpty();
    }
}
