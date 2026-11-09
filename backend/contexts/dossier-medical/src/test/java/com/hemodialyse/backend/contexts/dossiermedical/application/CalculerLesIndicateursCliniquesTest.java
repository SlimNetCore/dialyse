package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.MarqueurSerologique;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Indicateurs cliniques publiés avec un double du port de lectures : masque par centre, consolidés sur les bruts (KB-MED-104 reproduit). */
class CalculerLesIndicateursCliniquesTest {
    private static final UUID A = new UUID(0, 0xA);
    private static final UUID B = new UUID(0, 0xB);
    private static final LocalDate DU = LocalDate.of(2026, 1, 1);
    private static final LocalDate AU = LocalDate.of(2026, 12, 31);

    private final Map<LecturesDesIndicateursCliniques.Cible, Map<UUID, List<BigDecimal>>> valeurs = new HashMap<>();
    private final Map<MarqueurSerologique, Map<UUID, Long>> positifs = new HashMap<>();
    private final Map<UUID, Long> observance = new HashMap<>();
    private final Map<UUID, LecturesDesIndicateursCliniques.Greffes> greffes = new HashMap<>();

    private final CalculerLesIndicateursCliniques calcul = new CalculerLesIndicateursCliniques(new LecturesDesIndicateursCliniques() {
        @Override
        public Map<UUID, List<BigDecimal>> dernieresValeurs(Cible cible, List<UUID> centres, LocalDate du, LocalDate au) {
            return valeurs.getOrDefault(cible, Map.of());
        }

        @Override
        public Map<UUID, Long> porteursPositifs(MarqueurSerologique marqueur, List<UUID> centres, LocalDate au) {
            return positifs.getOrDefault(marqueur, Map.of());
        }

        @Override
        public Map<UUID, Long> patientsEnRetardDObservance(List<UUID> centres) {
            return observance;
        }

        @Override
        public Map<UUID, Greffes> greffes(List<UUID> centres, LocalDate du, LocalDate au) {
            return greffes;
        }
    });

    private static List<BigDecimal> hb(int n) {
        return java.util.stream.IntStream.range(0, n).mapToObj(i -> new BigDecimal("11")).collect(Collectors.toList());
    }

    @Test
    void sans_centre_les_consolides_sont_a_zero_et_aucun_centre_n_est_servi() {
        var r = calcul.desCentres(List.of(), DU, AU);
        assertThat(r.parCentre()).isEmpty();
        assertThat(r.consolides().hemoglobine().evalues()).isZero();
        assertThat(r.consolides().vhbPositifs()).isZero();
        assertThat(r.consolides().observanceEnRetard()).isFalse();
    }

    /** KB-MED-104 : A = 5 publié, B = 3 masqué, consolidés 8 : « B = 3 » se déduit ; les taux des consolidés portent sur 8. */
    @Test
    void un_centre_masque_est_deductible_des_consolides_kb_med_104() {
        valeurs.put(LecturesDesIndicateursCliniques.Cible.HEMOGLOBINE, Map.of(A, hb(5), B, hb(3)));
        positifs.put(MarqueurSerologique.AG_HBS, Map.of(A, 5L, B, 1L));
        observance.put(B, 2L);
        greffes.put(A, new LecturesDesIndicateursCliniques.Greffes(5, 0, 0));
        greffes.put(B, new LecturesDesIndicateursCliniques.Greffes(4, 0, 1));
        var r = calcul.desCentres(List.of(A, B), DU, AU);
        assertThat(r.parCentre().get(A).hemoglobine().evalues()).isEqualTo(5L);
        assertThat(r.parCentre().get(A).hemoglobine().pctDansCible()).isEqualByComparingTo("100.0");
        assertThat(r.parCentre().get(B).hemoglobine().evalues()).isNull();
        assertThat(r.parCentre().get(B).hemoglobine().pctDansCible()).isNull();
        assertThat(r.consolides().hemoglobine().evalues()).isEqualTo(8L);
        assertThat(r.parCentre().get(B).vhbPositifs()).isNull();
        assertThat(r.consolides().vhbPositifs()).isEqualTo(6L);
        assertThat(r.parCentre().get(B).patientsObservanceEnRetard()).isNull();
        assertThat(r.parCentre().get(B).observanceEnRetard()).isTrue();
        assertThat(r.parCentre().get(A).observanceEnRetard()).isFalse();
        assertThat(r.consolides().greffeListeAttente()).isEqualTo(9L);
        assertThat(r.parCentre().get(B).greffesPeriode()).isNull();
        assertThat(r.parCentre().get(A).greffesPeriode()).isZero();
    }

    @Test
    void un_centre_inconnu_des_lectures_est_ignore_et_un_centre_demande_sans_ligne_est_servi_a_zero() {
        valeurs.put(LecturesDesIndicateursCliniques.Cible.KT_V, Map.of(new UUID(0, 0x99), hb(7)));
        var r = calcul.desCentres(List.of(A), DU, AU);
        assertThat(r.parCentre()).containsOnlyKeys(A);
        assertThat(r.parCentre().get(A).ktV().evalues()).isZero();
        assertThat(r.consolides().ktV().evalues()).isZero();
    }
}
