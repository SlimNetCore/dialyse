package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.seance.domain.PatientsDeLHistorique;

import java.util.List;
import java.util.UUID;

/**
 * ACL vers Patient et couverture (LEC-PAT sous-slice iii, ADR 0178) : les patients du centre de l'historique des séances, par le langage
 * publié {@link LotsDePatients#rechercherDuCentre}. Traduction pure, sans règle : remplace la jointure {@code seances x patients} et le
 * filtre de texte en SQL de {@code HistoriqueDesSeancesJdbc}.
 */
public class PatientsDeLHistoriqueParLaFiche implements PatientsDeLHistorique {

    private final LotsDePatients lots;

    public PatientsDeLHistoriqueParLaFiche(LotsDePatients lots) {
        this.lots = lots;
    }

    @Override
    public List<PatientDeLHistorique> duCentre(UUID centre, String texte, Ordre ordre, boolean decroissant) {
        LotsDePatients.OrdreDeRecherche demande = switch (ordre) {
            case NOM_PUIS_PRENOM -> LotsDePatients.OrdreDeRecherche.NOM_PUIS_PRENOM;
            case CODE -> LotsDePatients.OrdreDeRecherche.CODE;
            case AUCUN -> LotsDePatients.OrdreDeRecherche.AUCUN;
        };
        return lots.rechercherDuCentre(centre, texte, demande, decroissant).stream()
                .map(p -> new PatientDeLHistorique(p.patient(), p.code(), p.nom(), p.prenom(), p.rang())).toList();
    }
}
