package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.AlerteDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.IndicateursDeLaDirectionFournis.IndicateursDeLaPeriode;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.RapportDeSyntheseDeLaDirection.SourceDuRapport;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.CaisseDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.CaisseDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.PointMensuel;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Repartition;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.RepartitionParAge;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.RepartitionParSexe;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.SocieteDeLaDirection;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.StatistiquesDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.Tranche;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.TableauDeBordDeLaDirection.VueDEnsemble;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Le modèle du rapport de synthèse reflète sa source (KB-DIR-01 : totaux bruts conservés) et n'imprime jamais un effectif masqué. */
class ConstituerLeRapportDeSyntheseTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final LocalDate DEBUT = LocalDate.of(2026, 1, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 3, 31);
    private static final Instant MAINTENANT = Instant.parse("2026-11-09T10:00:00Z");
    private static final HorlogeDuPilotage HORLOGE = new HorlogeDuPilotage(Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    private final List<String> appels = new ArrayList<>();

    private static StatistiquesDuCentre centre(UUID id, String nom, Long patients, long seances) {
        return new StatistiquesDuCentre(id, nom, true, patients, patients, seances, 2L, new BigDecimal("100.00"), new BigDecimal("119.00"),
                new BigDecimal("50.00"), new BigDecimal("69.00"), new BigDecimal("42.02"));
    }

    private static VueDEnsemble vue(List<StatistiquesDuCentre> centres, StatistiquesDuCentre totaux, List<PointMensuel> mensuel) {
        return new VueDEnsemble(SOCIETE, "Société Test", DEBUT, FIN, OffsetDateTime.parse("2026-04-01T00:00:00Z"), 5, centres, totaux,
                mensuel, null);
    }

    private static ConstituerLeRapportDeSynthese cas(TableauDeBordDeLaDirection tableau, IndicateursDeLaDirectionFournis indicateurs) {
        return new ConstituerLeRapportDeSynthese(tableau, indicateurs, HORLOGE);
    }

    private static final IndicateursDeLaDirectionFournis SANS_INDICATEUR = (societe, from, to) -> null;

    private static TableauDeBordDeLaDirection tableauFige(VueDEnsemble vue, Repartition repartition, List<String> appels) {
        return new TableauDeBordDeLaDirection() {
            public SocieteDeLaDirection societe(UUID societeId) {
                throw new UnsupportedOperationException();
            }

            public VueDEnsemble vueDEnsemble(UUID societeId, LocalDate from, LocalDate to) {
                appels.add("vue:" + from + ":" + to);
                return vue;
            }

            public Repartition repartition(UUID societeId, LocalDate from, LocalDate to) {
                appels.add("repartition:" + from + ":" + to);
                return repartition;
            }
        };
    }

    @Test
    void le_rapport_en_direct_lit_dans_lordre_la_vue_les_indicateurs_puis_la_repartition_sur_les_bornes_de_la_vue() {
        var vue = vue(List.of(centre(A, "Annaba", 8L, 10)), centre(null, null, 8L, 10), List.of());
        IndicateursDeLaDirectionFournis indicateurs = (societe, from, to) -> {
            appels.add("indicateurs:" + from + ":" + to);
            return null;
        };

        var rapport = cas(tableauFige(vue, null, appels), indicateurs).enDirect(SOCIETE, null, null);

        assertThat(appels).containsExactly("vue:null:null", "indicateurs:2026-01-01:2026-03-31", "repartition:2026-01-01:2026-03-31");
        assertThat(rapport.titre()).isEqualTo("Rapport de direction");
        assertThat(rapport.genereLe().toInstant()).isEqualTo(MAINTENANT);
        assertThat(rapport.debut()).isEqualTo("2026-01-01");
        assertThat(rapport.fin()).isEqualTo("2026-03-31");
        assertThat(rapport.seuilAnonymat()).isEqualTo(5);
    }

    @Test
    void le_rapport_n_imprime_pas_un_total_de_patients_dont_un_centre_est_masque_meme_si_la_source_ancienne_le_porte_kb_dir_01_corrige() {
        // Source d'un instantané figé avant la correction : total brut 7 alors que les deux centres sont masqués.
        var vue = vue(List.of(centre(A, "Annaba", null, 10), centre(B, "Blida", null, 5)), centre(null, null, 7L, 15), List.of());

        var rapport = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("Instantané", MAINTENANT.atOffset(ZoneOffset.UTC), vue, null, null));

        assertThat(rapport.finances().centres()).extracting(l -> l.patients()).containsExactly(null, null);
        assertThat(rapport.synthese().patients()).isNull();
        assertThat(rapport.finances().total().patientsSousKt()).isNull();
        assertThat(rapport.finances().total().patients()).isNull();
        assertThat(rapport.synthese().seances()).isEqualTo(15L);
        assertThat(rapport.titre()).isEqualTo("Instantané");
    }

    @Test
    void le_rapport_garde_un_total_de_patients_compose_de_centres_publies() {
        var vue = vue(List.of(centre(A, "Annaba", 50L, 10), centre(B, "Blida", 5L, 5)), centre(null, null, 55L, 15), List.of());

        var rapport = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("Instantané", MAINTENANT.atOffset(ZoneOffset.UTC), vue, null, null));

        assertThat(rapport.synthese().patients()).isEqualTo(55L);
        assertThat(rapport.finances().total().patients()).isEqualTo(55L);
    }

    @Test
    void sans_indicateurs_ni_repartition_les_sections_absentes_restent_absentes() {
        var vue = vue(List.of(), null, null);

        var rapport = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("t", MAINTENANT.atOffset(ZoneOffset.UTC), vue, null, null));

        assertThat(rapport.synthese()).isNull();
        assertThat(rapport.alertes()).isEmpty();
        assertThat(rapport.evolution()).isEmpty();
        assertThat(rapport.qualite()).isNull();
        assertThat(rapport.stock()).isNull();
        assertThat(rapport.repartitions()).isNull();
        assertThat(rapport.finances().total().nom()).isNull();
    }

    @Test
    void un_code_dalerte_inconnu_est_imprime_tel_quel_et_un_code_connu_par_son_libelle() {
        var alertes = List.of(new AlerteDeLaDirection("Annaba", "STOCK_SOUS_SEUIL", "CRITICAL", new BigDecimal("3")),
                new AlerteDeLaDirection("Blida", "CODE_FUTUR", "WARNING", null), new AlerteDeLaDirection("Blida", null, "WARNING", null));
        var indicateurs = new IndicateursDeLaPeriode(List.of(), null, alertes);

        var rapport = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("t", MAINTENANT.atOffset(ZoneOffset.UTC),
                vue(List.of(), null, List.of()), indicateurs, null));

        assertThat(rapport.alertes()).extracting(a -> a.libelle()).containsExactly("Articles sous le seuil d'alerte", "CODE_FUTUR", "—");
        assertThat(rapport.alertes().getFirst().critique()).isTrue();
        assertThat(rapport.alertes().get(1).critique()).isFalse();
        assertThat(rapport.alertes().get(1).valeur()).isNull();
    }

    @Test
    void levolution_cumule_les_centres_par_mois_et_imprime_un_mois_absent_en_tiret() {
        var mensuel = List.of(new PointMensuel("2026-02", A, 10L, new BigDecimal("100.50"), new BigDecimal("119.00")),
                new PointMensuel("2026-02", B, 5L, new BigDecimal("50.25"), null), new PointMensuel(null, A, null, null, null),
                new PointMensuel("2026-01", A, 1L, BigDecimal.ONE, BigDecimal.ONE));

        var rapport = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("t", MAINTENANT.atOffset(ZoneOffset.UTC),
                vue(List.of(), null, mensuel), null, null));

        assertThat(rapport.evolution()).extracting(m -> m.mois()).containsExactly("2026-01", "2026-02", "—");
        assertThat(rapport.evolution().get(1).seances()).isEqualTo(15);
        assertThat(rapport.evolution().get(1).caHt()).isEqualTo(150.75);
        assertThat(rapport.evolution().get(1).caTtc()).isEqualTo(119.0);
    }

    @Test
    void les_repartitions_gardent_les_effectifs_masques_nuls_et_impriment_les_tranches_dans_lordre_daffichage() {
        var repartition = new Repartition(SOCIETE, DEBUT, FIN, OffsetDateTime.parse("2026-04-01T00:00:00Z"), 5,
                List.of(new RepartitionParSexe(A, "Annaba", null, 9L, 0L), new RepartitionParSexe(B, "Blida", 6L, 0L, 0L)),
                List.of(new RepartitionParAge(A, "Annaba", List.of(new Tranche("60_PLUS", 7L), new Tranche("0_17", null)))),
                List.of(), List.of(), List.of());

        var rapport = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("t", MAINTENANT.atOffset(ZoneOffset.UTC),
                vue(List.of(), null, List.of()), null, repartition));

        var repartitions = rapport.repartitions();
        assertThat(repartitions.sexe()).extracting(s -> s.masculin()).containsExactly(null, 6L);
        assertThat(repartitions.ages().getFirst().tranches().keySet()).containsExactly("0_17", "60_PLUS");
        assertThat(repartitions.ages().getFirst().tranches().get("0_17")).isNull();
        assertThat(repartitions.caisses()).isNull();
    }

    @Test
    void la_matrice_des_caisses_a_une_colonne_par_centre_et_garde_le_masquage_de_la_source() {
        var repartition = new Repartition(SOCIETE, DEBUT, FIN, OffsetDateTime.parse("2026-04-01T00:00:00Z"), 5,
                List.of(new RepartitionParSexe(A, "Annaba", 6L, 0L, 0L), new RepartitionParSexe(B, "Blida", 6L, 0L, 0L)), List.of(),
                List.of(new CaisseDuCentre(A, "Annaba", "CNAS", "CNAS", null, 20L, new BigDecimal("100.00")),
                        new CaisseDuCentre(A, "Annaba", "", "", 9L, 4L, new BigDecimal("10.00"))),
                List.of(new CaisseDeLaSociete("CNAS", "CNAS", 3L, 20L, new BigDecimal("100.00")),
                        new CaisseDeLaSociete("", "", 9L, 4L, new BigDecimal("10.00"))), List.of());

        var caisses = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("t", MAINTENANT.atOffset(ZoneOffset.UTC),
                vue(List.of(), null, List.of()), null, repartition)).repartitions().caisses();

        assertThat(caisses.centres()).containsExactly("Annaba", "Blida");
        assertThat(caisses.lignes()).extracting(l -> l.caisse()).containsExactly("CNAS", "Caisse non renseignée");
        assertThat(caisses.lignes().getFirst().patients()).containsExactly(null, 0L);
        // KB-DIR-01 corrigé : le total d'une caisse dont une cellule est masquée est imprimé masqué (la source ancienne portait 3).
        assertThat(caisses.lignes().getFirst().totalPatients()).isNull();
        assertThat(caisses.lignes().get(1).totalPatients()).isEqualTo(9L);
        assertThat(caisses.lignes().get(1).patients()).containsExactly(9L, 0L);
        assertThat(caisses.lignes().get(1).seances()).containsExactly(4L, 0L);
    }

    @Test
    void le_total_clinique_sans_effectif_masque_dans_un_centre_est_imprime_masque_champ_par_champ_kb_med_104_corrige() {
        var centreA = new IndicateursDeLaDirectionFournis.IndicateursDuCentre("Annaba",
                new IndicateursDeLaDirectionFournis.Clinique(null, null, null, null, null, 50L, 8L, 0L, 5L, null, null, null), null);
        var centreB = new IndicateursDeLaDirectionFournis.IndicateursDuCentre("Blida",
                new IndicateursDeLaDirectionFournis.Clinique(null, null, null, null, null, null, 6L, 0L, 5L, null, null, null), null);
        // Source ancienne : totaux bruts 52 (vhb) alors que Blida est masque ; vhc 14 reste publiable.
        var totaux = new IndicateursDeLaDirectionFournis.IndicateursDuCentre(null,
                new IndicateursDeLaDirectionFournis.Clinique(null, null, null, null, null, 52L, 14L, 0L, 10L, null, null, null), null);
        var indicateurs = new IndicateursDeLaDirectionFournis.IndicateursDeLaPeriode(List.of(centreA, centreB), totaux, List.of());

        var qualite = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("t", MAINTENANT.atOffset(ZoneOffset.UTC),
                vue(List.of(), null, List.of()), indicateurs, null)).qualite();

        assertThat(qualite.total().vhbPositifs()).isNull();
        assertThat(qualite.total().vhcPositifs()).isEqualTo(14L);
        assertThat(qualite.total().vihPositifs()).isEqualTo(0L);
        assertThat(qualite.total().patientsObservanceEnRetard()).isEqualTo(10L);
    }

    @Test
    void une_source_figee_aux_champs_absents_est_constituee_sans_erreur() {
        var repartition = new Repartition(SOCIETE, DEBUT, FIN, null, 5, null, null, null, null, null);
        var vue = new VueDEnsemble(SOCIETE, null, null, null, null, null, null, null, null, null);

        var rapport = cas(null, SANS_INDICATEUR).constituer(new SourceDuRapport("t", null, vue, null, repartition));

        assertThat(rapport.debut()).isNull();
        assertThat(rapport.finances()).isNull();
        assertThat(rapport.evolution()).isEmpty();
        assertThat(rapport.repartitions().sexe()).isEmpty();
        assertThat(rapport.repartitions().ages()).isEmpty();
        assertThat(rapport.repartitions().anemie()).isEmpty();
        assertThat(rapport.repartitions().caisses()).isNull();
    }
}
