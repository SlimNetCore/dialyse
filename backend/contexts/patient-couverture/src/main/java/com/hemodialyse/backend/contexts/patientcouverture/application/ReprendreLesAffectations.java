package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.PatientsDeLaReprise.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAffectations;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reprise de l'historique des affectations de l'outil de migration historique (ADR 0134, S6b-3) : mêmes tables
 * {@code assure_patient} et {@code patients}. Une affectation principale est l'unique principale du patient et l'assuré principal
 * de sa fiche ; la rejouer ne lui retire jamais ce caractère (KB-S6a-1). Les contrôles du fichier sont ceux de l'appelant.
 */
public class ReprendreLesAffectations implements RepriseDesAffectations {

    private final AssuresDuCentre assures;
    private final PatientsDeLaReprise patients;

    public ReprendreLesAffectations(AssuresDuCentre assures, PatientsDeLaReprise patients) {
        this.assures = assures;
        this.patients = patients;
    }

    @Override
    public List<AffectationConnue> historique(UUID centre, UUID patient) {
        return assures.historique(new CentreId(centre), new PatientId(patient)).stream()
                .map(a -> new AffectationConnue(a.id(), a.numeroAssurance(), a.dateDebut())).toList();
    }

    @Override
    public UUID reprendre(UUID centreDemande, UUID patient, UUID affectation, ReportDeLAffectation report) {
        CentreId centre = new CentreId(centreDemande);
        PatientId patientId = new PatientId(patient);
        Fiche fiche = report.principale() ? patients.trouver(centre, patient).orElseThrow(PatientIntrouvable::new) : null;

        // Le principal des autres lignes (et de celle-ci) est retiré AVANT l'écriture de la ligne, qui le rétablit explicitement.
        if (report.principale()) assures.retirerLePrincipal(centre, patientId);
        Optional<AssuresDuCentre.Affectation> existante = affectation == null ? Optional.empty()
                : assures.trouverAffectation(affectation).filter(a -> a.centre().equals(centreDemande));
        UUID id;
        if (existante.isPresent()) {
            id = existante.get().id();
            assures.remplacerUneAffectation(centre, id, report.numeroAssurance(), report.principale(), report.dateDebut(),
                    report.dateFin());
        } else {
            id = assures.ajouterUneAffectation(centre, patientId, report.numeroAssurance(), report.principale(), report.dateDebut(),
                    report.dateFin());
        }
        if (fiche != null) patients.mettreAJour(centre, fiche.avecLAssure(report.numeroAssurance(), fiche.assure()));
        return id;
    }
}
