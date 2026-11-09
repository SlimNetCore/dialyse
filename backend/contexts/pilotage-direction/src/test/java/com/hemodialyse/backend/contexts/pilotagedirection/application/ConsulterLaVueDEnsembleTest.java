package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresFinanciers;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.EffectifDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeChiffres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeSeances;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RegleDuPilotageViolee;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ChiffreDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PatientsDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PrescriptionsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ProfilDUnPatient;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.SeancesDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConsulterLaVueDEnsembleTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final UUID A = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();
    private static final HorlogeDuPilotage HORLOGE =
            new HorlogeDuPilotage(Clock.fixed(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC));

    private final List<String> lectures = new ArrayList<>();

    private SocietesSuivies societes(List<CentreDeLaSociete> centres) {
        return id -> {
            lectures.add("societe");
            return new SocieteSuivie(SOCIETE, "Société", centres);
        };
    }

    private EffectifsDesPatients effectifs(Map<UUID, EffectifDuCentre> parCentre) {
        return new EffectifsDesPatients() {
            public Map<UUID, EffectifDuCentre> parCentre(List<UUID> centres, Periode periode) {
                return parCentre;
            }

            public List<ProfilDUnPatient> profils(List<UUID> centres, Periode periode) {
                return List.of();
            }

            public List<PatientsDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
                return List.of();
            }

            public Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(List<UUID> centres, Periode periode) {
                return Map.of();
            }
        };
    }

    private SeancesRealiseesDesCentres seances(Map<UUID, Long> parCentre) {
        return new SeancesRealiseesDesCentres() {
            public Map<UUID, Long> parCentre(List<UUID> centres, Periode periode) {
                return parCentre;
            }

            public long auTotal(List<UUID> centres, Periode periode) {
                return parCentre.values().stream().mapToLong(Long::longValue).sum();
            }

            public List<MoisDeSeances> parMois(List<UUID> centres, Periode periode) {
                return List.of();
            }

            public List<SeancesDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
                return List.of();
            }
        };
    }

    private FacturationDesCentres facturation() {
        return new FacturationDesCentres() {
            public Map<UUID, ChiffresDuCentre> parCentre(List<UUID> centres, Periode periode) {
                return Map.of(A, new ChiffresDuCentre(2, new ChiffresFinanciers(new BigDecimal("100.00"), new BigDecimal("119.00"),
                        new BigDecimal("19.00"))));
            }

            public ChiffresFinanciers cumules(List<UUID> centres, Periode periode) {
                return ChiffresFinanciers.zero();
            }

            public List<MoisDeChiffres> parMois(List<UUID> centres, Periode periode) {
                return List.of();
            }

            public List<ChiffreDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
                return List.of();
            }
        };
    }

    private ConsulterLaVueDEnsemble cas(List<CentreDeLaSociete> centres, Map<UUID, EffectifDuCentre> effectifs, Map<UUID, Long> seances) {
        return new ConsulterLaVueDEnsemble(societes(centres), effectifs(effectifs), seances(seances), facturation(), HORLOGE);
    }

    @Test
    void une_periode_invalide_est_refusee_avant_toute_lecture() {
        var cas = cas(List.of(), Map.of(), Map.of());

        assertThatThrownBy(() -> cas.execute(SOCIETE, LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1)))
                .isInstanceOf(RegleDuPilotageViolee.class);
        assertThat(lectures).isEmpty();
    }

    @Test
    void une_societe_sans_centre_a_une_vue_vide_sans_lire_les_activites() {
        var vue = cas(List.of(), Map.of(), Map.of()).execute(SOCIETE, null, null);

        assertThat(vue.centres()).isEmpty();
        assertThat(vue.periode().fin()).isEqualTo(LocalDate.of(2026, 11, 9));
        assertThat(vue.genereLe().toInstant()).isEqualTo(Instant.parse("2026-11-09T10:00:00Z"));
    }

    @Test
    void le_total_de_patients_est_supprime_quand_des_centres_sont_masques_et_les_autres_totaux_restent_des_sommes_kb_dir_01_corrige() {
        var centres = List.of(new CentreDeLaSociete(A, "Annaba", true), new CentreDeLaSociete(B, "Blida", true));
        var vue = cas(centres, Map.of(A, new EffectifDuCentre(3, 1), B, new EffectifDuCentre(4, 0)), Map.of(A, 10L, B, 5L))
                .execute(SOCIETE, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31));

        assertThat(vue.centres()).hasSize(2);
        assertThat(vue.centres().getFirst().patientsPublies()).isNull();
        assertThat(vue.totaux().patientsPublies()).isNull();
        assertThat(vue.totaux().patientsSousKtPublies()).isNull();
        assertThat(vue.totaux().seances()).isEqualTo(15);
        assertThat(vue.totaux().finances().caHt()).isEqualByComparingTo("100.00");
    }
}
