package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.GenerateurIdentifiants;

import java.util.UUID;

public class IdentifiantsSequentiels implements GenerateurIdentifiants {
    private int compteur = 0;

    @Override
    public UUID nouvelIdentifiant() {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", ++compteur));
    }
}
