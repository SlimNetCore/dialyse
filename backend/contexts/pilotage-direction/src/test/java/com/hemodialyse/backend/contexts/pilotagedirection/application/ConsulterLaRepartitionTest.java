package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.CentreDeLaSociete;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ChiffresFinanciers;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.EffectifDuCentre;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeChiffres;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.MoisDeSeances;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.Periode;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.AdministrationsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ChiffreDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PatientsDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.PrescriptionsDeLAnemie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.ProfilDUnPatient;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.RepartitionDeLaSociete.SeancesDeLaCaisse;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.SocieteSuivie;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TrancheDAge;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * KB-DIR-08 (lot L6) : les tranches d'âge sont calculées À LA DATE DE FIN DE LA PÉRIODE demandée, et non au jour de l'appel. Horloge
 * injectée fixe (2026-11-10) : le test ne dépend pas de l'heure réelle.
 */
class ConsulterLaRepartitionTest {

    private static final UUID SOCIETE = UUID.randomUUID();
    private static final UUID CENTRE = UUID.randomUUID();
    private static final HorlogeDuPilotage HORLOGE = new HorlogeDuPilotage(Clock.fixed(Instant.parse("2026-11-10T10:00:00Z"), ZoneOffset.UTC));

    private ConsulterLaRepartition casAvecLesNaissances(LocalDate... naissances) {
        SocietesSuivies societes = id -> new SocieteSuivie(SOCIETE, "Société", List.of(new CentreDeLaSociete(CENTRE, "Centre", true)));
        EffectifsDesPatients effectifs = new EffectifsDesPatients() {
            public Map<UUID, EffectifDuCentre> parCentre(List<UUID> centres, Periode periode) {
                return Map.of();
            }

            public List<ProfilDUnPatient> profils(List<UUID> centres, Periode periode) {
                return java.util.Arrays.stream(naissances).map(n -> new ProfilDUnPatient(CENTRE, "M", n)).toList();
            }

            public List<PatientsDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
                return List.of();
            }

            public Map<UUID, PrescriptionsDeLAnemie> prescriptionsDeLAnemie(List<UUID> centres, Periode periode) {
                return Map.of();
            }
        };
        SeancesRealiseesDesCentres seances = new SeancesRealiseesDesCentres() {
            public Map<UUID, Long> parCentre(List<UUID> centres, Periode periode) {
                return Map.of();
            }

            public long auTotal(List<UUID> centres, Periode periode) {
                return 0;
            }

            public List<MoisDeSeances> parMois(List<UUID> centres, Periode periode) {
                return List.of();
            }

            public List<SeancesDeLaCaisse> parCaisse(List<UUID> centres, Periode periode) {
                return List.of();
            }
        };
        FacturationDesCentres facturation = new FacturationDesCentres() {
            public Map<UUID, ChiffresDuCentre> parCentre(List<UUID> centres, Periode periode) {
                return Map.of();
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
        TraitementsDeLAnemieDesCentres anemie = (centres, periode) -> Map.<UUID, AdministrationsDeLAnemie>of();
        return new ConsulterLaRepartition(societes, effectifs, seances, facturation, anemie, HORLOGE);
    }

    private Map<TrancheDAge, Long> tranches(ConsulterLaRepartition cas, LocalDate debut, LocalDate fin) {
        return cas.execute(SOCIETE, debut, fin).profils().get(0).ages();
    }

    @Test
    void l_age_est_celui_du_dernier_jour_de_la_periode_demandee_meme_pour_une_periode_passee() {
        // né le 2008-06-01 : 17 ans le 2025-12-31 (0_17), 18 ans le 2026-11-10, jour de l'appel (18_29 avant la correction)
        var cas = casAvecLesNaissances(LocalDate.of(2008, 6, 1));

        assertThat(tranches(cas, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))).containsOnlyKeys(TrancheDAge.DE_0_A_17);
    }

    @Test
    void les_dix_huit_ans_revolus_le_dernier_jour_de_la_periode_sont_la_tranche_des_18_a_29_ans_la_veille_non() {
        var cas = casAvecLesNaissances(LocalDate.of(2007, 12, 31), LocalDate.of(2008, 1, 1));

        assertThat(tranches(cas, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)))
                .containsEntry(TrancheDAge.DE_0_A_17, 1L).containsEntry(TrancheDAge.DE_18_A_29, 1L);
    }

    @Test
    void sans_fin_demandee_la_fin_est_aujourd_hui_de_l_horloge_du_pilotage() {
        var cas = casAvecLesNaissances(LocalDate.of(2008, 11, 10), LocalDate.of(2008, 11, 11));

        assertThat(tranches(cas, null, null)).containsEntry(TrancheDAge.DE_18_A_29, 1L).containsEntry(TrancheDAge.DE_0_A_17, 1L);
    }

    @Test
    void une_naissance_absente_ou_posterieure_a_la_fin_de_la_periode_reste_inconnue() {
        var cas = casAvecLesNaissances(null, LocalDate.of(2026, 1, 1));

        // 2026-01-01 est postérieure à la fin 2025-12-31 (mais antérieure au jour de l'appel : INCONNUE seulement avec la correction)
        assertThat(tranches(cas, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31))).containsOnlyKeys(TrancheDAge.INCONNUE)
                .containsEntry(TrancheDAge.INCONNUE, 2L);
    }
}
