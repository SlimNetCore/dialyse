package com.hemodialyse.backend.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.planification.publishedlanguage.SeancesRealiseesAvecLeurPlaceFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesRealiseesAvecLeurPlacePubliees;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Fournisseur de la Planification (ADR 0098, SE7d) : les séances réalisées avec leur place passent par
 * {@link SeancesRealiseesAvecLeurPlacePubliees}. Logé dans la plateforme (cycle Maven : la Séance dépend, par Patient-couverture et les
 * Absences, de la Planification). Délégation pure, lecture seule, ordre conservé, nom et prénom bruts.
 */
@Component
public class SeancesRealiseesAvecLeurPlaceParLaSeance implements SeancesRealiseesAvecLeurPlaceFournies {

    private final SeancesRealiseesAvecLeurPlacePubliees seances;

    public SeancesRealiseesAvecLeurPlaceParLaSeance(SeancesRealiseesAvecLeurPlacePubliees seances) {
        this.seances = seances;
    }

    @Override
    public List<SeanceRealiseeAvecPlaceFournie> duCentre(UUID centre, LocalDate du, LocalDate au) {
        return seances.duCentre(centre, du, au).stream()
                .map(s -> new SeanceRealiseeAvecPlaceFournie(s.patient(), s.date(), s.salle(), s.creneau(), s.generateur(), s.nom(), s.prenom()))
                .toList();
    }
}
