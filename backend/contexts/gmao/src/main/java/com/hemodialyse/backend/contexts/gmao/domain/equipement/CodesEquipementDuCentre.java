package com.hemodialyse.backend.contexts.gmao.domain.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

/**
 * Port sortant du domaine : les codes déjà portés par les équipements d'un centre. Les équipements supprimés logiquement
 * n'y comptent pas (KB-GMAO-34) ; la base reste le dernier filet (KB-GMAO-33).
 */
public interface CodesEquipementDuCentre {

    boolean estUtilise(CentreId centre, CodeEquipement code);
}
