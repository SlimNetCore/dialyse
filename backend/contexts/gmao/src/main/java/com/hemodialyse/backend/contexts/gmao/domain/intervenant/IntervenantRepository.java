package com.hemodialyse.backend.contexts.gmao.domain.intervenant;

import java.util.Optional;

/** Port sortant : persistance de l'agrégat {@link Intervenant} (un repository par racine). Jamais de suppression (R24). */
public interface IntervenantRepository {

    void enregistrer(Intervenant intervenant);

    Optional<Intervenant> trouverParId(IntervenantId id);
}
