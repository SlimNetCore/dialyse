package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.absencespatients.application.NomsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.NomDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.FichesDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ACL vers le langage publié de Patient et couverture ({@code patientcouverture.v1}, PC5) : le nom affiché des patients du centre
 * (celui d'Absences compose « prénom nom » et ignore un patient sans nom). Un patient d'un autre centre est absent du résultat
 * (borne du port). Remplace la lecture SQL directe de {@code patients} (comportement inchangé).
 */
public class NomsDesPatientsDeLaFiche implements NomsDesPatients {

    private final FichesDesPatients fiches;

    public NomsDesPatientsDeLaFiche(FichesDesPatients fiches) {
        this.fiches = fiches;
    }

    @Override
    public Map<PatientId, String> parPatient(CentreId centre, Collection<PatientId> patients) {
        Map<PatientId, String> noms = new HashMap<>();
        if (patients.isEmpty()) return noms;
        Collection<UUID> ids = patients.stream().map(PatientId::valeur).distinct().toList();
        fiches.noms(centre.valeur(), ids).forEach((id, lu) -> {
            String nom = NomDuPatient.complet(lu.prenom(), lu.nom());
            if (nom != null) noms.put(new PatientId(id), nom);
        });
        return noms;
    }
}
