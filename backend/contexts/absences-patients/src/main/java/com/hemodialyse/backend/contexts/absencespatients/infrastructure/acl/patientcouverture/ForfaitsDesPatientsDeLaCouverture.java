package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.absencespatients.application.ForfaitsDesPatients;
import com.hemodialyse.backend.contexts.absencespatients.domain.ForfaitApplicable;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.ForfaitId;
import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CouvertureDesPatients;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * ACL vers le langage publié de Patient et couverture ({@code patientcouverture.v1}, PC5) : le forfait applicable à une date, règle
 * du legacy reproduite à l'identique par le port (KB-ABS-16 : effectif d'abord, sinon demandé, PEC couvrante puis la plus récente ;
 * le statut de la PEC n'est jamais regardé). Remplace la lecture SQL directe de {@code prise_en_charge} (comportement inchangé).
 */
public class ForfaitsDesPatientsDeLaCouverture implements ForfaitsDesPatients {

    private final CouvertureDesPatients couverture;

    public ForfaitsDesPatientsDeLaCouverture(CouvertureDesPatients couverture) {
        this.couverture = couverture;
    }

    @Override
    public Optional<ForfaitApplicable> applicable(CentreId centre, PatientId patient, LocalDate date) {
        return couverture.forfaitApplicable(centre.valeur(), patient.valeur(), date)
                .map(f -> new ForfaitApplicable(new ForfaitId(f.id()), f.libelle(), f.prix()));
    }
}
