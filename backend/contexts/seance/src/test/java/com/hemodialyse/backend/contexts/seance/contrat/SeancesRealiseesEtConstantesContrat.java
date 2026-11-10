package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ConstantesDesSeancesPubliees.ConstantesDeSeancePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.LiensDExclusionPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees.SeanceRealiseeAvecPlacePubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesPubliees.JourRealisePublie;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat commun des lectures publiées de la Séance pour les Absences de patients, la Planification et le Dossier médical (ADR 0098, SE7d) :
 * joué par le double en mémoire, l'adapter JDBC sur H2 et sur PostgreSQL 16 jetable. Il décrit les HUIT requêtes reprises à l'identique
 * (A1 à A5, P1, D1, D2 de {@code .ddd/safety-net/seance-se7d.md}). Lot L5a (ADR 0117) : tris déterministes (KB-SEA-77, 78), création nulle
 * en premier sur toutes les bases (KB-SEA-79), lien d'exclusion borné aux séances ABSENT (KB-SEA-81). Reste reproduit : casse et blancs du
 * statut (KB-SEA-82), jointure de la fiche au centre de la séance (KB-SEA-80). Le jeu de données reprend celui de
 * {@code seance-se7d-pg16-rejeu.sh}.
 */
public abstract class SeancesRealiseesEtConstantesContrat {

    protected static final UUID CENTRE_A = UUID.fromString("5e7dc000-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("5e7dc000-0000-0000-0000-00000000000b");
    private static final UUID P1 = UUID.fromString("5e7dc001-0000-0000-0000-000000000001");
    private static final UUID P2 = UUID.fromString("5e7dc001-0000-0000-0000-000000000002");
    private static final UUID P3 = UUID.fromString("5e7dc001-0000-0000-0000-000000000003");
    private static final UUID P4 = UUID.fromString("5e7dc001-0000-0000-0000-000000000004");
    private static final UUID P5 = UUID.fromString("5e7dc001-0000-0000-0000-000000000005");
    private static final UUID PB = UUID.fromString("5e7dc001-0000-0000-0000-00000000000b");
    private static final UUID SALLE_1 = UUID.fromString("5e7dc002-0000-0000-0000-000000000001");
    private static final UUID SALLE_2 = UUID.fromString("5e7dc002-0000-0000-0000-000000000002");
    private static final UUID CRENEAU = UUID.fromString("5e7dc003-0000-0000-0000-000000000001");
    private static final UUID GENERATEUR = UUID.fromString("5e7dc004-0000-0000-0000-000000000001");
    /**
     * Trois identifiants dont l'ordre signé de Java (C, B, A en décroissant) diffère de l'ordre en octets non signés de H2 et PostgreSQL
     * (B, A, C) ; distincts de ceux du jeu de {@code seance-se7d-pg16-rejeu.sh}, posé dans la même base jetable.
     */
    private static final UUID EGAL_A = UUID.fromString("ce7dc000-0000-0000-0000-00000000000a");
    private static final UUID EGAL_B = UUID.fromString("e37dc000-0000-0000-0000-00000000000b");
    private static final UUID EGAL_C = UUID.fromString("407dc000-0000-0000-0000-00000000000c");
    private static final Instant DIX_HEURES = Instant.parse("2026-01-01T10:00:00Z");
    /** Dimanche 2026-03-01 : la semaine des absences va du dimanche au samedi. */
    private static final LocalDate DIMANCHE = LocalDate.of(2026, 3, 1);

    protected abstract SeancesRealiseesPubliees realisees();

    protected abstract LiensDExclusionPublies liens();

    protected abstract SeancesRealiseesAvecLeurPlacePubliees avecPlace();

    protected abstract ConstantesDesSeancesPubliees constantes();

    protected abstract void patient(UUID id, UUID centre, String nom, String prenom);

    /** La fiche du patient passe dans un autre centre (ses séances restent où elles sont). */
    protected abstract void ficheDansLeCentre(UUID patient, UUID centre);

    /** Une séance ; {@code creeLe} peut être nul (colonne {@code created_at} nullable). */
    protected abstract void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, Instant creeLe);

    protected abstract void place(UUID seance, UUID salle, UUID creneau, UUID generateur);

    protected abstract void lienDExclusion(UUID seance, UUID absence);

    protected abstract void volet(UUID centre, UUID seance, BigDecimal poidsAvant, BigDecimal poidsApres, String tensionAvant,
                                  String tensionApres, Integer debit, BigDecimal ultrafiltration, Integer duree);

    private UUID seance(UUID centre, UUID patient, LocalDate date, String statut) {
        UUID id = UUID.randomUUID();
        seance(id, centre, patient, date, statut, DIX_HEURES);
        return id;
    }

    private static LocalDate le(String jour) {
        return LocalDate.parse(jour);
    }

    // ---- A1 : séances réalisées du centre (semaine des absences)

    @Test
    void should_read_one_line_per_patient_and_day_of_the_realised_seances_of_the_centre_with_dates_ascending() {
        seance(CENTRE_A, P3, le("2026-03-03"), "FACTUREE");
        seance(CENTRE_A, P2, le("2026-03-03"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-03-03"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-03-03"), "SIGNEE");
        seance(CENTRE_A, P2, le("2026-03-02"), "SIGNEE");
        seance(CENTRE_A, P1, le("2026-03-07"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-03-08"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-02-28"), "VALIDEE");
        seance(CENTRE_A, P3, le("2026-03-04"), "CREE");
        seance(CENTRE_A, P3, le("2026-03-05"), "ABSENT");
        seance(CENTRE_A, P3, le("2026-03-06"), "validee");
        seance(CENTRE_B, PB, le("2026-03-03"), "VALIDEE");

        List<JourRealisePublie> semaine = realisees().joursRealisesDuCentre(CENTRE_A, DIMANCHE, DIMANCHE.plusDays(6));

        assertThat(semaine).extracting(JourRealisePublie::jour).isSortedAccordingTo(Comparator.naturalOrder());
        assertThat(semaine).as("date puis patient, déterministe (KB-SEA-77 corrigé)").containsExactly(
                new JourRealisePublie(P2, le("2026-03-02")), new JourRealisePublie(P1, le("2026-03-03")),
                new JourRealisePublie(P2, le("2026-03-03")), new JourRealisePublie(P3, le("2026-03-03")),
                new JourRealisePublie(P1, le("2026-03-07")));
    }

    @Test
    void should_read_nothing_when_the_bounds_are_reversed() {
        seance(CENTRE_A, P1, le("2026-03-03"), "VALIDEE");

        assertThat(realisees().joursRealisesDuCentre(CENTRE_A, DIMANCHE.plusDays(6), DIMANCHE)).isEmpty();
        assertThat(realisees().joursRealisesDuPatient(CENTRE_A, P1, DIMANCHE.plusDays(6), DIMANCHE)).isEmpty();
        assertThat(avecPlace().duCentre(CENTRE_A, DIMANCHE.plusDays(6), DIMANCHE)).isEmpty();
    }

    // ---- A2 : jours d'un patient (rattrapage)

    @Test
    void should_read_the_distinct_days_of_a_patient_ascending_within_inclusive_bounds_ignoring_a_trailing_blank() {
        seance(CENTRE_A, P1, le("2026-01-12"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-01-12"), "FACTUREE");
        seance(CENTRE_A, P1, le("2026-01-13"), "SIGNEE ");
        seance(CENTRE_A, P1, le("2026-01-14"), "SIGNEE");
        seance(CENTRE_A, P1, le("2026-01-20"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-01-21"), "VALIDEE");
        seance(CENTRE_A, P2, le("2026-01-15"), "VALIDEE");
        seance(CENTRE_B, P1, le("2026-01-16"), "VALIDEE");

        assertThat(realisees().joursRealisesDuPatient(CENTRE_A, P1, le("2026-01-11"), le("2026-01-20")))
                .containsExactly(le("2026-01-12"), le("2026-01-14"), le("2026-01-20"));
        assertThat(realisees().joursRealisesDuPatient(CENTRE_A, P1, le("2026-01-20"), le("2026-01-20"))).containsExactly(le("2026-01-20"));
    }

    // ---- A3 : séance réalisée un jour (casse et blancs, KB-SEA-82)

    @Test
    void should_see_a_seance_as_realised_only_with_an_exact_upper_case_status_without_blank() {
        String[] statuts = {"VALIDEE", "validee", "VALIDEE ", " VALIDEE", "Signee", "facturee", "SIGNEE", "FACTUREE", "CREE", "ABSENT",
                "ANNULEE"};
        boolean[] attendus = {true, false, false, false, false, false, true, true, false, false, false};
        for (int i = 0; i < statuts.length; i++) {
            seance(CENTRE_A, P1, le("2026-01-31").plusDays(i), statuts[i]);
        }
        seance(CENTRE_B, PB, le("2026-01-31"), "VALIDEE");

        for (int i = 0; i < statuts.length; i++) {
            assertThat(realisees().estRealisee(CENTRE_A, P1, le("2026-01-31").plusDays(i))).as("statut [" + statuts[i] + "]")
                    .isEqualTo(attendus[i]);
        }
        assertThat(realisees().estRealisee(CENTRE_A, P2, le("2026-01-31"))).as("autre patient").isFalse();
        assertThat(realisees().estRealisee(CENTRE_A, PB, le("2026-01-31"))).as("séance du centre B lue par A").isFalse();
        assertThat(realisees().estRealisee(CENTRE_B, PB, le("2026-01-31"))).isTrue();
    }

    // ---- A4 : lien d'exclusion (KB-SEA-81)

    @Test
    void should_see_an_absence_as_linked_only_when_the_seance_carrying_the_link_is_really_excluded_and_only_in_its_centre() {
        UUID absenceSurValidee = UUID.fromString("ab5dc000-0000-0000-0000-000000000001");
        UUID absenceSurAbsente = UUID.fromString("ab5dc000-0000-0000-0000-000000000002");
        UUID absenceDuCentreB = UUID.fromString("ab5dc000-0000-0000-0000-000000000003");
        UUID absenceLibre = UUID.fromString("ab5dc000-0000-0000-0000-000000000004");
        lienDExclusion(seance(CENTRE_A, P1, le("2026-01-20"), "VALIDEE"), absenceSurValidee);
        lienDExclusion(seance(CENTRE_A, P2, le("2026-01-20"), "ABSENT"), absenceSurAbsente);
        lienDExclusion(seance(CENTRE_B, PB, le("2026-01-20"), "ABSENT"), absenceDuCentreB);
        seance(CENTRE_A, P3, le("2026-01-20"), "ABSENT");

        assertThat(liens().absenceLiee(CENTRE_A, absenceSurValidee)).as("lien résiduel sur une séance VALIDEE : ne bloque plus (KB-SEA-81 corrigé)")
                .isFalse();
        assertThat(liens().absenceLiee(CENTRE_A, absenceSurAbsente)).isTrue();
        assertThat(liens().absenceLiee(CENTRE_A, absenceDuCentreB)).as("lien porté par une séance du centre B").isFalse();
        assertThat(liens().absenceLiee(CENTRE_B, absenceDuCentreB)).isTrue();
        assertThat(liens().absenceLiee(CENTRE_A, absenceLibre)).isFalse();
    }

    // ---- A5 : patients écartés de la détection

    @Test
    void should_list_the_patient_of_every_realised_seance_of_the_day_keeping_the_duplicates() {
        seance(CENTRE_A, P3, le("2026-03-03"), "FACTUREE");
        seance(CENTRE_A, P2, le("2026-03-03"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-03-03"), "VALIDEE");
        seance(CENTRE_A, P1, le("2026-03-03"), "SIGNEE");
        seance(CENTRE_A, P4, le("2026-03-03"), "CREE");
        seance(CENTRE_A, P5, le("2026-03-03"), "Validee");
        seance(CENTRE_A, P4, le("2026-03-04"), "VALIDEE");
        seance(CENTRE_B, PB, le("2026-03-03"), "VALIDEE");

        assertThat(realisees().patientsRealisesLe(CENTRE_A, le("2026-03-03"))).as("doublon conservé, aucun ordre")
                .containsExactlyInAnyOrder(P1, P1, P2, P3);
        assertThat(realisees().patientsRealisesLe(CENTRE_A, le("2026-03-05"))).isEmpty();
    }

    // ---- P1 : séances réalisées avec leur place (semaine du planning)

    @Test
    void should_read_the_realised_sessions_of_the_period_with_their_frozen_place_and_ignore_the_others() {
        LocalDate lundi = le("2026-03-02");
        patient(P1, CENTRE_A, "Durand", "Alice");
        patient(PB, CENTRE_B, "Autre", "Centre");
        UUID placee = seance(CENTRE_A, P1, lundi, "VALIDEE");
        place(placee, SALLE_1, CRENEAU, GENERATEUR);
        seance(CENTRE_A, P1, lundi.plusDays(1), "SIGNEE");
        place(seance(CENTRE_A, P1, lundi.plusDays(2), "CREE"), SALLE_1, CRENEAU, GENERATEUR);
        seance(CENTRE_A, P1, lundi.plusDays(3), "validee");
        place(seance(CENTRE_A, P1, lundi.plusDays(30), "FACTUREE"), SALLE_1, CRENEAU, GENERATEUR);
        place(seance(CENTRE_B, PB, lundi, "VALIDEE"), SALLE_1, CRENEAU, GENERATEUR);

        List<SeanceRealiseeAvecPlacePubliee> seances = avecPlace().duCentre(CENTRE_A, lundi, lundi.plusDays(6));

        assertThat(seances).containsExactly(
                new SeanceRealiseeAvecPlacePubliee(P1, lundi, SALLE_1, CRENEAU, GENERATEUR, "Durand", "Alice"),
                new SeanceRealiseeAvecPlacePubliee(P1, lundi.plusDays(1), null, null, null, "Durand", "Alice"));
    }

    @Test
    void should_ignore_a_realised_seance_whose_patient_record_is_not_in_the_centre_of_the_seance() {
        LocalDate mardi = le("2026-04-07");
        patient(P1, CENTRE_A, "Nom01", "Pre");
        patient(P2, CENTRE_A, "Nom02", "Pre");
        seance(CENTRE_A, P1, mardi, "VALIDEE");
        seance(CENTRE_A, P2, mardi, "VALIDEE");
        seance(CENTRE_B, P2, mardi, "VALIDEE");
        ficheDansLeCentre(P1, CENTRE_B);

        assertThat(avecPlace().duCentre(CENTRE_A, mardi, mardi)).as("fiche de P1 passée au centre B (KB-SEA-80)")
                .extracting(SeanceRealiseeAvecPlacePubliee::patient).containsExactly(P2);
        assertThat(avecPlace().duCentre(CENTRE_B, mardi, mardi)).as("séance du centre B posée sur un patient du centre A").isEmpty();
    }

    @Test
    void should_order_two_realised_seances_of_a_patient_on_the_same_day_by_creation_then_identifier_with_unknown_creation_last() {
        LocalDate mardi = le("2026-04-07");
        patient(P1, CENTRE_A, "Nom07", "Pre");
        UUID salle3 = UUID.fromString("5e7dc002-0000-0000-0000-000000000003");
        UUID salle4 = UUID.fromString("5e7dc002-0000-0000-0000-000000000004");
        // insérées dans l'ordre inverse de l'ordre attendu : récente, sans création, égale à l'ancienne (départage par identifiant)
        UUID recente = UUID.fromString("5e7dc600-0000-0000-0000-000000000001");
        UUID sansCreation = UUID.fromString("5e7dc600-0000-0000-0000-000000000002");
        UUID ancienneB = UUID.fromString("5e7dc600-0000-0000-0000-000000000004");
        UUID ancienneA = UUID.fromString("5e7dc600-0000-0000-0000-000000000003");
        seance(recente, CENTRE_A, P1, mardi, "VALIDEE", DIX_HEURES.plusSeconds(60));
        place(recente, SALLE_2, CRENEAU, GENERATEUR);
        seance(sansCreation, CENTRE_A, P1, mardi, "SIGNEE", null);
        place(sansCreation, salle4, CRENEAU, GENERATEUR);
        seance(ancienneB, CENTRE_A, P1, mardi, "VALIDEE", DIX_HEURES);
        place(ancienneB, salle3, CRENEAU, GENERATEUR);
        seance(ancienneA, CENTRE_A, P1, mardi, "VALIDEE", DIX_HEURES);
        place(ancienneA, SALLE_1, CRENEAU, GENERATEUR);
        seance(CENTRE_A, P1, mardi.minusDays(1), "VALIDEE");

        List<SeanceRealiseeAvecPlacePubliee> seances = avecPlace().duCentre(CENTRE_A, mardi.minusDays(1), mardi);

        assertThat(seances).extracting(SeanceRealiseeAvecPlacePubliee::date)
                .containsExactly(mardi.minusDays(1), mardi, mardi, mardi, mardi);
        assertThat(seances.subList(1, 5)).as("la plus ancienne créée d'abord, création inconnue en dernier, identifiant en dernier ressort (KB-SEA-78 corrigé)")
                .extracting(SeanceRealiseeAvecPlacePubliee::salle).containsExactly(SALLE_1, salle3, SALLE_2, salle4);
    }

    @Test
    void should_read_the_first_and_last_names_of_the_record_raw() {
        LocalDate lundi = le("2026-03-02");
        patient(P1, CENTRE_A, "Durand", "");
        patient(P2, CENTRE_A, " Martin ", "   ");
        patient(P3, CENTRE_A, "Ben Ali", " Lina");
        seance(CENTRE_A, P1, lundi, "VALIDEE");
        seance(CENTRE_A, P2, lundi.plusDays(1), "VALIDEE");
        seance(CENTRE_A, P3, lundi.plusDays(2), "VALIDEE");

        assertThat(avecPlace().duCentre(CENTRE_A, lundi, lundi.plusDays(6)))
                .extracting(s -> "[" + s.prenom() + "|" + s.nom() + "]")
                .containsExactly("[|Durand]", "[   | Martin ]", "[ Lina|Ben Ali]");
    }

    // ---- D1, D2 : constantes des séances (historique du Dossier médical)

    @Test
    void should_count_every_seance_of_the_patient_in_the_centre_whatever_its_status() {
        seance(CENTRE_A, P4, le("2026-01-06"), "VALIDEE");
        seance(CENTRE_A, P4, le("2026-01-07"), "ANNULEE");
        seance(CENTRE_A, P4, le("2026-01-05"), "CREE");
        seance(CENTRE_A, P4, le("2026-01-08"), "validee");
        seance(CENTRE_B, P4, le("2026-01-06"), "VALIDEE");
        seance(CENTRE_A, P5, le("2026-01-06"), "VALIDEE");

        assertThat(constantes().nombreDeSeances(CENTRE_A, P4)).isEqualTo(4);
        assertThat(constantes().nombreDeSeances(CENTRE_B, P4)).as("aucune fiche patient exigée").isEqualTo(1);
        assertThat(constantes().nombreDeSeances(CENTRE_A, PB)).isZero();
    }

    @Test
    void should_order_by_date_then_creation_then_identifier_descending_as_unsigned_bytes_and_cut_pages_through_a_tie() {
        UUID annulee = UUID.fromString("5e7dc500-0000-0000-0000-000000000001");
        UUID creee = UUID.fromString("5e7dc500-0000-0000-0000-000000000002");
        seance(EGAL_A, CENTRE_A, P4, le("2026-01-06"), "VALIDEE", DIX_HEURES);
        seance(EGAL_B, CENTRE_A, P4, le("2026-01-06"), "VALIDEE", DIX_HEURES);
        seance(EGAL_C, CENTRE_A, P4, le("2026-01-06"), "VALIDEE", DIX_HEURES);
        seance(annulee, CENTRE_A, P4, le("2026-01-07"), "ANNULEE", DIX_HEURES);
        seance(creee, CENTRE_A, P4, le("2026-01-05"), "CREE", DIX_HEURES);

        assertThat(constantes().page(CENTRE_A, P4, 20, 0)).extracting(ConstantesDeSeancePubliees::seance)
                .containsExactly(annulee, EGAL_B, EGAL_A, EGAL_C, creee);
        assertThat(constantes().page(CENTRE_A, P4, 2, 2)).extracting(ConstantesDeSeancePubliees::seance).containsExactly(EGAL_A, EGAL_C);
        assertThat(constantes().page(CENTRE_A, P4, 2, 4)).extracting(ConstantesDeSeancePubliees::seance).containsExactly(creee);
        assertThat(constantes().page(CENTRE_A, P4, 2, 6)).isEmpty();
    }

    @Test
    void should_rank_a_null_creation_first_in_descending_order_on_every_database() {
        UUID nulle = UUID.fromString("5e7dc200-0000-0000-0000-000000000001");
        UUID fixe = UUID.fromString("5e7dc200-0000-0000-0000-000000000002");
        UUID tard = UUID.fromString("5e7dc200-0000-0000-0000-000000000003");
        seance(nulle, CENTRE_A, P5, le("2026-01-06"), "VALIDEE", null);
        seance(fixe, CENTRE_A, P5, le("2026-01-06"), "SIGNEE", DIX_HEURES);
        seance(tard, CENTRE_A, P5, le("2026-01-06"), "VALIDEE", DIX_HEURES.plusSeconds(3600));

        List<UUID> ordre = constantes().page(CENTRE_A, P5, 20, 0).stream().map(ConstantesDeSeancePubliees::seance).toList();

        assertThat(ordre).as("KB-SEA-79 corrigé : NULLS FIRST explicite, H2 aligné sur PostgreSQL 16").containsExactly(nulle, tard, fixe);
    }

    @Test
    void should_join_the_paramedical_sheet_of_the_same_centre_only_with_raw_measures_and_nulls_otherwise() {
        UUID complete = UUID.fromString("5e7dc300-0000-0000-0000-000000000001");
        UUID sansVolet = UUID.fromString("5e7dc300-0000-0000-0000-000000000002");
        UUID voletAilleurs = UUID.fromString("5e7dc300-0000-0000-0000-000000000003");
        UUID partielle = UUID.fromString("5e7dc300-0000-0000-0000-000000000004");
        seance(complete, CENTRE_A, P4, le("2026-01-10"), "VALIDEE", DIX_HEURES);
        seance(sansVolet, CENTRE_A, P4, le("2026-01-09"), "CREE", DIX_HEURES);
        seance(voletAilleurs, CENTRE_A, P4, le("2026-01-08"), "VALIDEE", DIX_HEURES);
        seance(partielle, CENTRE_A, P4, le("2026-01-07"), "SIGNEE", DIX_HEURES);
        volet(CENTRE_A, complete, new BigDecimal("0.00"), new BigDecimal("-3.00"), "n/a", "12/8", -5, new BigDecimal("-100.00"), 0);
        volet(CENTRE_B, voletAilleurs, new BigDecimal("70.50"), null, "130/80", null, 300, null, 240);
        volet(CENTRE_A, partielle, new BigDecimal("71.00"), null, null, null, null, null, null);

        List<ConstantesDeSeancePubliees> page = constantes().page(CENTRE_A, P4, 20, 0);

        assertThat(page).extracting(ConstantesDeSeancePubliees::seance).containsExactly(complete, sansVolet, voletAilleurs, partielle);
        ConstantesDeSeancePubliees brute = page.get(0);
        assertThat(brute.date()).isEqualTo(le("2026-01-10"));
        assertThat(brute.poidsAvantKg()).isEqualByComparingTo("0");
        assertThat(brute.poidsApresKg()).isEqualByComparingTo("-3");
        assertThat(brute.tensionAvant()).isEqualTo("n/a");
        assertThat(brute.tensionApres()).isEqualTo("12/8");
        assertThat(brute.debitSangMlMin()).isEqualTo(-5);
        assertThat(brute.ultrafiltrationMl()).isEqualByComparingTo("-100");
        assertThat(brute.dureeMinutes()).isZero();
        assertThat(page.get(1)).isEqualTo(new ConstantesDeSeancePubliees(sansVolet, le("2026-01-09"), null, null, null, null, null, null, null));
        assertThat(page.get(2)).as("volet posé sous un autre centre : ignoré")
                .isEqualTo(new ConstantesDeSeancePubliees(voletAilleurs, le("2026-01-08"), null, null, null, null, null, null, null));
        assertThat(page.get(3).poidsAvantKg()).isEqualByComparingTo("71");
        assertThat(page.get(3).dureeMinutes()).isNull();
        assertThat(constantes().page(CENTRE_B, P4, 20, 0)).isEmpty();
    }
}
