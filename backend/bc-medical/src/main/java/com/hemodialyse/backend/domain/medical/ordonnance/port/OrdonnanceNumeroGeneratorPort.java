package com.hemodialyse.backend.domain.medical.ordonnance.port;

import com.hemodialyse.backend.domain.shared.vo.CenterId;

/**
 * Génère le numéro définitif attribué à une ordonnance au moment de sa signature — séquentiel et
 * unique par centre (multi-tenant, AGENTS.md §2).
 */
public interface OrdonnanceNumeroGeneratorPort {

    String genererNumero(CenterId centerId);
}
