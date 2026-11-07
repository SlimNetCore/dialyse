package com.hemodialyse.backend.contexts.dossiermedical.contrat;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;

/** Le double en mémoire passe le contrat. */
class EtapesDeBilanEnMemoireContratTest extends EtapesDeBilanContrat {
    private final EtapesDeBilan etapes = new EtapesDeBilanEnMemoire();

    @Override
    protected EtapesDeBilan etapes() {
        return etapes;
    }
}
