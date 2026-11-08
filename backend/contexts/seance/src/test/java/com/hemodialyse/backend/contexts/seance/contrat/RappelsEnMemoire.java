package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.application.RappelsDeRegularisation;
import com.hemodialyse.backend.contexts.seance.domain.RappelDeRegularisation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Double en mémoire du port des rappels : une seule émission par centre et par jour du centre. */
public class RappelsEnMemoire implements RappelsDeRegularisation {

    private final Set<String> cles = new HashSet<>();
    private final List<String> emissions = new ArrayList<>();

    @Override
    public boolean rappeler(RappelDeRegularisation rappel) {
        if (!cles.add(rappel.centre().valeur() + "|" + rappel.jour())) return false;
        emissions.add(rappel.centre().valeur() + "|" + rappel.jour() + "|" + rappel.seances().total() + "|" + rappel.seances().plusAncienne());
        return true;
    }

    public List<String> emissions() {
        return emissions;
    }
}
