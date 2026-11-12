package com.hemodialyse.backend.contexts.pilotagedirection.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Port sortant (PL10b2/PL10c, ADR 0152) : la synthèse du parc d'équipements d'un centre et le nombre de patients rattachés à un
 * générateur indisponible, lus dans les langages publiés de la GMAO (gmao.v1) et de la Planification. Lecture seule, sans SQL.
 */
public interface GmaoDesCentres {

    /** Le parc du centre et ses coûts de maintenance sur [du, au[. */
    SyntheseGmao duCentre(UUID centre, Instant du, Instant au);

    /** Les patients actuellement rattachés à un générateur indisponible (en maintenance, en attente de pièce, hors service, à réformer, réformé). */
    long patientsSurGenerateurIndisponible(UUID centre);

    record SyntheseGmao(long nombreDEquipements, long horsService, long enMaintenance, long reformes, long interventionsEnCours,
                        BigDecimal coutDeMaintenance, double indisponibiliteHeures, List<EquipementDuParc> equipements) {
    }

    record EquipementDuParc(UUID id, String code, String designation, String statut, BigDecimal coutDeLaPeriode,
                            BigDecimal coutCumule, BigDecimal prixAcquisition, BigDecimal ratioMaintenance,
                            double indisponibiliteHeures, boolean reformeRecommandee) {
    }
}
