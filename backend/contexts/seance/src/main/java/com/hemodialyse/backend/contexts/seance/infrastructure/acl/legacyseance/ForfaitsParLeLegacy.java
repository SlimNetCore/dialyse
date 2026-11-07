package com.hemodialyse.backend.contexts.seance.infrastructure.acl.legacyseance;

import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.ForfaitsFournis;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/** ACL des forfaits : traduit le langage publié {@link ForfaitsFournis}. */
public class ForfaitsParLeLegacy implements ForfaitsDeSeance {

    private final ForfaitsFournis forfaits;

    public ForfaitsParLeLegacy(ForfaitsFournis forfaits) {
        this.forfaits = forfaits;
    }

    @Override
    public Optional<ForfaitCourant> courant(CentreId centre, SeanceId seance, PatientId patient, LocalDate jour) {
        return forfaits.courant(centre.valeur(), seance.valeur(), patient.valeur(), jour)
                .map(f -> new ForfaitCourant(f.id(), f.code(), f.nom(), f.prix(), f.nombreDeSeances(), f.misAJourLe(), f.misAJourPar(),
                        f.surcharge()));
    }
}
