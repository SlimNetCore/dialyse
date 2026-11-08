package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;
import com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance.RappelsParLeLegacy;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.RappelsFournis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Contrat des rappels sur l'ACL du langage publié, au-dessus d'un fournisseur qui dédoublonne comme le journal de la plateforme. */
class RappelsParLeLegacyContratTest extends RappelsContrat {

    private final List<String> emissions = new ArrayList<>();
    private final Set<String> cles = new HashSet<>();
    private final RappelsFournis fournisseur = new RappelsFournis() {
        @Override
        public boolean rappeler(UUID centre, LocalDate jour, long nbSeances, LocalDate plusAncienne) {
            if (!cles.add(centre + "|" + jour)) return false;
            emissions.add(centre + "|" + jour + "|" + nbSeances + "|" + plusAncienne);
            return true;
        }
    };

    @Override
    protected RappelsDeRegularisation rappels() {
        return new RappelsParLeLegacy(fournisseur);
    }

    @Override
    protected List<String> emissions() {
        return emissions;
    }
}
