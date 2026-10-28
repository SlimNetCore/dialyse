package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Port sortant : taux de TVA (en pourcentage) de l'hémodialyse à une date, lu chez la Comptabilité (langage publié
 * {@code ParametrageFiscal}). Zéro quand aucune règle ne s'applique ou que la règle est exonérée (KB-CPT-19, reproduit).
 */
public interface TauxDeTva {

    BigDecimal pour(CentreId centre, LocalDate date);
}
