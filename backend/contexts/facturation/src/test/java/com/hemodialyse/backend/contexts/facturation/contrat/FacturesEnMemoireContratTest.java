package com.hemodialyse.backend.contexts.facturation.contrat;

import com.hemodialyse.backend.contexts.facturation.doubles.FacturesEnMemoire;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Facture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Factures;

import java.util.UUID;

/**
 * Le double en mémoire passe le contrat partagé des factures.
 */
class FacturesEnMemoireContratTest extends FacturesContrat {

    private final FacturesEnMemoire memoire = new FacturesEnMemoire();

    @Override
    protected Factures factures() {
        return memoire;
    }

    @Override
    protected Enregistree relire(UUID id) {
        Facture f = memoire.emises().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow();
        return new Enregistree(f.numero(), f.patient().id(), f.patient().codeAffiche(), f.patient().nomComplet(),
                f.patient().etatAffiche(), f.patient().numeroDImmatriculation(), f.patient().centrePayeur(), f.patient().agence(),
                f.periode().debut(), f.periode().fin(), f.dateDeFacturation(), f.taux().pourcentage(), f.totalHt(), f.totalTva(),
                f.totalTtc(), f.emisePar(), f.lignes().stream()
                .map(l -> new LigneEnregistree(l.forfait(), l.libelle(), l.prixUnitaireHt(), l.nombreDeSeances(), l.montantHt()))
                .toList());
    }
}
