package com.hemodialyse.backend.contexts.seance.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.seance.application.ForfaitDeLaPriseEnCharge;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance.ForfaitCourant;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ACL vers Patient et couverture (LEC-PAT sous-slice ii, ARGENT, ADR 0177) : le forfait de la prise en charge, par
 * {@link CouvertureDesPatients#forfaitApplicable}. Remplace la lecture SQL de {@code prise_en_charge} et {@code forfait} de
 * {@code ForfaitsDeSeanceJdbc} (RF2-R2). Traduction pure : le prix est copié à l'identique, {@code nombreDeSeances} vaut zéro (valeur
 * historique du legacy), ni auteur ni date de mise à jour, ce n'est pas une surcharge.
 */
public class ForfaitDeLaPriseEnChargeParLaCouverture implements ForfaitDeLaPriseEnCharge {

    private final CouvertureDesPatients couverture;

    public ForfaitDeLaPriseEnChargeParLaCouverture(CouvertureDesPatients couverture) {
        this.couverture = couverture;
    }

    @Override
    public Optional<ForfaitCourant> duJour(CentreId centre, PatientId patient, LocalDate jour) {
        return couverture.forfaitApplicable(centre.valeur(), patient.valeur(), jour)
                .map(f -> new ForfaitCourant(f.id(), f.code(), f.libelle(), f.prix(), 0, null, null, false));
    }
}
