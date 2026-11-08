package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.domain.ForfaitDuCentre;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitId;
import com.hemodialyse.backend.contexts.seance.domain.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ReferentielDeForfaitsFourni;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Optional;

/** ACL du référentiel des forfaits : traduit le langage publié {@link ReferentielDeForfaitsFourni} ({@code libelle} devient le nom). */
public class ForfaitsDuCentreParLeLegacy implements ForfaitsDuCentre {

    private final ReferentielDeForfaitsFourni referentiel;

    public ForfaitsDuCentreParLeLegacy(ReferentielDeForfaitsFourni referentiel) {
        this.referentiel = referentiel;
    }

    @Override
    public Optional<ForfaitDuCentre> trouver(CentreId centre, ForfaitId forfait) {
        return referentiel.forfait(centre.valeur(), forfait.valeur())
                .map(f -> new ForfaitDuCentre(new ForfaitId(f.id()), f.code(), f.libelle(), f.prix()));
    }
}
