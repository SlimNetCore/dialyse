package com.hemodialyse.backend.contexts.stock.application.referentiel;

import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Emplacement;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Fournisseur;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Emplacements;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ReferentielSimple.Fournisseurs;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage des fournisseurs et des emplacements (RG-STK-003, 004) : créer, lister les actifs, chercher un fournisseur.
 */
public final class ReferentielDesFournisseursEtEmplacements {

    private final Fournisseurs fournisseurs;
    private final Emplacements emplacements;
    private final Supplier<UUID> identifiants;

    public ReferentielDesFournisseursEtEmplacements(Fournisseurs fournisseurs, Emplacements emplacements,
                                                    Supplier<UUID> identifiants) {
        this.fournisseurs = fournisseurs;
        this.emplacements = emplacements;
        this.identifiants = identifiants;
    }

    public Fournisseur creerUnFournisseur(CentreId centre, String code, String raisonSociale, String contact, String telephone,
                                          String email) {
        Fournisseur fournisseur = Fournisseur.referencer(identifiants.get(), centre, code, raisonSociale, contact, telephone, email);
        fournisseurs.enregistrer(fournisseur);
        return fournisseur;
    }

    /**
     * Les fournisseurs actifs ; avec un texte non blanc, ceux dont la raison sociale le contient (texte nettoyé).
     */
    public List<Fournisseur> chercherDesFournisseurs(CentreId centre, String texte) {
        if (texte == null || texte.isBlank()) {
            return fournisseurs.actifs(centre);
        }
        return fournisseurs.chercher(centre, texte.trim());
    }

    public Emplacement creerUnEmplacement(CentreId centre, String code, String libelle) {
        Emplacement emplacement = Emplacement.creer(identifiants.get(), centre, code, libelle);
        emplacements.enregistrer(emplacement);
        return emplacement;
    }

    public List<Emplacement> listerLesEmplacements(CentreId centre) {
        return emplacements.actifs(centre);
    }
}

