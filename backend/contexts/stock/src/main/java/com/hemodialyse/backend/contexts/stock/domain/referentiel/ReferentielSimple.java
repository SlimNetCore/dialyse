package com.hemodialyse.backend.contexts.stock.domain.referentiel;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Ports sortants des fournisseurs et des emplacements d'un centre (tables {@code fournisseurs}, {@code emplacements}).
 */
public final class ReferentielSimple {

    private ReferentielSimple() {
    }

    public interface Fournisseurs {
        void enregistrer(Fournisseur fournisseur);

        /**
         * Les fournisseurs actifs du centre, triés par raison sociale.
         */
        List<Fournisseur> actifs(CentreId centre);

        /**
         * Les fournisseurs actifs dont la raison sociale contient le texte (sans casse), triés par raison sociale.
         */
        List<Fournisseur> chercher(CentreId centre, String texte);

        /**
         * Vrai si le fournisseur, actif ou non, appartient au centre (bons de commande, RG-STK-010).
         */
        boolean existe(CentreId centre, java.util.UUID id);
    }

    public interface Emplacements {
        void enregistrer(Emplacement emplacement);

        /**
         * Les emplacements actifs du centre, triés par libellé.
         */
        List<Emplacement> actifs(CentreId centre);
    }
}


