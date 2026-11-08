package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsImposesAuxSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDuCahierPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees.CaisseRealiseePubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesDesCentresPubliees.MoisRealisePublie;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.StatistiquesDesSeancesPubliees.TensionsDeSeancePubliees;
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
 * E11, l'état Jasper, excepté : il reste dans le modèle de document) y compris ce qui n'est PAS corrigé : casse et blancs du statut
 * (KB-SEA-83, 88), séance sur une fiche d'un autre centre absente des caisses (KB-SEA-83), chaîne de caisse sans centre et ordre du SGBD
 * (KB-SEA-84), statistiques de tous statuts avec le volet d'un autre centre (KB-SEA-85), ordre intra-jour et libellés de colonnes du SGBD
 * (KB-SEA-86), précision des moyennes du SGBD (KB-SEA-87), séance déjà présente de n'importe quel statut (KB-SEA-89), séances du patient
 * et facture sans centre (KB-SEA-90), forfait imposé de n'importe quel centre ou statut (KB-SEA-91).
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

    protected abstract void prescription(UUID centre, UUID patient, LocalDate date, BigDecimal poidsSec, Instant majLe);

    protected abstract void forfaitImpose(UUID seance, UUID forfait);

    protected abstract void facture(UUID seance, UUID facture);

    /** Statut de la séance, {@code null} si elle n'existe plus. */
    protected abstract String statutDe(UUID seance);

    protected abstract UUID patientDe(UUID seance);

    /** Libellés de colonnes du SGBD : MAJUSCULES sur H2 (et le double), minuscules sur PostgreSQL (KB-SEA-86). */
    protected abstract boolean libellesEnMajuscules();

    /** Décimales de {@code AVG} sur un NUMERIC : 12 sur H2 (et le double), 16 sur PostgreSQL 16 pour ces données (KB-SEA-87). */
    protected abstract int decimalesDesMoyennes();

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
        return d(somme).divide(BigDecimal.valueOf(nombre), decimalesDesMoyennes(), RoundingMode.HALF_UP).doubleValue();
    }

    private String libelle(String colonne) {
        return libellesEnMajuscules() ? colonne.toUpperCase() : colonne;
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

    // ---- E4 : répartition par caisse

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
                .as("ordre du SGBD (KB-SEA-84) : ensemble seulement ; la séance de PX (fiche au centre B) disparaît (KB-SEA-83) ; la caisse du "
                        + "centre B est suivie depuis une fiche du centre A (KB-SEA-84) ; sans caisse : code vide, nom nul")
                .extracting(CaisseRealiseePubliee::centre, CaisseRealiseePubliee::codeCaisse, CaisseRealiseePubliee::nomCaisse,
                        CaisseRealiseePubliee::nombre)
                .containsExactlyInAnyOrder(tuple(CENTRE_A, "CNAS", "CNAS du centre A", 2L), tuple(CENTRE_A, "", null, 1L),
                        tuple(CENTRE_A, "CNAS-B", "CNAS du centre B", 1L), tuple(CENTRE_B, "CNAS-B", "CNAS du centre B", 1L));
        assertThat(pilotage().parCentre(List.of(CENTRE_A), le("2026-02-01"), le("2026-02-28")))
                .as("la vue d'ensemble compte la séance de PX : les deux comptes ne concordent pas (KB-SEA-83)")
                .containsExactly(Map.entry(CENTRE_A, 5L));
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
    void should_count_every_seance_of_the_patient_in_the_centre_whatever_its_status_with_an_optional_period() {
        jeuDesStatistiques();

        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, null, null)).as("tous statuts, centre B exclu (KB-SEA-85)").isEqualTo(7);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, le("2026-02-01"), le("2026-02-05"))).isEqualTo(6);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, null, le("2026-02-04"))).as("au seul").isEqualTo(4);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, le("2026-03-01"), null)).as("du seul").isEqualTo(1);
        assertThat(statistiques().nombreDeSeances(CENTRE_A, PA1, le("2026-02-06"), le("2026-02-28"))).isZero();
    }

    @Test
    void should_average_the_measures_including_a_volet_of_another_centre_with_the_precision_of_the_database() {
        jeuDesStatistiques();

        assertThat(statistiques().moyenneDuPoidsAvant(CENTRE_A, PA1, null, le("2026-02-04")))
                .as("70.50, 72.00 et 80.00 (volet du centre B, KB-SEA-85) ; précision du SGBD (KB-SEA-87)").isEqualTo(moyenne("222.50", 3));
        assertThat(statistiques().moyenneDuPoidsApres(CENTRE_A, PA1, null, le("2026-02-04"))).isEqualTo(moyenne("215.50", 3));
        assertThat(statistiques().moyenneDeLUltrafiltration(CENTRE_A, PA1, null, le("2026-02-04")))
                .as("2500 saisie, 2000 et 2500 recalculées (perte de poids x 1000)").isEqualTo(moyenne("7000", 3));
        assertThat(statistiques().moyenneDuPoidsAvant(CENTRE_A, PA1, le("2026-02-06"), le("2026-02-28"))).as("aucune mesure").isNull();
        assertThat(statistiques().moyenneDeLUltrafiltration(CENTRE_A, PA1, le("2026-03-01"), null)).as("séance sans volet").isNull();
    }

    @Test
    void should_return_the_weight_evolution_as_raw_rows_labelled_by_the_database_with_the_dry_weight_in_force() {
        jeuDesStatistiques();

        List<Map<String, Object>> lignes = statistiques().evolutionDuPoids(CENTRE_A, PA1, le("2026-02-01"), le("2026-02-05"));

        assertThat(lignes).hasSize(6);
        assertThat(lignes.get(0).keySet()).as("libellés du SGBD (KB-SEA-86)").containsExactly(libelle("date_seance"),
                libelle("poids_avant_kg"), libelle("poids_apres_kg"), libelle("poids_sec_cible_kg"), libelle("uf_reelle_ml"));
        List<LocalDate> dates = lignes.stream().map(l -> ((java.sql.Date) l.get(libelle("date_seance"))).toLocalDate()).toList();
        assertThat(dates).as("tri par date seule ; ordre intra-jour non verrouillé (KB-SEA-86)").isSortedAccordingTo(Comparator.naturalOrder());
        List<String> resume = new ArrayList<>();
        for (Map<String, Object> l : lignes) {
            resume.add(l.get(libelle("date_seance")) + "|" + texte(l.get(libelle("poids_sec_cible_kg"))) + "|" + texte(l.get(libelle("uf_reelle_ml"))));
        }
        assertThat(resume).as("poids sec en vigueur au centre (le plus récemment mis à jour à date égale), UF saisie ou recalculée")
                .containsExactlyInAnyOrder("2026-02-01|66|2500", "2026-02-02|66|2000", "2026-02-03|68|null", "2026-02-04|68|2500",
                        "2026-02-05|68|1500", "2026-02-05|68|null");
    }

    private static String texte(Object valeur) {
        return valeur == null ? "null" : new BigDecimal(String.valueOf(valeur)).stripTrailingZeros().toPlainString();
    }

    @Test
    void should_return_the_raw_blood_pressures_of_each_seance_ordered_by_date() {
        jeuDesStatistiques();

        assertThat(statistiques().evolutionDeLaTension(CENTRE_A, PA1, le("2026-02-01"), le("2026-02-04")))
                .containsExactly(new TensionsDeSeancePubliees(le("2026-02-01"), "130/80", null),
                        new TensionsDeSeancePubliees(le("2026-02-02"), "13/8", null),
                        new TensionsDeSeancePubliees(le("2026-02-03"), null, null),
                        new TensionsDeSeancePubliees(le("2026-02-04"), " 140 - 90 ", null));
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
    void should_find_an_already_present_seance_of_the_centre_whatever_its_status_the_first_created() {
        patient(PA1, CENTRE_A, null);
        UUID annulee = UUID.randomUUID();
        seance(annulee, CENTRE_A, PA1, le("2026-01-05"), "ANNULEE", DIX_HEURES);
        seance(UUID.randomUUID(), CENTRE_A, PA1, le("2026-01-05"), "VALIDEE", MIDI);
        seance(UUID.randomUUID(), CENTRE_B, PA1, le("2026-01-07"), "VALIDEE", DIX_HEURES);

        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-05"), PA1))).as("KB-SEA-89").contains(annulee);
        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(le("2026-01-07"), PA1))).as("séance d'un autre centre").isEmpty();
        assertThat(reprise().seanceDejaPresente(CENTRE_A, cle(null, PA1))).as("valeur nulle : IS NULL").isEmpty();
        assertThatThrownBy(() -> reprise().seanceDejaPresente(CENTRE_A, Map.of("salleId", PA1)))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Champ non autorisé pour seances : salleId");
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
}
