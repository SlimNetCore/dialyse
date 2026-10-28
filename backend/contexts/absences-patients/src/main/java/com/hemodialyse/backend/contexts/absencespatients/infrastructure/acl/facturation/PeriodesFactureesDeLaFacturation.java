package com.hemodialyse.backend.contexts.absencespatients.infrastructure.acl.facturation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.absencespatients.domain.Identifiants.PatientId;
import com.hemodialyse.backend.contexts.absencespatients.domain.PeriodesFacturees;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;

import java.time.LocalDate;

/**
 * Période facturée lue par le langage publié de la Facturation ({@code FacturationDuPatient}) : relue à la demande, à chaque
 * écriture.
 */
public class PeriodesFactureesDeLaFacturation implements PeriodesFacturees {

    private final FacturationDuPatient facturation;

    public PeriodesFactureesDeLaFacturation(FacturationDuPatient facturation) {
        this.facturation = facturation;
    }

    @Override
    public boolean estFacturee(CentreId centre, PatientId patient, LocalDate jour) {
        return facturation.periodeFacturee(centre.valeur(), patient.valeur(), jour);
    }
}
