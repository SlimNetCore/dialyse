package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies.SeanceExcluablePublie;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesAFacturerPubliees.SeanceAFacturerPubliee;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDesFacturesPubliees;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrat commun des lectures et écritures publiées de la Séance pour la Facturation (ADR 0097, lot c2a) : joué par le double en mémoire,
 * l'adapter JDBC sur H2 et sur PostgreSQL 16 jetable. Il décrit le comportement REPRODUIT du legacy (statut en casse exacte KB-SEA-73,
 * mémoire d'exclusion blanche KB-SEA-72, {@code forfait_override_code} non écrit KB-SEA-47, deux définitions de « facturée » KB-SEA-75).
 * {@code lierALAbsence} sur une séance facturée (KB-SEA-71) est traité au lot c2b.
 */
public abstract class FacturationDesSeancesContrat {

    protected static final UUID CENTRE_A = UUID.fromString("99997700-0000-0000-0000-00000000000a");
    protected static final UUID CENTRE_B = UUID.fromString("99997700-0000-0000-0000-00000000000b");
    protected static final UUID AMRANI = UUID.fromString("99997700-0000-0000-0000-00000000a001");
    protected static final UUID BELKACEM = UUID.fromString("99997700-0000-0000-0000-00000000a002");
    protected static final UUID PATIENT_B = UUID.fromString("99997700-0000-0000-0000-00000000b001");
    protected static final UUID HD = UUID.fromString("99997700-0000-0000-0000-0000000f0001");
    protected static final UUID PREMIUM = UUID.fromString("99997700-0000-0000-0000-0000000f0002");
    private static final LocalDate DEBUT = LocalDate.of(2026, 8, 1);
    private static final LocalDate FIN = LocalDate.of(2026, 8, 31);
    private static final Instant LE_20 = Instant.parse("2026-10-20T08:00:00Z");

    protected abstract SeancesAFacturerPubliees aFacturer();

    protected abstract ExclusionsEtForfaitsDesSeancesPublies exclusions();

    protected abstract SeancesDesFacturesPubliees facturees();

    protected abstract void patient(UUID id, UUID centre, String code, String nom, String prenom, String etat);

    /** Donne au patient son centre payeur et l'agence de celui-ci ; son numéro d'assurance est {@code ASS-<code>}. */
    protected abstract void payeur(UUID patient, UUID centre, UUID centrePayeur, UUID agence);

    protected abstract void seance(UUID id, UUID centre, UUID patient, LocalDate date, String statut, UUID facture);

    protected abstract void surcharge(UUID seance, UUID forfait, String nom, BigDecimal prix, String code);

    /** Pose la mémoire d'exclusion telle quelle (nulle, blanche ou non). */
    protected abstract void memoire(UUID seance, String statutAvant);

    protected abstract String statutDe(UUID seance);

    protected abstract String codeDuForfaitDe(UUID seance);

    protected abstract String memoireDe(UUID seance);

    protected abstract UUID absenceDe(UUID seance);

    protected void decor() {
        patient(AMRANI, CENTRE_A, "P-001", "Amrani", "Lina", "PERMANENT");
        patient(BELKACEM, CENTRE_A, "P-002", "Belkacem", "Omar", null);
        patient(PATIENT_B, CENTRE_B, "PB-01", "Zerrouki", "Rania", "PERMANENT");
    }

    private UUID uneSeance(UUID centre, UUID patient, String jour, String statut, boolean facturee) {
        UUID id = UUID.randomUUID();
        seance(id, centre, patient, LocalDate.parse(jour), statut, facturee ? UUID.randomUUID() : null);
        return id;
    }

    private List<UUID> identifiants(UUID centre) {
        return aFacturer().dansLaPeriode(centre, DEBUT, FIN).stream().map(SeanceAFacturerPubliee::id).toList();
    }

    // ---- séances à facturer

    @Test
    void should_read_only_validated_or_signed_seances_not_invoiced_of_the_centre_within_the_period() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID signee = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "SIGNEE", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-04", "BROUILLON", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-05", "ABSENT", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-06", "validee", false);
        uneSeance(CENTRE_A, AMRANI, "2026-08-07", "VALIDEE", true);
        uneSeance(CENTRE_A, AMRANI, "2026-07-31", "VALIDEE", false);
        uneSeance(CENTRE_A, AMRANI, "2026-09-01", "VALIDEE", false);
        uneSeance(CENTRE_B, PATIENT_B, "2026-08-02", "VALIDEE", false);

        assertThat(identifiants(CENTRE_A)).containsExactly(validee, signee);
    }

    @Test
    void should_include_the_first_and_the_last_day_of_the_period() {
        decor();
        UUID premier = uneSeance(CENTRE_A, AMRANI, "2026-08-01", "VALIDEE", false);
        UUID dernier = uneSeance(CENTRE_A, AMRANI, "2026-08-31", "SIGNEE", false);

        assertThat(identifiants(CENTRE_A)).containsExactly(premier, dernier);
    }

    @Test
    void should_order_the_seances_by_patient_name_then_date() {
        decor();
        UUID belkacem = uneSeance(CENTRE_A, BELKACEM, "2026-08-01", "VALIDEE", false);
        UUID amraniTard = uneSeance(CENTRE_A, AMRANI, "2026-08-09", "VALIDEE", false);
        UUID amraniTot = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "VALIDEE", false);

        assertThat(identifiants(CENTRE_A)).containsExactly(amraniTot, amraniTard, belkacem);
    }

    @Test
    void should_read_the_patient_the_payer_and_the_imposed_forfait_of_each_seance() {
        decor();
        UUID centrePayeur = UUID.fromString("99997700-0000-0000-0000-000000000c01");
        UUID agence = UUID.fromString("99997700-0000-0000-0000-000000000a61");
        payeur(AMRANI, CENTRE_A, centrePayeur, agence);
        UUID avec = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        surcharge(avec, PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"), "F-PREM");
        uneSeance(CENTRE_A, BELKACEM, "2026-08-03", "SIGNEE", false);

        List<SeanceAFacturerPubliee> lues = aFacturer().dansLaPeriode(CENTRE_A, DEBUT, FIN);

        SeanceAFacturerPubliee premiere = lues.get(0);
        assertThat(premiere.id()).isEqualTo(avec);
        assertThat(premiere.date()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(premiere.statut()).isEqualTo("VALIDEE");
        assertThat(premiere.forfaitImposeId()).isEqualTo(PREMIUM);
        assertThat(premiere.libelleImpose()).isEqualTo("Forfait HD Premium");
        assertThat(premiere.prixHtImpose()).isEqualByComparingTo("5200.00");
        assertThat(premiere.patient().id()).isEqualTo(AMRANI);
        assertThat(premiere.patient().code()).isEqualTo("P-001");
        assertThat(premiere.patient().nom()).isEqualTo("Amrani");
        assertThat(premiere.patient().prenom()).isEqualTo("Lina");
        assertThat(premiere.patient().etat()).isEqualTo("PERMANENT");
        assertThat(premiere.patient().numeroAssurance()).isEqualTo("ASS-P-001");
        assertThat(premiere.patient().centrePayeur()).isEqualTo(centrePayeur);
        assertThat(premiere.patient().agence()).isEqualTo(agence);
        SeanceAFacturerPubliee seconde = lues.get(1);
        assertThat(seconde.forfaitImposeId()).isNull();
        assertThat(seconde.libelleImpose()).isNull();
        assertThat(seconde.prixHtImpose()).isNull();
        assertThat(seconde.patient().etat()).isNull();
        assertThat(seconde.patient().centrePayeur()).isNull();
        assertThat(seconde.patient().agence()).isNull();
    }

    @Test
    void should_not_read_a_seance_whose_patient_belongs_to_another_centre() {
        decor();
        uneSeance(CENTRE_A, PATIENT_B, "2026-08-02", "VALIDEE", false);

        assertThat(identifiants(CENTRE_A)).isEmpty();
    }

    // ---- trouver

    @Test
    void should_find_a_seance_of_the_centre_with_its_state() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);

        SeanceExcluablePublie trouvee = exclusions().trouver(CENTRE_A, id).orElseThrow();

        assertThat(trouvee.id()).isEqualTo(id);
        assertThat(trouvee.patient()).isEqualTo(AMRANI);
        assertThat(trouvee.date()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(trouvee.statut()).isEqualTo("VALIDEE");
        assertThat(trouvee.facturee()).isFalse();
        assertThat(trouvee.statutAvantExclusion()).isNull();
        assertThat(trouvee.absenceDeLExclusion()).isNull();
    }

    @Test
    void should_not_find_an_unknown_seance_nor_one_of_another_centre_and_see_an_invoiced_one_as_invoiced() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "FACTUREE", true);

        assertThat(exclusions().trouver(CENTRE_A, UUID.randomUUID())).isEmpty();
        assertThat(exclusions().trouver(CENTRE_B, id)).isEmpty();
        assertThat(exclusions().trouver(CENTRE_A, id).orElseThrow().facturee()).isTrue();
    }

    @Test
    void should_read_a_blank_memory_of_the_exclusion_as_absent() {
        decor();
        for (String blanc : new String[]{"", "   "}) {
            UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "ABSENT", false);
            memoire(id, blanc);

            assertThat(exclusions().trouver(CENTRE_A, id).orElseThrow().statutAvantExclusion()).isNull();
        }
    }

    // ---- exclusion et réinclusion

    @Test
    void should_exclude_a_seance_remember_its_status_and_remove_it_from_the_seances_to_invoice() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "SIGNEE", false);

        exclusions().exclure(CENTRE_A, id, "SIGNEE");

        SeanceExcluablePublie exclue = exclusions().trouver(CENTRE_A, id).orElseThrow();
        assertThat(exclue.statut()).isEqualTo("ABSENT");
        assertThat(exclue.statutAvantExclusion()).isEqualTo("SIGNEE");
        assertThat(identifiants(CENTRE_A)).isEmpty();
    }

    @Test
    void should_refuse_to_exclude_a_seance_in_another_status_invoiced_unknown_or_of_another_centre() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID facturee = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "VALIDEE", true);

        assertThatThrownBy(() -> exclusions().exclure(CENTRE_A, validee, "SIGNEE")).isInstanceOf(IllegalStateException.class)
                .hasMessage("Seance introuvable ou non modifiable pour exclusion");
        assertThatThrownBy(() -> exclusions().exclure(CENTRE_A, facturee, "VALIDEE")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exclusions().exclure(CENTRE_A, UUID.randomUUID(), "VALIDEE")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exclusions().exclure(CENTRE_B, validee, "VALIDEE")).isInstanceOf(IllegalStateException.class);
        assertThat(statutDe(validee)).isEqualTo("VALIDEE");
    }

    @Test
    void should_exclude_a_seance_whose_stored_status_is_in_lower_case_when_given_that_exact_status() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "validee", false);

        assertThatThrownBy(() -> exclusions().exclure(CENTRE_A, id, "VALIDEE")).isInstanceOf(IllegalStateException.class);
        exclusions().exclure(CENTRE_A, id, "validee");

        assertThat(statutDe(id)).isEqualTo("ABSENT");
        assertThat(memoireDe(id)).isEqualTo("validee");
    }

    @Test
    void should_remember_the_absence_declared_by_the_exclusion() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID absence = UUID.randomUUID();
        exclusions().exclure(CENTRE_A, id, "VALIDEE");

        exclusions().lierALAbsence(CENTRE_A, id, absence);

        assertThat(exclusions().trouver(CENTRE_A, id).orElseThrow().absenceDeLExclusion()).isEqualTo(absence);
    }

    @Test
    void should_refuse_to_link_an_absence_to_a_seance_that_was_not_excluded() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID absenteReelle = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "ABSENT", false);
        UUID facturee = uneSeance(CENTRE_A, AMRANI, "2026-08-04", "FACTUREE", true);
        memoire(facturee, "VALIDEE");

        assertThatThrownBy(() -> exclusions().lierALAbsence(CENTRE_A, validee, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exclusions().lierALAbsence(CENTRE_A, absenteReelle, UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exclusions().lierALAbsence(CENTRE_A, facturee, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exclusions().lierALAbsence(CENTRE_B, validee, UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void should_reinclude_an_excluded_seance_with_its_former_status_and_forget_the_exclusion() {
        decor();
        UUID id = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "SIGNEE", false);
        exclusions().exclure(CENTRE_A, id, "SIGNEE");
        exclusions().lierALAbsence(CENTRE_A, id, UUID.randomUUID());

        exclusions().reinclure(CENTRE_A, id, "SIGNEE");

        SeanceExcluablePublie rendue = exclusions().trouver(CENTRE_A, id).orElseThrow();
        assertThat(rendue.statut()).isEqualTo("SIGNEE");
        assertThat(rendue.statutAvantExclusion()).isNull();
        assertThat(rendue.absenceDeLExclusion()).isNull();
        assertThat(identifiants(CENTRE_A)).containsExactly(id);
    }

    @Test
    void should_refuse_to_reinclude_a_seance_that_was_not_excluded_invoiced_unknown_or_of_another_centre() {
        decor();
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        UUID absenteReelle = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "ABSENT", false);
        UUID absenteFacturee = uneSeance(CENTRE_A, AMRANI, "2026-08-04", "ABSENT", true);
        memoire(absenteFacturee, "VALIDEE");

        assertThatThrownBy(() -> exclusions().reinclure(CENTRE_A, validee, "VALIDEE")).isInstanceOf(IllegalStateException.class)
                .hasMessage("Seance introuvable ou non reinclusable");
        assertThatThrownBy(() -> exclusions().reinclure(CENTRE_A, absenteReelle, "VALIDEE")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exclusions().reinclure(CENTRE_A, absenteFacturee, "VALIDEE")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> exclusions().reinclure(CENTRE_A, UUID.randomUUID(), "VALIDEE")).isInstanceOf(IllegalStateException.class);
        assertThat(statutDe(absenteReelle)).isEqualTo("ABSENT");
        assertThat(statutDe(absenteFacturee)).isEqualTo("ABSENT");
    }

    @Test
    void should_let_the_writes_through_on_a_blank_memory_of_the_exclusion_as_the_legacy_does() {
        decor();
        UUID absence = UUID.randomUUID();
        UUID lie = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "ABSENT", false);
        memoire(lie, "  ");
        UUID rendue = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "ABSENT", false);
        memoire(rendue, "");

        exclusions().lierALAbsence(CENTRE_A, lie, absence);
        exclusions().reinclure(CENTRE_A, rendue, "VALIDEE");

        assertThat(absenceDe(lie)).isEqualTo(absence);
        assertThat(statutDe(rendue)).isEqualTo("VALIDEE");
        assertThat(memoireDe(rendue)).isNull();
    }

    // ---- forfait imposé

    @Test
    void should_impose_a_forfait_on_a_seance_and_read_it_back_without_writing_its_code() {
        decor();
        UUID avecCode = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        surcharge(avecCode, HD, "Forfait HD", new BigDecimal("3500.00"), "F-HD");
        UUID sansCode = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "SIGNEE", false);

        exclusions().poserLeForfait(CENTRE_A, avecCode, PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"), "medecin", LE_20);
        exclusions().poserLeForfait(CENTRE_A, sansCode, PREMIUM, "Forfait HD Premium", new BigDecimal("5200.00"), "medecin", LE_20);

        List<SeanceAFacturerPubliee> lues = aFacturer().dansLaPeriode(CENTRE_A, DEBUT, FIN);
        assertThat(lues).extracting(SeanceAFacturerPubliee::forfaitImposeId).containsExactly(PREMIUM, PREMIUM);
        assertThat(lues.get(0).libelleImpose()).isEqualTo("Forfait HD Premium");
        assertThat(lues.get(0).prixHtImpose()).isEqualByComparingTo("5200.00");
        assertThat(codeDuForfaitDe(avecCode)).as("KB-SEA-47 : le code périmé est conservé").isEqualTo("F-HD");
        assertThat(codeDuForfaitDe(sansCode)).as("KB-SEA-47 : le code reste nul").isNull();
    }

    @Test
    void should_refuse_to_impose_a_forfait_on_an_excluded_invoiced_unknown_foreign_or_lower_case_seance() {
        decor();
        UUID exclue = uneSeance(CENTRE_A, AMRANI, "2026-08-02", "VALIDEE", false);
        exclusions().exclure(CENTRE_A, exclue, "VALIDEE");
        UUID facturee = uneSeance(CENTRE_A, AMRANI, "2026-08-03", "VALIDEE", true);
        UUID minuscule = uneSeance(CENTRE_A, AMRANI, "2026-08-05", "signee", false);
        UUID validee = uneSeance(CENTRE_A, AMRANI, "2026-08-04", "VALIDEE", false);

        for (UUID refusee : new UUID[]{exclue, facturee, minuscule, UUID.randomUUID()}) {
            assertThatThrownBy(() -> exclusions().poserLeForfait(CENTRE_A, refusee, PREMIUM, "Forfait HD Premium",
                    new BigDecimal("5200.00"), "medecin", LE_20))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Seance introuvable ou non modifiable pour changement forfait");
        }
        assertThatThrownBy(() -> exclusions().poserLeForfait(CENTRE_B, validee, PREMIUM, "Forfait HD Premium",
                new BigDecimal("5200.00"), "medecin", LE_20)).isInstanceOf(IllegalStateException.class);
    }

    // ---- séances facturées, deux définitions (KB-SEA-75)

    @Test
    void should_count_the_seances_of_each_invoice_whatever_their_status_and_date() {
        decor();
        UUID f1 = UUID.randomUUID();
        UUID f2 = UUID.randomUUID();
        UUID vide = UUID.randomUUID();
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 2), "FACTUREE", f1);
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 9, 2), "FACTUREE", f1);
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 3), "VALIDEE", f1);
        seance(UUID.randomUUID(), CENTRE_A, BELKACEM, LocalDate.of(2026, 8, 4), "ABSENT", f2);
        seance(UUID.randomUUID(), CENTRE_A, BELKACEM, LocalDate.of(2026, 8, 5), "VALIDEE", null);
        seance(UUID.randomUUID(), CENTRE_B, PATIENT_B, LocalDate.of(2026, 8, 6), "FACTUREE", f2);

        Map<UUID, Long> nombres = facturees().nombreParFacture(CENTRE_A, List.of(f1, f2, vide));

        assertThat(nombres).containsOnly(Map.entry(f1, 3L), Map.entry(f2, 1L));
        assertThat(facturees().nombreParFacture(CENTRE_A, List.of())).isEmpty();
        assertThat(facturees().nombreParFacture(CENTRE_B, List.of(f1, f2))).containsOnly(Map.entry(f2, 1L));
    }

    @Test
    void should_count_the_seances_of_a_large_number_of_invoices() {
        decor();
        Collection<UUID> factures = IntStream.range(0, 1201).mapToObj(i -> UUID.randomUUID()).toList();
        factures.stream().limit(3).forEach(f -> seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 2), "FACTUREE", f));
        UUID derniere = factures.stream().skip(1200).findFirst().orElseThrow();
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 3), "FACTUREE", derniere);
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 4), "FACTUREE", derniere);

        Map<UUID, Long> nombres = facturees().nombreParFacture(CENTRE_A, factures);

        assertThat(nombres).hasSize(4);
        assertThat(nombres.get(derniere)).isEqualTo(2L);
    }

    @Test
    void should_count_the_seances_at_the_status_invoiced_by_their_date_with_or_without_invoice() {
        decor();
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 1), "FACTUREE", UUID.randomUUID());
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 31), "FACTUREE", null);
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 15), "facturee", UUID.randomUUID());
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 8, 16), "VALIDEE", UUID.randomUUID());
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 7, 31), "FACTUREE", UUID.randomUUID());
        seance(UUID.randomUUID(), CENTRE_A, AMRANI, LocalDate.of(2026, 9, 1), "FACTUREE", UUID.randomUUID());
        seance(UUID.randomUUID(), CENTRE_B, PATIENT_B, LocalDate.of(2026, 8, 10), "FACTUREE", UUID.randomUUID());

        assertThat(facturees().nombreDeSeancesFactureesEntre(CENTRE_A, DEBUT, FIN)).isEqualTo(2L);
        assertThat(facturees().nombreDeSeancesFactureesEntre(CENTRE_A, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 31))).isZero();
    }
}
