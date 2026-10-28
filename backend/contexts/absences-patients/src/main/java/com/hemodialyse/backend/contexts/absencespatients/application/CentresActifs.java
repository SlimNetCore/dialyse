package com.hemodialyse.backend.contexts.absencespatients.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;


import java.util.List;

/**
 * Port sortant : les centres que le contrôle de nuit parcourt. Un centre inactif n'est pas contrôlé (KB-ABS-26 corrigé).
 */
public interface CentresActifs {

    List<CentreId> liste();
}
