package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees.CaisseRealiseePubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees.MoisRealisePublie;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees.TensionsDeSeancePubliees;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Contrat commun des ports publiés de la Séance pour la plateforme (ADR 0099, SE7e) : joué par le double en mémoire, l'adapter JDBC sur
 * H2 et sur PostgreSQL 16 jetable. Il décrit les DIX-SEPT requêtes reprises à l'identique (E1 à E17 de {@code .ddd/safety-net/seance-se7e.md},
 * E11, l'état Jasper, excepté : il reste dans le modèle de document). Lot L5a (ADR 0117) : ordre intra-jour déterministe et clés en
 * minuscules (KB-SEA-86), moyennes comparées à 10 décimales (KB-SEA-87 clos), une séance ANNULEE ne bloque plus la reprise (KB-SEA-89).
 * Lot L5b (ADR 0118) : un seul prédicat pour la vue d'ensemble et les caisses (une séance sur une fiche d'un autre centre reste comptée sous la
 * caisse vide : KB-DIR-05, KB-SEA-83), centre payeur borné au centre de la fiche (KB-SEA-84), statistiques des séances RÉALISÉES seulement et
 * volet borné au centre de la séance (KB-SEA-85).
 * Reste voulu (prudence, KB-SEA-90 et 91 CLOS par documentation) : séances du patient et facture sans borne de centre, forfait imposé de
 * n'importe quel centre ou statut. Reste reproduit : casse et blancs du statut (KB-SEA-82).
 */
public abstract class PilotageEtRapportsDesSeancesContrat {

    protected static final UUID CENTRE_A = UUID.fromString("5e7ec000-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("5e7ec000-0000-0000-0000-00000000000b");
    protected static final UUID CENTRE_C = UUID.fromString("5e7ec000-0000-0000-0000-00000000000c");
    private static final UUID PA1 = UUID.fromString("5e7ec001-0000-0000-0000-000000000001");
    private static final UUID PA2 = UUID.fromString("5e7ec001-0000-0000-0000-000000000002");
    private static final UUID PA3 = UUID.fromString("5e7ec001-0000-0000-0000-000000000003");
    private static final UUID PX = UUID.fromString("5e7ec001-0000-0000-0000-000000000004");
    private static final UUID PB = UUID.fromString("5e7ec001-0000-0000-0000-00000000000b");
    private static final UUID PC = UUID.fromString("5e7ec001-0000-0000-0000-00000000000c");
    private static final UUID CAISSE_A = UUID.fromString("5e7ec002-0000-0000-0000-00000000000a");
    private static final UUID CAISSE_B = UUID.fromString("5e7ec002-0000-0000-0000-00000000000b");
    private static final UUID PAYEUR_A = UUID.fromString("5e7ec003-0000-0000-0000-00000000000a");
    private static final UUID PAYEUR_B = UUID.fromString("5e7ec003-0000-0000-0000-00000000000b");
    private static final UUID FORFAIT = UUID.fromString("5e7ec004-0000-0000-0000-000000000001");
    private static final UUID FACTURE = UUID.fromString("5e7ec005-0000-0000-0000-000000000001");
    private static final Instant DIX_HEURES = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant MIDI = Instant.parse("2026-01-01T12:00:00Z");

    protected abstract SeancesRealiseesDesCentresPubliees pilotage();

    protected abstract StatistiquesDesSeancesPubliees statistiques();

    protected abstract SeancesDuCahierPubliees cahier();

    protected abstract ForfaitsImposesAuxSeancesPublies forfaits();

    protected abstract SeancesDeLaReprisePubliees reprise();

    /** Une caisse, son agence et un centre payeur rattaché, tous du centre donné. */
    protected abstract void payeur(UUID payeur, UUID caisse, UUID centre, String codeCaisse, String nomCaisse);

    /** Une fiche de patient dans le centre, rattachée ou non à un centre payeur. */
    protected abstract void patient(UUID id, UUID centre, UUID payeur);

    protected abstract void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe);

    protected abstract void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant,
                                  BigDecimal ultrafiltration);

    /** Le volet médical de la séance (RP2 : seule sa présence compte). */
    protected abstract void voletMedical(UUID centre, UUID seance);

    /** Un centre payeur du centre {@code centreDuPayeur} dont l'agence (et la caisse) est posée sous un AUTRE centre (L8, KB-SEA-84 : agence bornée). */
    protected abstract void payeurDontLAgenceEstDUnAutreCentre(UUID payeur, UUID caisse, UUID centreDuPayeur, UUID centreDeLAgence,
                                                              String codeCaisse, String nomCaisse);

    protected void prescription(UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe) {
        prescription(UUID.randomUUID(), centre, patient, date, poidsSec, majLe);
    }

    /** Une prescription d'identifiant imposé ({@code majLe} peut être nul : la colonne est nullable). */
    protected abstract void prescription(UUID id, UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe);

    protected abstract void forfaitImpose(UUID seance, UUID forfait);

    protected abstract void facture(UUID seance, UUID facture);

    /** Statut de la séance, {@code null} si elle n'existe plus. */
    protected abstract String statutDe(UUID seance);

    protected abstract UUID patientDe(UUID seance);

    /**
     * Précision garantie des moyennes (KB-SEA-87, clôture documentée, ADR 0117) : {@code AVG} d'un NUMERIC rend 12 décimales sur H2 et 16 sur
     * PostgreSQL 16, ce qu'aucun code ne peut unifier sans changer le contrat ; seules 10 décimales sont garanties, comparées avec cette tolérance.
     */
    private static final Offset<Double> PRECISION_DES_MOYENNES = Offset.offset(1e-10);

    private UUID seance(UUID centre, UUID patient, String date, String statut) {
        UUID id = UUID.randomUUID();
        seance(id, centre, patient, LocalDate.parse(date), statut, DIX_HEURES);
        return id;
    }

    private static LocalDate le(String jour) {
        return LocalDate.parse(jour);
    }

    private static BigDecimal d(String valeur) {
        return new BigDecimal(valeur);
    }

    private double moyenne(String somme, int nombre) {
        return d(somme).divide(BigDecimal.valueOf(nombre), 12, RoundingMode.HALF_UP).doubleValue();
    }

    // ---- E1, E2, E3, E5 : pilotage de la Direction

    private void jeuDuPilotage() {
        patient(PA1, CENTRE_A, null);
        patient(PA2, CENTRE_A, null);
        patient(PB, CENTRE_B, null);
        patient(PC, CENTRE_C, null);
        seance(CENTRE_A, PA1, "2026-01-31", "VALIDEE");
        seance(CENTRE_A, PA1, "2026-02-15", "SIGNEE");
        seance(CENTRE_A, PA1, "2026-03-01", "FACTUREE");
        seance(CENTRE_A, PA1, "2026-03-02", "VALIDEE");
        seance(CENTRE_A, PA1, "2026-01-30", "VALIDEE");
        seance(CENTRE_A, PA1, "2025-12-31", "VALIDEE");
        seance(CENTRE_A, PA2, "2026-02-10", "validee");
        seance(CENTRE_A, PA2, "2026-02-11", "VALIDEE ");
        seance(CENTRE_A, PA2, "2026-02-12", "ANNULEE");
        seance(CENTRE_A, PA2, "2026-02-13", "ABSENT");
        seance(CENTRE_A, PA2, "2026-02-14", "CREE");
        seance(CENTRE_A, PA2, "2026-02-16", "PLANIFIEE");
        seance(CENTRE_A, PA2, "2026-02-17", "VALIDEE");
        seance(CENTRE_A, PA2, "2026-02-17", "SIGNEE");
        seance(CENTRE_B, PB, "2026-02-20", "VALIDEE");
        seance(CENTRE_B, PB, "2026-01-11", "FACTUREE");
        seance(CENTRE_C, PC, "2026-02-15", "VALIDEE");
    }

    @Test
    void should_count_the_realised_seances_of_each_requested_centre_with_inclusive_bounds_and_the_exact_case_of_the_status() {
        jeuDuPilotage();

        assertThat(pilotage().parCentre(List.of(CENTRE_A, CENTRE_B), le("2026-01-31"), le("2026-03-01")))
                .as("bornes incluses ; validee, 'VALIDEE ', ANNULEE, ABSENT, CREE, PLANIFIEE ignorées ; doublon du 17 compté ; centre C non demandé")
                .containsExactlyInAnyOrderEntriesOf(Map.of(CENTRE_A, 5L, CENTRE_B, 1L));
        assertThat(pilotage().parCentre(List.of(CENTRE_A, CENTRE_B), le("2026-02-21"), le("2026-02-28")))
                .as("un centre sans séance est absent").isEmpty();
        assertThat(pilotage().parCentre(List.of(CENTRE_C), le("2026-02-15"), le("2026-02-15"))).containsExactly(Map.entry(CENTRE_C, 1L));
    }

    @Test
    void should_total_the_realised_seances_of_the_previous_period() {
        jeuDuPilotage();

        assertThat(pilotage().auTotal(List.of(CENTRE_A, CENTRE_B), le("2026-01-01"), le("2026-01-30"))).isEqualTo(2);
        assertThat(pilotage().auTotal(List.of(CENTRE_A), le("2026-04-01"), le("2026-04-30"))).isZero();
    }

    @Test
    void should_count_the_realised_seances_by_centre_and_civil_month_across_a_year() {
        jeuDuPilotage();

        assertThat(pilotage().parMois(List.of(CENTRE_A, CENTRE_B), le("2025-12-01"), le("2026-03-01")))
                .containsExactlyInAnyOrder(
                        new MoisRealisePublie(CENTRE_A, 2025, 12, 1), new MoisRealisePublie(CENTRE_A, 2026, 1, 2),
                        new MoisRealisePublie(CENTRE_A, 2026, 2, 3), new MoisRealisePublie(CENTRE_A, 2026, 3, 1),
                        new MoisRealisePublie(CENTRE_B, 2026, 1, 1), new MoisRealisePublie(CENTRE_B, 2026, 2, 1));
        assertThat(pilotage().parMois(List.of(CENTRE_A), le("2026-02-15"), le("2026-02-15")))
                .containsExactly(new MoisRealisePublie(CENTRE_A, 2026, 2, 1));
    }

    @Test
    void should_list_the_months_by_centre_then_year_then_month_whatever_the_order_of_the_centres_and_of_the_seances_rf5() {
        patient(PA1, CENTRE_A, null);
        patient(PB, CENTRE_B, null);
        seance(CENTRE_B, PB, "2026-02-20", "VALIDEE");
        seance(CENTRE_A, PA1, "2026-02-15", "VALIDEE");
        seance(CENTRE_A, PA1, "2026-01-10", "VALIDEE");
        seance(CENTRE_A, PA1, "2025-12-31", "VALIDEE");
        seance(CENTRE_B, PB, "2026-01-11", "VALIDEE");

        assertThat(pilotage().parMois(List.of(CENTRE_B, CENTRE_A), le("2025-12-01"), le("2026-03-01")))
                .as("RF5 (revue complémentaire, constat 1) : ordre garanti = centre (texte de l'identifiant), année, mois")
                .containsExactly(new MoisRealisePublie(CENTRE_A, 2025, 12, 1), new MoisRealisePublie(CENTRE_A, 2026, 1, 1),
                        new MoisRealisePublie(CENTRE_A, 2026, 2, 1), new MoisRealisePublie(CENTRE_B, 2026, 1, 1),
                        new MoisRealisePublie(CENTRE_B, 2026, 2, 1));
    }

    // ---- E4 : répartition par caisse

    @Test
    void should_list_the_insurance_funds_by_centre_then_code_whatever_the_order_of_the_centres_and_of_the_seances_rf5() {
        UUID autrePayeurDuCentreA = UUID.randomUUID();
        payeur(PAYEUR_B, CAISSE_B, CENTRE_B, "AA", "AA du centre B");
        payeur(PAYEUR_A, CAISSE_A, CENTRE_A, "ZZ", "ZZ du centre A");
        payeur(autrePayeurDuCentreA, UUID.randomUUID(), CENTRE_A, "CNAS", "CNAS du centre A");
        patient(PB, CENTRE_B, PAYEUR_B);
        patient(PA1, CENTRE_A, PAYEUR_A);
        patient(PA3, CENTRE_A, autrePayeurDuCentreA);
        patient(PA2, CENTRE_A, null);
        seance(CENTRE_B, PB, "2026-02-02", "VALIDEE");
        seance(CENTRE_A, PA1, "2026-02-01", "VALIDEE");
        seance(CENTRE_A, PA3, "2026-02-01", "VALIDEE");
        seance(CENTRE_A, PA2, "2026-02-01", "VALIDEE");

        assertThat(pilotage().parCaisse(List.of(CENTRE_B, CENTRE_A), le("2026-02-01"), le("2026-02-28")))
                .as("RF5 (revue complémentaire, constat 1) : ordre garanti = centre (texte de l'identifiant), puis code ; la caisse vide d'abord")
                .extracting(CaisseRealiseePubliee::centre, CaisseRealiseePubliee::codeCaisse)
                .containsExactly(tuple(CENTRE_A, ""), tuple(CENTRE_A, "CNAS"), tuple(CENTRE_A, "ZZ"), tuple(CENTRE_B, "AA"));
    }

    @Test
    void should_name_a_fund_shared_by_two_payers_with_the_greatest_name_whatever_the_order_of_the_seances_rf5() {
        payeur(PAYEUR_A, CAISSE_A, CENTRE_A, "CNAS", "Zeta");
        payeur(PAYEUR_B, CAISSE_B, CENTRE_A, "CNAS", "Alpha");
        patient(PA1, CENTRE_A, PAYEUR_A);
        patient(PA2, CENTRE_A, PAYEUR_B);
        seance(CENTRE_A, PA1, "2026-02-01", "VALIDEE");
        seance(CENTRE_A, PA2, "2026-02-01", "VALIDEE");

        assertThat(pilotage().parCaisse(List.of(CENTRE_A), le("2026-02-01"), le("2026-02-28")))
                .as("RF5 (constat 2) : deux caisses de même code dans un centre (aucune contrainte en base) : un seul regroupement, nom le plus "
                        + "grand dans l'ordre des chaînes (collation de la base non reproduite, KB-RF5-01), jamais « le dernier lu »")
                .extracting(CaisseRealiseePubliee::codeCaisse, CaisseRealiseePubliee::nomCaisse, CaisseRealiseePubliee::nombre)
                .containsExactly(tuple("CNAS", "Zeta", 2L));
    }

    @Test
    void should_count_the_realised_seances_by_insurance_fund_only_when_the_patient_record_is_in_the_centre_of_the_seance() {
        payeur(PAYEUR_A, CAISSE_A, CENTRE_A, "CNAS", "CNAS du centre A");
        payeur(PAYEUR_B, CAISSE_B, CENTRE_B, "CNAS-B", "CNAS du centre B");
        patient(PA1, CENTRE_A, PAYEUR_A);
        patient(PA2, CENTRE_A, null);
        patient(PA3, CENTRE_A, PAYEUR_B);
        patient(PX, CENTRE_B, null);
        patient(PB, CENTRE_B, PAYEUR_B);
        seance(CENTRE_A, PA1, "2026-02-01", "VALIDEE");
        seance(CENTRE_A, PA1, "2026-02-03", "FACTUREE");
        seance(CENTRE_A, PA1, "2026-02-05", "validee");
        seance(CENTRE_A, PA2, "2026-02-01", "SIGNEE");
        seance(CENTRE_A, PA3, "2026-02-01", "VALIDEE");
        seance(CENTRE_A, PX, "2026-02-01", "VALIDEE");
        seance(CENTRE_B, PB, "2026-02-02", "VALIDEE");

        assertThat(pilotage().parCaisse(List.of(CENTRE_A, CENTRE_B), le("2026-02-01"), le("2026-02-28")))
                .as("ensemble seulement (ordre du SGBD) ; KB-DIR-05 et KB-SEA-83 corrigés : la séance de PX (fiche au centre B) reste comptée, "
                        + "sous la caisse vide ; KB-SEA-84 corrigé : la chaîne payeur du centre B posée sur une fiche du centre A tombe sur la "
                        + "caisse vide ; sans caisse : code vide, nom nul")
                .extracting(CaisseRealiseePubliee::centre, CaisseRealiseePubliee::codeCaisse, CaisseRealiseePubliee::nomCaisse,
                        CaisseRealiseePubliee::nombre)
                .containsExactlyInAnyOrder(tuple(CENTRE_A, "CNAS", "CNAS du centre A", 2L), tuple(CENTRE_A, "", null, 3L),
                        tuple(CENTRE_B, "CNAS-B", "CNAS du centre B", 1L));
        assertThat(pilotage().parCentre(List.of(CENTRE_A), le("2026-02-01"), le("2026-02-28")))
                .containsExactly(Map.entry(CENTRE_A, 5L));
    }

    @Test
    void should_count_the_same_seances_in_the_overview_and_in_the_insurance_fund_breakdown_whatever_the_record_or_payer_centre() {
        payeur(PAYEUR_B, CAISSE_B, CENTRE_B, "CNAS-B", "CNAS du centre B");
        patient(PA1, CENTRE_A, PAYEUR_B);
        patient(PB, CENTRE_B, PAYEUR_B);
        seance(CENTRE_A, PA1, "2026-02-01", "VALIDEE");
        seance(CENTRE_A, PB, "2026-02-02", "SIGNEE");
        seance(CENTRE_A, PB, "2026-02-03", "FACTUREE");
        seance(CENTRE_B, PB, "2026-02-04", "VALIDEE");

        long vueDEnsemble = pilotage().parCentre(List.of(CENTRE_A, CENTRE_B), le("2026-02-01"), le("2026-02-28")).get(CENTRE_A);
        long caisses = pilotage().parCaisse(List.of(CENTRE_A, CENTRE_B), le("2026-02-01"), le("2026-02-28")).stream()
                .filter(c -> c.centre().equals(CENTRE_A)).mapToLong(CaisseRealiseePubliee::nombre).sum();

        assertThat(caisses).as("la somme des caisses d'un centre égale sa vue d'ensemble (KB-DIR-05, KB-SEA-83)").isEqualTo(vueDEnsemble).isEqualTo(3);
        assertThat(pilotage().parCaisse(List.of(CENTRE_A), le("2026-02-01"), le("2026-02-28")))
                .extracting(CaisseRealiseePubliee::codeCaisse, CaisseRealiseePubliee::nombre).containsExactly(tuple("", 3L));
    }

    @Test
    void should_count_a_patient_whose_payer_centre_agency_is_in_another_centre_under_the_empty_fund_l8() {
        payeurDontLAgenceEstDUnAutreCentre(PAYEUR_A, CAISSE_A, CENTRE_A, CENTRE_B, "CNAS-B", "CNAS du centre B");
        patient(PA1, CENTRE_A, PAYEUR_A);
        seance(CENTRE_A, PA1, "2026-02-01", "VALIDEE");

        assertThat(pilotage().parCaisse(List.of(CENTRE_A), le("2026-02-01"), le("2026-02-28")))
                .as("centre payeur du centre A, agence du centre B : caisse vide (aligné sur la facturation, ag.center_id borné)")
                .extracting(CaisseRealiseePubliee::codeCaisse, CaisseRealiseePubliee::nombre).containsExactly(tuple("", 1L));
    }

    // ---- E6 à E9 : statistiques du patient

    private final Map<String, UUID> stats = new LinkedHashMap<>();

    private void jeuDesStatistiques() {
        patient(PA1, CENTRE_A, null);
        stats.put("S1", seance(CENTRE_A, PA1, "2026-02-01", "VALIDEE"));
        stats.put("S2", seance(CENTRE_A, PA1, "2026-02-02", "ANNULEE"));
        stats.put("S3", seance(CENTRE_A, PA1, "2026-02-03", "validee"));
        stats.put("S4", seance(CENTRE_A, PA1, "2026-02-04", "CREE"));
        stats.put("S5", seance(CENTRE_A, PA1, "2026-02-05", "VALIDEE"));
        stats.put("S6", seance(CENTRE_A, PA1, "2026-02-05", "SIGNEE"));
        stats.put("S8", seance(CENTRE_A, PA1, "2026-03-02", "VALIDEE"));
        stats.put("S7", seance(CENTRE_B, PA1, "2026-02-05", "VALIDEE"));
        stats.put("S9", seance(CENTRE_A, PA1, "2026-03-05", "FACTUREE"));
        volet(CENTRE_B, stats.get("S9"), d("90.00"), d("80.00"), "99/99", d("8000"));
        volet(CENTRE_A, stats.get("S1"), d("70.50"), d("68.00"), "130/80", d("2500"));
        volet(CENTRE_A, stats.get("S2"), d("72.00"), d("70.00"), "13/8", null);
        volet(CENTRE_B, stats.get("S4"), d("80.00"), d("77.50"), " 140 - 90 ", null);
        volet(CENTRE_A, stats.get("S5"), d("69.25"), d("67.75"), "120/70", d("1500"));
        volet(CENTRE_B, stats.get("S7"), d("99.00"), d("90.00"), "150/95", d("9000"));
        prescription(CENTRE_A, PA1, le("2026-01-01"), d("66.00"), DIX_HEURES);
        prescription(CENTRE_A, PA1, le("2026-02-01"), null, DIX_HEURES);
        prescription(CENTRE_A, PA1, le("2026-02-03"), d("67.50"), DIX_HEURES);
        prescription(CENTRE_A, PA1, le("2026-02-03"), d("68.00"), MIDI);
        prescription(CENTRE_B, PA1, le("2026-02-04"), d("99.00"), DIX_HEURES);
    }

    @Test
    void should_count_only_the_realised_seances_of_the_patient_in_the_centre_with_an_optional_period() {
        jeuDesStatistiques();

        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, null, null))
                .as("VALIDEE, SIGNEE, FACTUREE seulement (KB-SEA-85 b corrigé) : ANNULEE, CREE et 'validee' sortent ; centre B exclu")
                .isEqualTo(5);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, le("2026-02-01"), le("2026-02-05"))).isEqualTo(3);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, null, le("2026-02-04"))).as("au seul").isEqualTo(1);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, le("2026-03-01"), null)).as("du seul").isEqualTo(2);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, le("2026-02-06"), le("2026-02-28"))).isZero();
    }

    @Test
    void should_average_the_measures_of_the_realised_seances_with_the_volet_of_their_own_centre_only() {
        jeuDesStatistiques();

        assertThat(statistiques().moyenneDuPoidsAvant(CENTRE_A, PA1, null, le("2026-02-04")))
                .as("seule S1 est réalisée : S2 (ANNULEE) et S4 (CREE) sortent (KB-SEA-85 b) ; 10 décimales garanties (KB-SEA-87)")
                .isCloseTo(70.5, PRECISION_DES_MOYENNES);
        assertThat(statistiques().moyenneDuPoidsApres(CENTRE_A, PA1, null, le("2026-02-04"))).isCloseTo(68.0, PRECISION_DES_MOYENNES);
        assertThat(statistiques().moyenneDeLUltrafiltration(CENTRE_A, PA1, null, le("2026-02-04")))
                .as("2500 saisie").isCloseTo(2500.0, PRECISION_DES_MOYENNES);
        assertThat(statistiques().moyenneDuPoidsAvant(CENTRE_A, PA1, le("2026-02-01"), le("2026-02-28")))
                .as("S1, S5, S6 sans volet : 70.50 et 69.25").isCloseTo(moyenne("139.75", 2), PRECISION_DES_MOYENNES);
        assertThat(statistiques().moyenneDuPoidsAvant(CENTRE_A, PA1, le("2026-02-06"), le("2026-02-28"))).as("aucune mesure").isNull();
        assertThat(statistiques().moyenneDuPoidsAvant(CENTRE_A, PA1, le("2026-03-01"), null))
                .as("le volet de S9 est posé sous le centre B : ignoré (KB-SEA-85 a corrigé)").isNull();
        assertThat(statistiques().moyenneDeLUltrafiltration(CENTRE_A, PA1, le("2026-03-01"), null)).as("séance sans volet du centre").isNull();
    }

    @Test
    void should_return_the_weight_evolution_as_rows_with_lower_case_labels_on_every_database_with_the_dry_weight_in_force() {
        jeuDesStatistiques();

        List<Map<String, Object>> lignes = statistiques().evolutionDuPoids(CENTRE_A, PA1, le("2026-02-01"), le("2026-02-05"));

        assertThat(lignes).as("réalisées seulement (KB-SEA-85 b)").hasSize(3);
        assertThat(lignes.get(0).keySet()).as("clés en minuscules sur H2 comme sur PostgreSQL (KB-SEA-86 corrigé)").containsExactly("date_seance",
                "poids_avant_kg", "poids_apres_kg", "poids_sec_cible_kg", "uf_reelle_ml");
        List<LocalDate> dates = lignes.stream().map(l -> ((java.sql.Date) l.get("date_seance")).toLocalDate()).toList();
        assertThat(dates).isSortedAccordingTo(Comparator.naturalOrder());
        List<String> resume = new ArrayList<>();
        for (Map<String, Object> l : lignes) {
            resume.add(l.get("date_seance") + "|" + texte(l.get("poids_sec_cible_kg")) + "|" + texte(l.get("uf_reelle_ml")));
        }
        assertThat(resume).as("poids sec en vigueur au centre (le plus récemment mis à jour à date égale), UF saisie ou recalculée")
                .containsExactlyInAnyOrder("2026-02-01|66|2500", "2026-02-05|68|1500", "2026-02-05|68|null");
    }

    @Test
    void should_order_the_seances_of_a_same_day_by_creation_then_identifier_with_unknown_creation_last_in_both_evolutions() {
        patient(PA3, CENTRE_A, null);
        UUID tardive = UUID.fromString("5e7ec600-0000-0000-0000-000000000001");
        UUID sansCreation = UUID.fromString("5e7ec600-0000-0000-0000-000000000002");
        UUID ancienneB = UUID.fromString("5e7ec600-0000-0000-0000-000000000004");
        UUID ancienneA = UUID.fromString("5e7ec600-0000-0000-0000-000000000003");
        seance(tardive, CENTRE_A, PA3, le("2026-02-05"), "VALIDEE", MIDI);
        seance(sansCreation, CENTRE_A, PA3, le("2026-02-05"), "VALIDEE", null);
        seance(ancienneB, CENTRE_A, PA3, le("2026-02-05"), "VALIDEE", DIX_HEURES);
        seance(ancienneA, CENTRE_A, PA3, le("2026-02-05"), "VALIDEE", DIX_HEURES);
        seance(UUID.randomUUID(), CENTRE_A, PA3, le("2026-02-04"), "VALIDEE", MIDI);
        volet(CENTRE_A, tardive, d("73.00"), d("71.00"), "13/8", null);
        volet(CENTRE_A, sansCreation, d("74.00"), d("71.00"), "14/8", null);
        volet(CENTRE_A, ancienneB, d("72.00"), d("71.00"), "12/8", null);
        volet(CENTRE_A, ancienneA, d("71.00"), d("71.00"), "11/8", null);

        List<Map<String, Object>> poids = statistiques().evolutionDuPoids(CENTRE_A, PA3, le("2026-02-01"), le("2026-02-28"));
        List<TensionsDeSeancePubliees> tensions = statistiques().evolutionDeLaTension(CENTRE_A, PA3, le("2026-02-01"), le("2026-02-28"));

        assertThat(poids.subList(1, 5)).as("la plus ancienne créée d'abord, création inconnue en dernier, identifiant en dernier ressort (KB-SEA-86)")
                .extracting(l -> texte(l.get("poids_avant_kg"))).containsExactly("71", "72", "73", "74");
        assertThat(tensions.subList(1, 5)).extracting(TensionsDeSeancePubliees::tensionAvant).containsExactly("11/8", "12/8", "13/8", "14/8");
    }

    @Test
    void should_pick_the_dry_weight_with_a_known_update_before_an_unknown_one_then_the_highest_identifier_l8() {
        patient(PA3, CENTRE_A, null);
        seance(CENTRE_A, PA3, "2026-02-10", "VALIDEE");
        seance(CENTRE_A, PA3, "2026-04-10", "VALIDEE");
        // février : mise à jour inconnue (nulle) contre connue : la connue l'emporte, sur H2 comme sur PostgreSQL
        prescription(UUID.fromString("5e7ec700-0000-0000-0000-0000000000f1"), CENTRE_A, PA3, le("2026-02-01"), d("60.00"), null);
        prescription(UUID.fromString("5e7ec700-0000-0000-0000-000000000001"), CENTRE_A, PA3, le("2026-02-01"), d("61.00"), DIX_HEURES);
        // avril : même date, même mise à jour : l'identifiant le plus élevé l'emporte
        prescription(UUID.fromString("5e7ec700-0000-0000-0000-000000000002"), CENTRE_A, PA3, le("2026-04-01"), d("70.00"), MIDI);
        prescription(UUID.fromString("5e7ec700-0000-0000-0000-000000000003"), CENTRE_A, PA3, le("2026-04-01"), d("71.00"), MIDI);

        List<Map<String, Object>> lignes = statistiques().evolutionDuPoids(CENTRE_A, PA3, le("2026-02-01"), le("2026-04-30"));

        assertThat(lignes).extracting(l -> texte(l.get("poids_sec_cible_kg"))).containsExactly("61", "71");
    }

    private static String texte(Object valeur) {
        return valeur == null ? "null" : new BigDecimal(String.valueOf(valeur)).stripTrailingZeros().toPlainString();
    }

    @Test
    void should_return_the_raw_blood_pressures_of_each_seance_ordered_by_date() {
        jeuDesStatistiques();

        List<TensionsDeSeancePubliees> fevrier = statistiques().evolutionDeLaTension(CENTRE_A, PA1, le("2026-02-01"), le("2026-02-28"));
        assertThat(fevrier).as("réalisées seulement (KB-SEA-85 b)").hasSize(3);
        assertThat(fevrier.get(0)).isEqualTo(new TensionsDeSeancePubliees(le("2026-02-01"), "130/80", null));
        assertThat(fevrier.subList(1, 3)).as("deux séances du même jour, même création : l'ordre dépend de l'identifiant tiré au hasard")
                .containsExactlyInAnyOrder(new TensionsDeSeancePubliees(le("2026-02-05"), "120/70", null),
                        new TensionsDeSeancePubliees(le("2026-02-05"), null, null));
        assertThat(statistiques().evolutionDeLaTension(CENTRE_A, PA1, le("2026-03-01"), null))
                .containsExactly(new TensionsDeSeancePubliees(le("2026-03-02"), null, null),
                        new TensionsDeSeancePubliees(le("2026-03-05"), null, null));
        assertThat(statistiques().evolutionDeLaTension(CENTRE_A, PA1, le("2026-04-01"), null)).isEmpty();
    }

    // ---- E10 : cahier de dialyse ; E17 : forfait imposé

    @Test
    void should_count_the_printable_seances_of_the_logbook_with_the_exact_case_of_the_status() {
        patient(PA1, CENTRE_A, null);
        seance(CENTRE_A, PA1, "2026-02-01", "VALIDEE");
        seance(CENTRE_A, PA1, "2026-02-10", "SIGNEE");
        seance(CENTRE_A, PA1, "2026-02-10", "FACTUREE");
        seance(CENTRE_A, PA1, "2026-02-15", "validee");
        seance(CENTRE_A, PA1, "2026-02-16", "ANNULEE");
        seance(CENTRE_A, PA1, "2026-02-28", "VALIDEE");
        seance(CENTRE_B, PA1, "2026-02-11", "VALIDEE");

        assertThat(cahier().nombreDeSeancesAImprimer(CENTRE_A, PA1, le("1900-01-01"), le("2999-12-31"))).isEqualTo(4);
        assertThat(cahier().nombreDeSeancesAImprimer(CENTRE_A, PA1, le("2026-02-10"), le("2026-02-10"))).isEqualTo(2);
        assertThat(cahier().nombreDeSeancesAImprimer(CENTRE_A, PA1, le("2026-02-11"), le("2026-02-27"))).as("validee seule (KB-SEA-88)").isZero();
        assertThat(cahier().nombreDeSeancesAImprimer(CENTRE_B, PA1, le("1900-01-01"), le("2999-12-31"))).isEqualTo(1);
    }

    @Test
    void should_count_the_seances_imposing_a_package_in_any_centre_and_any_status() {
        patient(PA1, CENTRE_A, null);
        forfaitImpose(seance(CENTRE_A, PA1, "2026-02-01", "ANNULEE"), FORFAIT);
        forfaitImpose(seance(CENTRE_B, PA1, "2026-02-02", "VALIDEE"), FORFAIT);
        seance(CENTRE_A, PA1, "2026-02-03", "VALIDEE");

        assertThat(forfaits().nombreDeSeancesQuiImposentLeForfait(FORFAIT)).as("KB-SEA-91").isEqualTo(2);
        assertThat(forfaits().nombreDeSeancesQuiImposentLeForfait(UUID.fromString("5e7ec004-0000-0000-0000-000000000099"))).isZero();
    }

    // ---- E12 à E16 : reprise de données

    @Test
    void should_find_an_already_present_seance_of_the_centre_whatever_its_status_but_a_cancelled_one_the_first_created() {
        patient(PA1, CENTRE_A, null);
        UUID annulee = UUID.randomUUID();
        UUID tenue = UUID.randomUUID();
        seance(annulee, CENTRE_A, PA1, le("2026-01-05"), "ANNULEE", DIX_HEURES);
        seance(tenue, CENTRE_A, PA1, le("2026-01-05"), "VALIDEE", MIDI);
        seance(UUID.randomUUID(), CENTRE_B, PA1, le("2026-01-07"), "VALIDEE", DIX_HEURES);

        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-05"), PA1)))
                .as("la séance annulée n'est pas une séance tenue : seule la tenue bloque (KB-SEA-89 corrigé)").contains(tenue);
        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-07"), PA1))).as("séance d'un autre centre").isEmpty();
        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(null, PA1))).as("valeur nulle : IS NULL").isEmpty();
        assertThatThrownBy(() -> reprise().seanceDejaPresente(CENTRE_A, Map.of("salleId", PA1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Champ non autorisé pour seances : salleId");
    }

    @Test
    void should_let_a_cancelled_seance_alone_not_block_the_takeover_while_every_other_status_still_does() {
        patient(PA2, CENTRE_A, null);
        seance(UUID.randomUUID(), CENTRE_A, PA2, le("2026-01-05"), "ANNULEE", DIX_HEURES);
        String[] bloquants = {"CREE", "PLANIFIEE", "ABSENT", "VALIDEE", "SIGNEE", "FACTUREE"};
        UUID[] ids = new UUID[bloquants.length];
        for (int i = 0; i < bloquants.length; i++) {
            ids[i] = UUID.randomUUID();
            seance(ids[i], CENTRE_A, PA2, le("2026-01-06").plusDays(i), bloquants[i], DIX_HEURES);
        }
        seance(UUID.randomUUID(), CENTRE_B, PA2, le("2026-01-20"), "ANNULEE", DIX_HEURES);

        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-05"), PA2))).as("annulée seule : n'empêche plus la reprise (KB-SEA-89)").isEmpty();
        for (int i = 0; i < bloquants.length; i++) {
            assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-06").plusDays(i), PA2))).as("statut " + bloquants[i]).contains(ids[i]);
        }
        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-20"), PA2))).as("annulée d'un autre centre").isEmpty();
    }

    private static Map<String, Object> cle(LocalDate date, UUID patient) {
        Map<String, Object> cle = new LinkedHashMap<>();
        cle.put("dateSeance", date);
        cle.put("patientId", patient);
        return cle;
    }

    @Test
    void should_take_over_a_seance_modify_it_without_changing_its_patient_and_refuse_an_unknown_field() {
        patient(PA1, CENTRE_A, null);
        Map<String, Object> valeurs = new LinkedHashMap<>();
        valeurs.put("dateSeance", le("2026-01-09"));
        valeurs.put("statut", "SIGNEE");
        valeurs.put("patientId", PA1);

        UUID reprise = reprise().reprendre(CENTRE_A, valeurs);

        assertThat(statutDe(reprise)).isEqualTo("SIGNEE");
        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-09"), PA1))).contains(reprise);
        reprise().modifier(CENTRE_B, reprise, Map.of("statut", "VALIDEE"));
        assertThat(statutDe(reprise)).as("modification gardée par le centre du lot").isEqualTo("SIGNEE");
        Map<String, Object> modification = new LinkedHashMap<>();
        modification.put("statut", "FACTUREE");
        modification.put("patientId", PA2);
        reprise().modifier(CENTRE_A, reprise, modification);
        assertThat(statutDe(reprise)).isEqualTo("FACTUREE");
        assertThat(patientDe(reprise)).as("une ligne ne change jamais de patient").isEqualTo(PA1);
        Map<String, Object> inconnu = new LinkedHashMap<>(valeurs);
        inconnu.put("salleId", PA1);
        assertThatThrownBy(() -> reprise().reprendre(CENTRE_A, inconnu))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Champ non autorisé pour seances : salleId");
        assertThat(reprise().seancesDuPatient(PA1)).as("rien n'est écrit sur un champ refusé").containsExactly(reprise.toString());
    }

    @Test
    void should_list_the_seances_and_the_invoice_without_the_centre_and_remove_only_in_the_centre_of_the_batch() {
        patient(PA1, CENTRE_A, null);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        seance(a, CENTRE_A, PA1, le("2026-01-08"), "FACTUREE", DIX_HEURES);
        seance(b, CENTRE_B, PA1, le("2026-01-07"), "VALIDEE", DIX_HEURES);
        facture(a, FACTURE);

        assertThat(reprise().seancesDuPatient(PA1)).as("tous centres (KB-SEA-90)").containsExactlyInAnyOrder(a.toString(), b.toString());
        assertThat(reprise().factureDeLaSeance(a)).containsExactly(FACTURE.toString());
        assertThat(reprise().factureDeLaSeance(b)).isEmpty();
        reprise().retirer(CENTRE_A, b);
        assertThat(statutDe(b)).as("suppression gardée par le centre du lot").isEqualTo("VALIDEE");
        reprise().retirer(CENTRE_B, b);
        assertThat(statutDe(b)).isNull();
        assertThat(reprise().seancesDuPatient(PA1)).containsExactly(a.toString());
    }

    /** RP2 (ADR 0170), E17 : « complétée » = un volet médical OU paramédical existe, sans borne de centre. */
    @Test
    void should_tell_a_seance_is_completed_when_a_medical_or_a_paramedical_volet_exists_whatever_the_centre() {
        patient(PA1, CENTRE_A, null);
        UUID libre = UUID.randomUUID();
        UUID medicale = UUID.randomUUID();
        UUID paramedicale = UUID.randomUUID();
        UUID autreCentre = UUID.randomUUID();
        for (UUID s : List.of(libre, medicale, paramedicale, autreCentre)) {
            seance(s, CENTRE_A, PA1, le("2026-01-0" + (1 + List.of(libre, medicale, paramedicale, autreCentre).indexOf(s))), "VALIDEE", DIX_HEURES);
        }
        voletMedical(CENTRE_A, medicale);
        volet(CENTRE_A, paramedicale, d("70.00"), d("68.00"), "13/8", null);
        voletMedical(CENTRE_B, autreCentre);

        assertThat(reprise().laSeanceEstCompletee(libre)).as("aucun volet").isFalse();
        assertThat(reprise().laSeanceEstCompletee(medicale)).as("volet médical").isTrue();
        assertThat(reprise().laSeanceEstCompletee(paramedicale)).as("volet paramédical").isTrue();
        assertThat(reprise().laSeanceEstCompletee(autreCentre)).as("volet posé sous un autre centre : compte aussi").isTrue();
        assertThat(reprise().laSeanceEstCompletee(UUID.randomUUID())).as("séance inconnue").isFalse();
    }
}
