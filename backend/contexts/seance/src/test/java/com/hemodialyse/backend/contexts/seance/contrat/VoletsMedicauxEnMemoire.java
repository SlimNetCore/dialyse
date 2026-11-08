package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletMedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Double en mémoire des volets médicaux : le même contrat que la table (H2, PostgreSQL 16) lui est appliqué. */
public class VoletsMedicauxEnMemoire implements VoletsMedicaux {

    private final List<VoletMedical> volets = new ArrayList<>();

    @Override
    public Optional<VoletMedical> deLaSeance(CentreId centre, SeanceId seance) {
        return volets.stream().filter(v -> v.centre().equals(centre) && v.seance().equals(seance)).findFirst().map(VoletsMedicauxEnMemoire::copie);
    }

    /** Comme une base : remplace les observations et la date de modification, jamais la date de création. */
    @Override
    public void enregistrer(VoletMedical volet) {
        for (int i = 0; i < volets.size(); i++) {
            VoletMedical existant = volets.get(i);
            if (existant.id().equals(volet.id()) && existant.centre().equals(volet.centre())) {
                volets.set(i, VoletMedical.restituer(existant.id(), existant.centre(), existant.seance(), volet.observations(),
                        existant.creeLe(), volet.modifieLe()));
                return;
            }
        }
        volets.add(copie(volet));
    }

    public int nombre() {
        return volets.size();
    }

    private static VoletMedical copie(VoletMedical v) {
        return VoletMedical.restituer(v.id(), v.centre(), v.seance(), v.observations(), v.creeLe(), v.modifieLe());
    }
}
