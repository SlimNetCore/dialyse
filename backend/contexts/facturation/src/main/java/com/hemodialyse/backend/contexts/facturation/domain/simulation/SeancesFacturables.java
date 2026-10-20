package com.hemodialyse.backend.contexts.facturation.domain.simulation;

import com.hemodialyse.backend.contexts.facturation.domain.CentreId;

import java.util.List;

/**
 * Port sortant (lecture) : les séances facturables du centre dans la période (RG-FAC-020), avec le forfait appliqué à chacune
 * (RG-FAC-021). Triées par nom puis prénom du patient, date de séance, identifiant. L'adapter est une ACL sur les séances, les
 * patients et les prises en charge.
 */
public interface SeancesFacturables {

    List<SeanceFacturable> dansLaPeriode(CentreId centre, PeriodeDeFacturation periode);
}
