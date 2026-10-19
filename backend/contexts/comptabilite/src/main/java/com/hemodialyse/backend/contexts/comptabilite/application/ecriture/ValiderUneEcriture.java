package com.hemodialyse.backend.contexts.comptabilite.application.ecriture;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureIntrouvable;

import java.util.UUID;

/**
 * Transition BROUILLON → VALIDEE, idempotente pour une écriture déjà validée.
 */
public final class ValiderUneEcriture {
    private final EcritureComptableRepository ecritures;
    private final UniteDeTravail uniteDeTravail;

    public ValiderUneEcriture(EcritureComptableRepository ecritures, UniteDeTravail uniteDeTravail) {
        this.ecritures = ecritures;
        this.uniteDeTravail = uniteDeTravail;
    }

    public EcritureVue executer(CentreId centre, UUID id) {
        return uniteDeTravail.executer(() -> {
            EcritureComptable ecriture = ecritures.parId(centre, id).orElseThrow(EcritureIntrouvable::new);
            ecriture.valider();
            ecritures.enregistrer(ecriture);
            return EcritureVue.depuis(ecriture);
        });
    }
}


