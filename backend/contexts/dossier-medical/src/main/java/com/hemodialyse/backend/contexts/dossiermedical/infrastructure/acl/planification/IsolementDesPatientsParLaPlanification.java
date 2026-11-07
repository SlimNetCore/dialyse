package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.acl.planification;

import com.hemodialyse.backend.contexts.dossiermedical.application.IsolementDesPatients;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.ReaffectationEnIsolement;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.VerrouDesPlacesDuCentre;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

/**
 * ACL du port {@link IsolementDesPatients} vers le langage publié de la Planification ({@code ReaffectationEnIsolement}, P5, et
 * {@code VerrouDesPlacesDuCentre}) : la sérologie ne parle que de ses termes, la Planification réévalue la place dans la transaction de
 * l'appelant, sous le verrou du centre. Les refus de la Planification ({@code PlacementDesPatients.Refus} : 422
 * {@code PLACEMENT_PATIENT_INTROUVABLE}, {@code EcritureConcurrente} : 409) traversent tels quels : leur format est celui de
 * l'advice de la Planification, comportement du legacy inchangé. Le verrou n'est pris que pour un patient connu du centre : la ligne de
 * paramètres du centre est créée si elle manque, ce qu'un centre demandé inexistant ne doit pas provoquer.
 */
public class IsolementDesPatientsParLaPlanification implements IsolementDesPatients {

    private final FichesDesPatients fiches;
    private final VerrouDesPlacesDuCentre verrou;
    private final ReaffectationEnIsolement reaffectation;

    public IsolementDesPatientsParLaPlanification(FichesDesPatients fiches, VerrouDesPlacesDuCentre verrou, ReaffectationEnIsolement reaffectation) {
        this.fiches = fiches;
        this.verrou = verrou;
        this.reaffectation = reaffectation;
    }

    @Override
    public void verrouillerLesPlaces(CentreId centre, PatientId patient) {
        if (fiches.existe(centre.valeur(), patient.valeur())) verrou.verrouiller(centre.valeur());
    }

    @Override
    public void reevaluer(CentreId centre, PatientId patient) {
        reaffectation.siRisque(centre.valeur(), patient.valeur());
    }
}
