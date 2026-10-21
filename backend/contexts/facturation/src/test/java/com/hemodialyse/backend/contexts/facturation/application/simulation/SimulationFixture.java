package com.hemodialyse.backend.contexts.facturation.application.simulation;

import com.hemodialyse.backend.contexts.facturation.application.cloture.GardeDesMoisClos;
import com.hemodialyse.backend.contexts.facturation.doubles.AbsencesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.MoisDeFacturationEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.ParametresEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.SequenceEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.SeancesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.doubles.UniteDeTravailDirecte;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Décor commun des tests de cas d'usage de la simulation : doubles en mémoire, horloge fixe (20 octobre 2026), un centre, deux
 * patients, deux forfaits, une prise en charge par patient et des séances d'août 2026.
 */
class SimulationFixture {

    static final CentreId CENTRE = new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000a"));
    static final CentreId AUTRE_CENTRE = new CentreId(UUID.fromString("99996800-0000-0000-0000-00000000000b"));
    static final UUID AMRANI = UUID.fromString("99996800-0000-0000-0000-00000000a001");
    static final UUID BELKACEM = UUID.fromString("99996800-0000-0000-0000-00000000a002");
    static final UUID FORFAIT_HD = UUID.fromString("99996800-0000-0000-0000-0000000f0001");
    static final UUID FORFAIT_PREMIUM = UUID.fromString("99996800-0000-0000-0000-0000000f0002");
    static final UUID UTILISATEUR = UUID.fromString("99996800-0000-0000-0000-00000000c001");
    static final Instant MAINTENANT = Instant.parse("2026-10-20T08:00:00Z");
    static final DemandeDeSimulation AOUT = new DemandeDeSimulation(YearMonth.of(2026, 8), null, null, true);

    final Clock horloge = Clock.fixed(MAINTENANT, ZoneOffset.UTC);
    final SeancesEnMemoire seances = new SeancesEnMemoire();
    final ParametresEnMemoire parametres = new ParametresEnMemoire();
    final SequenceEnMemoire sequence = new SequenceEnMemoire();
    final AbsencesEnMemoire absences = new AbsencesEnMemoire();
    final UniteDeTravailDirecte unite = new UniteDeTravailDirecte();
    final MoisDeFacturationEnMemoire moisDeFacturation = new MoisDeFacturationEnMemoire();
    final GardeDesMoisClos garde = new GardeDesMoisClos(moisDeFacturation);
    final List<LocalDate> datesDeTvaDemandees = new ArrayList<>();
    TauxDeTva tva = new TauxDeTva(new BigDecimal("9"));

    final TauxDeTvaApplicable tauxDeTva = (centre, date) -> {
        datesDeTvaDemandees.add(date);
        return tva;
    };
    final SimulerLaFacturation simuler = new SimulerLaFacturation(seances, parametres, sequence, tauxDeTva, unite, horloge);

    SimulationFixture() {
        seances.forfait(FORFAIT_HD, CENTRE, "Forfait HD", new BigDecimal("3500.00"));
        seances.forfait(FORFAIT_PREMIUM, CENTRE, "Forfait HD Premium", new BigDecimal("5200.00"));
        seances.patient(AMRANI, CENTRE, "P-001", "Amrani", "Lina", "PERMANENT");
        seances.patient(BELKACEM, CENTRE, "P-002", "Belkacem", "Omar", "PERMANENT");
        ForfaitApplique hd = new ForfaitApplique(FORFAIT_HD, "Forfait HD", new BigDecimal("3500.00"));
        for (UUID patient : List.of(AMRANI, BELKACEM)) {
            seances.priseEnCharge(patient, new PriseEnChargeTarifee(hd, true, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                    null, null, Instant.parse("2026-01-01T08:00:00Z")));
        }
        sequence.poser(CENTRE, 2026, 41);
    }

    UUID seance(UUID patient, int jourDAout, String statut) {
        UUID id = UUID.randomUUID();
        seances.seance(id, CENTRE, patient, LocalDate.of(2026, 8, jourDAout), statut, false);
        return id;
    }
}
