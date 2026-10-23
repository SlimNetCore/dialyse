package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.CentreId;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodeDeDetection;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Rattrapage d'une période passée demandé par l'administrateur (RG-ABS-046) : détecte les absences jamais détectées (serveur
 * arrêté, base reprise) et annule celles dont la séance a été saisie après coup. Idempotent, sans notification. La fin est ramenée
 * à hier au fuseau du centre et la réponse le signale.
 */
public class DetecterUnePeriodePassee {

    private final HorlogeDesCentres horloge;
    private final ReconciliationAbsences reconciliation;
    private final DetectionAbsences detection;

    public DetecterUnePeriodePassee(HorlogeDesCentres horloge, ReconciliationAbsences reconciliation,
                                    DetectionAbsences detection) {
        this.horloge = horloge;
        this.reconciliation = reconciliation;
        this.detection = detection;
    }

    /**
     * @param periode la période réellement contrôlée (fin ramenée à hier si besoin, {@code finRamenee})
     */
    public record Rattrapage(PeriodeDeDetection periode, BilanDuControle bilan) {
    }

    /**
     * @throws com.hemodialyse.backend.contexts.absencespatients.domain.RegleDAbsenceViolee {@code ABSENCE_PERIODE_INVALIDE} ou
     *                                                                                      {@code ABSENCE_PERIODE_TROP_LONGUE}
     */
    public Rattrapage executer(CentreId centre, LocalDate du, LocalDate au) {
        PeriodeDeDetection periode = PeriodeDeDetection.demandee(du, au, horloge.aujourdhui(centre));
        Instant maintenant = horloge.maintenant();
        BilanDuControle bilan = reconciliation.executer(centre, periode, maintenant)
                .avec(detection.executer(centre, periode, maintenant));
        return new Rattrapage(periode, bilan);
    }
}
