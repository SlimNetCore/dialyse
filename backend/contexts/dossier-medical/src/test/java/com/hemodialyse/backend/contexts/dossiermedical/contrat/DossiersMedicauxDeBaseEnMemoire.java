package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DossiersMedicauxDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Double en mémoire du port {@link DossiersMedicauxDeBase} (unicité globale du patient, comme la table réelle). */
public class DossiersMedicauxDeBaseEnMemoire implements DossiersMedicauxDeBase {
    private final List<DossierMedicalDeBase> lignes = new ArrayList<>();

    private static DossierMedicalDeBase copie(DossierMedicalDeBase d) {
        return DossierMedicalDeBase.reconstituer(d.id(), d.patient(), d.centre(), d.nephropathieInitiale(), d.dateMiseEnDialyse(), d.hepatiteBStatut(),
                d.hepatiteCStatut(), d.observationGlobale(), d.conclusionMedicale(), d.creeLe(), d.modifieLe());
    }

    @Override
    public Optional<DossierMedicalDeBase> duPatient(CentreId centre, PatientId patient) {
        return lignes.stream().filter(d -> d.centre().equals(centre) && d.patient().equals(patient))
                .min(Comparator.comparing(DossierMedicalDeBase::creeLe, Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(DossierMedicalDeBase::id))
                .map(DossiersMedicauxDeBaseEnMemoire::copie);
    }

    @Override
    public void verrouillerLEnregistrement(PatientId patient) {
    }

    @Override
    public Optional<DossierMedicalDeBase> duPatientEnVerrouillant(CentreId centre, PatientId patient) {
        return duPatient(centre, patient);
    }

    @Override
    public void ajouter(DossierMedicalDeBase dossier) {
        if (lignes.stream().anyMatch(d -> d.patient().equals(dossier.patient()))) throw new EcritureConcurrenteSurUnDossierMedical();
        lignes.add(copie(dossier));
    }

    @Override
    public void enregistrer(DossierMedicalDeBase dossier) {
        lignes.removeIf(d -> d.id().equals(dossier.id()));
        lignes.add(copie(dossier));
    }
}
