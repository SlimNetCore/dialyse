package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.intervention.NomsDesUtilisateurs;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Double in-memory du port {@link NomsDesUtilisateurs} : les noms sont posés par le test ; un utilisateur inconnu n'a pas de nom. */
public class NomsEnMemoire implements NomsDesUtilisateurs {

    private final Map<UtilisateurId, String> noms = new HashMap<>();

    public NomsEnMemoire avec(UtilisateurId utilisateur, String nom) {
        noms.put(utilisateur, nom);
        return this;
    }

    @Override
    public Optional<String> de(UtilisateurId utilisateur) {
        return Optional.ofNullable(noms.get(utilisateur));
    }
}
