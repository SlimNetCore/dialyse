package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http.dto;

import java.util.UUID;

/**
 * Corps d'une transition d'une demande d'examen : le centre (facultatif, par défaut celui de la session) et la conclusion, posée par la
 * seule validation et ignorée par les autres actions (reproduit du legacy).
 */
public record TransitionDeDemandeDExamen(UUID centerId, String conclusion) {
}
