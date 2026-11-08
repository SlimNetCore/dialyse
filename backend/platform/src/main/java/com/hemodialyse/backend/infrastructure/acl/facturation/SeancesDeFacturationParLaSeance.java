package com.hemodialyse.backend.infrastructure.acl.facturation;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.ExclusionsDeSeancesFournies;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ExclusionsEtForfaitsDesSeancesPublies;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Fournisseur de la Facturation (ADR 0097, SE7c2, ARGENT) : l'exclusion, la réinclusion et le forfait imposé d'une séance passent par
 * {@link ExclusionsEtForfaitsDesSeancesPublies} (seance.v1). Logé dans la plateforme (cycle Maven Séance - Absences-patients - Facturation).
 * Traduction champ à champ, aucune règle, aucun montant recalculé ; les refus (422) viennent du port de la Séance et traversent tels quels.
 */
@Component
public class SeancesDeFacturationParLaSeance implements ExclusionsDeSeancesFournies {

    private final ExclusionsEtForfaitsDesSeancesPublies seances;

    public SeancesDeFacturationParLaSeance(ExclusionsEtForfaitsDesSeancesPublies seances) {
        this.seances = seances;
    }

    @Override
    public Optional<SeanceExcluableFournie> trouver(UUID centre, UUID seance) {
        return seances.trouver(centre, seance).map(s -> new SeanceExcluableFournie(s.id(), s.patient(), s.date(), s.statut(),
                s.facturee(), s.statutAvantExclusion(), s.absenceDeLExclusion()));
    }

    @Override
    public void exclure(UUID centre, UUID seance, String statutAvant) {
        seances.exclure(centre, seance, statutAvant);
    }

    @Override
    public void lierALAbsence(UUID centre, UUID seance, UUID absence) {
        seances.lierALAbsence(centre, seance, absence);
    }

    @Override
    public void reinclure(UUID centre, UUID seance, String statutRetabli) {
        seances.reinclure(centre, seance, statutRetabli);
    }

    @Override
    public void poserLeForfait(UUID centre, UUID seance, UUID forfait, String libelle, BigDecimal prixHt, String auteur, Instant le) {
        seances.poserLeForfait(centre, seance, forfait, libelle, prixHt, auteur, le);
    }
}
