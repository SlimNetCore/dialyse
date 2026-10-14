package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.PublicateurDEvenements;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Double en mémoire du port {@link PublicateurDEvenements} : conserve les événements publiés. */
public class PublicateurEnMemoire implements PublicateurDEvenements {

    public final List<Object> publies = new ArrayList<>();

    @Override
    public void publier(Collection<?> evenements) {
        publies.addAll(evenements);
    }
}
