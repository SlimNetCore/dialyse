package com.hemodialyse.backend.contexts.facturation.infrastructure.acl.seance;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.ForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PatientFacture;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PolitiqueDeForfaitApplique;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PriseEnChargeTarifee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.StatutDeSeance;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesAFacturerFournies.SeanceAFacturerFournie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ACL de la Facturation vers la Séance (ADR 0097, SE7c2, ARGENT) : les séances à facturer viennent de {@link SeancesAFacturerFournies}
 * (statut {@code VALIDEE} ou {@code SIGNEE} en casse exacte, KB-FAC-17 ; non facturées ; dans la période ; ordre inchangé). La POLITIQUE de
 * forfait (RG-FAC-021) reste ici, à la Facturation : forfait imposé d'abord, sinon prise en charge du patient lue UNE fois par patient par
 * {@link CouvertureDesPatients}. Aucun montant n'est recalculé : le prix imposé est copié à l'identique. Remplace {@code SeancesFacturablesJdbc}.
 */
public class SeancesFacturablesPubliees implements SeancesFacturables {

    private final SeancesAFacturerFournies seances;

    private final CouvertureDesPatients couverture;

    public SeancesFacturablesPubliees(SeancesAFacturerFournies seances, CouvertureDesPatients couverture) {
        this.seances = seances;
        this.couverture = couverture;
    }

    @Override
    public List<SeanceFacturable> dansLaPeriode(CentreId centre, PeriodeDeFacturation periode) {
        List<SeanceAFacturerFournie> lignes = seances.dansLaPeriode(centre.valeur(), periode.debut(), periode.fin());

        Map<UUID, List<PriseEnChargeTarifee>> prisesEnChargeParPatient = new HashMap<>();
        List<SeanceFacturable> resultat = new ArrayList<>();
        for (SeanceAFacturerFournie ligne : lignes) {
            PatientFacture patient = new PatientFacture(ligne.patient().id(), ligne.patient().code(), ligne.patient().nom(),
                    ligne.patient().prenom(), ligne.patient().etat(), ligne.patient().numeroAssurance(), ligne.patient().centrePayeur(),
                    ligne.patient().agence());
            Optional<ForfaitApplique> surcharge = ligne.forfaitImposeId() == null ? Optional.empty()
                    : Optional.of(new ForfaitApplique(ligne.forfaitImposeId(), ligne.libelleImpose(), ligne.prixHtImpose()));
            List<PriseEnChargeTarifee> prisesEnCharge = prisesEnChargeParPatient.computeIfAbsent(patient.id(),
                    p -> prisesEnCharge(centre, p));
            ForfaitApplique forfait = PolitiqueDeForfaitApplique.pour(ligne.date(), surcharge, prisesEnCharge);
            resultat.add(new SeanceFacturable(ligne.id(), patient, ligne.date(), new StatutDeSeance(ligne.statut()), forfait));
        }
        return resultat;
    }

    /** Les prises en charge tarifées du patient : langage publié de Patient et couverture (PC5), requête inchangée. */
    private List<PriseEnChargeTarifee> prisesEnCharge(CentreId centre, UUID patient) {
        return couverture.prisesEnChargeTarifees(centre.valeur(), patient).stream()
                .map(p -> new PriseEnChargeTarifee(new ForfaitApplique(p.forfaitId(), p.libelle(), p.prix()), p.forfaitEffectif(),
                        p.debutEffectif(), p.finEffectif(), p.debutDemande(), p.finDemande(), p.creeLe()))
                .toList();
    }
}
