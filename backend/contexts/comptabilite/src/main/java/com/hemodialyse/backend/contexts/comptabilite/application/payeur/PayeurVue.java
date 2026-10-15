package com.hemodialyse.backend.contexts.comptabilite.application.payeur;

import java.util.UUID;

/**
 * Un payeur du centre et son compte client propre, tel que l'écran le lit.
 *
 * @param compte compte client propre au payeur, ou {@code null} : le compte client par défaut du centre s'applique
 */
public record PayeurVue(UUID payeurId, String code, String nom, String compte) {
}
