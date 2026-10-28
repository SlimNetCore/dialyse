package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Facture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Factures;
import com.hemodialyse.backend.contexts.facturation.domain.facture.NumeroDeFactureDejaPris;

import java.util.ArrayList;
import java.util.List;

/**
 * Double en mémoire des factures émises : même contrat que l'adaptateur JDBC (voir {@code FacturesContrat}).
 */
public class FacturesEnMemoire implements Factures {

    private final List<Facture> factures = new ArrayList<>();

    @Override
    public void enregistrer(Facture facture) {
        if (numeroDejaPris(facture.centre(), facture.numero())) {
            throw new NumeroDeFactureDejaPris(facture.numero());
        }
        factures.add(facture);
    }

    @Override
    public boolean numeroDejaPris(CentreId centre, String numero) {
        return factures.stream().anyMatch(f -> f.centre().equals(centre) && f.numero().equals(numero));
    }

    public List<Facture> emises() {
        return List.copyOf(factures);
    }

    /**
     * Une facture déjà émise avant la validation (fixture), pour provoquer un doublon de numéro.
     */
    public void dejaEmise(Facture facture) {
        factures.add(facture);
    }

    public void annulerTout() {
        factures.clear();
    }
}
