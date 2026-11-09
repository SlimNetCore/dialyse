package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** SE7f (ADR 0100) : règles du volet paramédical reprises du legacy (DM1 : poids bornés, tension normalisée, remplacement total, FACTUREE). */
class VoletParamedicalTest {

    private static final CentreId CENTRE = new CentreId(new UUID(0, 0xA));
    private static final SeanceId SEANCE = new SeanceId(new UUID(2, 1));
    private static final Instant T0 = Instant.parse("2026-06-15T10:00:00Z");

    private static Seance seance(StatutDeSeance statut) {
        return Seance.restituer(SEANCE, CENTRE, new PatientId(new UUID(1, 1)), LocalDate.of(2026, 6, 15), statut, T0, null, null);
    }

    private static MesuresDeSeance completes() {
        return MesuresDeSeance.saisies(new BigDecimal("70.5"), new BigDecimal("68.0"), "130/80", "120/70", 240, 300, new BigDecimal("2000"),
                "HEPARINE", "STANDARD", "RAS");
    }

    @Test
    void should_accept_the_paramedical_sheet_on_every_status_but_the_invoiced_one() {
        for (StatutDeSeance statut : new StatutDeSeance[]{StatutDeSeance.CREE, StatutDeSeance.VALIDEE, StatutDeSeance.SIGNEE, StatutDeSeance.ABSENT}) {
            assertThatCode(() -> seance(statut).exigerUnVoletParamedical()).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> seance(StatutDeSeance.FACTUREE).exigerUnVoletParamedical()).isInstanceOfSatisfying(SeanceRefusee.class, e -> {
            assertThat(e.motif().code()).isEqualTo("SEANCE_FACTUREE_NON_MODIFIABLE");
            assertThat(e.getMessage()).isEqualTo("La seance facturee ne peut plus etre modifiee");
        });
    }

    @Test
    void should_bound_a_weight_between_zero_excluded_and_500_kg_included_with_the_legacy_messages() {
        assertThatCode(() -> new Poids(new BigDecimal("0.001"))).doesNotThrowAnyException();
        assertThatCode(() -> new Poids(new BigDecimal("500"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> new Poids(BigDecimal.ZERO)).isInstanceOfSatisfying(SeanceRefusee.class, e -> {
            assertThat(e.motif().code()).isEqualTo("BUSINESS_ERROR");
            assertThat(e.getMessage()).isEqualTo("Le poids doit être strictement positif");
        });
        assertThatThrownBy(() -> new Poids(new BigDecimal("-1"))).hasMessage("Le poids doit être strictement positif");
        assertThatThrownBy(() -> new Poids(new BigDecimal("500.01"))).hasMessage("Le poids dépasse la borne plausible (500 kg)");
        assertThatThrownBy(() -> new Poids(new BigDecimal("99999999999"))).hasMessage("Le poids dépasse la borne plausible (500 kg)");
    }

    @Test
    void should_normalise_the_blood_pressure_as_the_legacy_including_the_cmhg_notation() {
        Map<String, String> normalisees = Map.ofEntries(Map.entry("130/80", "130/80"), Map.entry(" 120 / 80 ", "120/80"),
                Map.entry("12/8", "120/80"), Map.entry("11,5/7", "115/70"), Map.entry("12.5/8.5", "125/85"), Map.entry("19/10", "190/100"),
                Map.entry("1e2/80", "100/80"), Map.entry("20/20", "200/200"), Map.entry("30/20", "300/200"), Map.entry("30,5/20", "31/20"),
                Map.entry("31/20", "31/20"), Map.entry("400/200", "400/200"));
        normalisees.forEach((saisie, attendue) -> assertThat(TensionArterielle.lire(saisie).orElseThrow().texte()).as(saisie).isEqualTo(attendue));
        assertThat(TensionArterielle.lire(null)).isEmpty();
        assertThat(TensionArterielle.lire("   ")).isEmpty();
    }

    @Test
    void should_refuse_an_unreadable_or_out_of_range_blood_pressure_with_the_legacy_messages() {
        Map<String, String> refus = Map.ofEntries(
                Map.entry("-120/80", "Valeur de tension hors bornes physiologiques: -1200"),
                Map.entry("10/0", "Valeur de tension hors bornes physiologiques: 0"),
                Map.entry("120/130", "La systolique doit être supérieure ou égale à la diastolique"),
                Map.entry("120/80/60", "Format de tension artérielle invalide (attendu: systolique/diastolique)"),
                Map.entry("120", "Format de tension artérielle invalide (attendu: systolique/diastolique)"),
                Map.entry("120/xyz", "Valeur de tension non numérique: xyz"),
                Map.entry("abc/80", "Valeur de tension non numérique: abc"),
                Map.entry("401/200", "Valeur de tension hors bornes physiologiques: 401"));
        refus.forEach((saisie, message) -> assertThatThrownBy(() -> TensionArterielle.lire(saisie)).as(saisie)
                .isInstanceOfSatisfying(SeanceRefusee.class, e -> {
                    assertThat(e.motif().code()).isEqualTo("BUSINESS_ERROR");
                    assertThat(e.getMessage()).isEqualTo(message);
                }));
    }

    @Test
    void should_validate_the_measures_in_the_legacy_order_and_keep_a_blank_pressure_as_entered() {
        var mesures = MesuresDeSeance.saisies(null, null, "   ", "", -5, -1, new BigDecimal("-100"), null, null, null);
        assertThat(mesures.tensionAvant()).isEqualTo("   ");
        assertThat(mesures.tensionApres()).isEmpty();
        assertThat(mesures.dureeMinutes()).isEqualTo(-5);

        assertThatThrownBy(() -> MesuresDeSeance.saisies(BigDecimal.ZERO, null, "abc/80", null, null, null, null, null, null, null))
                .hasMessage("Le poids doit être strictement positif");
        assertThatThrownBy(() -> MesuresDeSeance.saisies(new BigDecimal("70"), BigDecimal.ZERO, "abc/80", null, null, null, null, null, null, null))
                .hasMessage("Le poids doit être strictement positif");
        assertThatThrownBy(() -> MesuresDeSeance.saisies(new BigDecimal("70"), null, "abc/80", "120/130", null, null, null, null, null, null))
                .hasMessage("Valeur de tension non numérique: abc");
        assertThat(MesuresDeSeance.restituees(BigDecimal.ZERO, null, "n'importe quoi", null, null, null, null, null, null, null).tensionAvant())
                .isEqualTo("n'importe quoi");
    }

    @Test
    void should_open_a_sheet_dated_once_then_replace_every_measure_keeping_identity_and_creation_date() {
        var volet = VoletParamedical.ouvrir(new UUID(8, 1), CENTRE, SEANCE, completes(), T0);
        assertThat(volet.creeLe()).isEqualTo(T0);
        assertThat(volet.modifieLe()).isEqualTo(T0);
        assertThat(volet.mesures().tensionAvant()).isEqualTo("130/80");

        var plusTard = T0.plusSeconds(3600);
        volet.remplacerLesMesures(MesuresDeSeance.saisies(new BigDecimal("71.2"), null, null, null, null, null, null, null, null, null), plusTard);

        assertThat(volet.id()).isEqualTo(new UUID(8, 1));
        assertThat(volet.seance()).isEqualTo(SEANCE);
        assertThat(volet.mesures().poidsAvantKg()).isEqualByComparingTo("71.2");
        assertThat(volet.mesures().anticoagulant()).isNull();
        assertThat(volet.creeLe()).isEqualTo(T0);
        assertThat(volet.modifieLe()).isEqualTo(plusTard);
    }

    @Test
    void should_refuse_a_sheet_without_identity_centre_session_measures_or_instant() {
        assertThatThrownBy(() -> VoletParamedical.ouvrir(null, CENTRE, SEANCE, completes(), T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> VoletParamedical.ouvrir(new UUID(8, 1), null, SEANCE, completes(), T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> VoletParamedical.ouvrir(new UUID(8, 1), CENTRE, null, completes(), T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> VoletParamedical.ouvrir(new UUID(8, 1), CENTRE, SEANCE, null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> VoletParamedical.ouvrir(new UUID(8, 1), CENTRE, SEANCE, completes(), null)).isInstanceOf(SaisieInvalide.class);
        var volet = VoletParamedical.ouvrir(new UUID(8, 1), CENTRE, SEANCE, completes(), T0);
        assertThatThrownBy(() -> volet.remplacerLesMesures(null, T0)).isInstanceOf(SaisieInvalide.class);
        assertThatThrownBy(() -> volet.remplacerLesMesures(MesuresDeSeance.aucune(), null)).isInstanceOf(SaisieInvalide.class);
    }
}
