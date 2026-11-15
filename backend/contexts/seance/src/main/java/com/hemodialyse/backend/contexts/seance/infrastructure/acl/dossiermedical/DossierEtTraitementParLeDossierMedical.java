package com.hemodialyse.backend.contexts.seance.infrastructure.acl.dossiermedical;

import com.hemodialyse.backend.contexts.seance.application.DossierEtTraitementDuPatient;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.DossierEtTraitementDuCahier;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ACL vers le Dossier médical (DOC5a, ADR 0193) : traduit le langage {@link DossierEtTraitementDuCahier} que le Dossier médical FOURNIT à la
 * Séance (il dépend d'elle, pas l'inverse) en port de la Séance. Traduction terme à terme, valeurs brutes.
 */
public class DossierEtTraitementParLeDossierMedical implements DossierEtTraitementDuPatient {

    private final DossierEtTraitementDuCahier fourni;

    public DossierEtTraitementParLeDossierMedical(DossierEtTraitementDuCahier fourni) {
        this.fourni = fourni;
    }

    @Override
    public Optional<Dossier> dossier(CentreId centre, PatientId patient) {
        return fourni.dossier(centre.valeur(), patient.valeur()).map(d -> new Dossier(d.nephropathieInitiale(), d.dateMiseEnDialyse(),
                d.statutHepatiteB(), d.statutHepatiteC(), d.observationGlobale(), d.conclusionMedicale()));
    }

    @Override
    public Optional<Traitement> traitementEnVigueur(CentreId centre, PatientId patient, LocalDate jour) {
        return fourni.traitementEnVigueur(centre.valeur(), patient.valeur(), jour)
                .map(t -> new Traitement(t.poidsSecCibleKg(), medicament(t.epo()), medicament(t.fer())));
    }

    static Medicament medicament(DossierEtTraitementDuCahier.Traitement t) {
        return new Medicament(t.article(), t.dose(), t.voie(), t.frequenceValeur(), t.frequenceUnite());
    }
}
