package com.hemodialyse.backend.contexts.stock.application.peremptions;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;

/**
 * Port sortant : les centres en activité (contexte Identité et accès, encore legacy), dans un ordre stable.
 */
public interface CentresActifs {

    List<CentreId> liste();
}
