package com.hemodialyse.backend.contexts.seance.contrat;

import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.VoletParamedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsParamedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Double en mémoire des volets paramédicaux : le même contrat que la table (H2, PostgreSQL 16) lui est appliqué, y compris l'unicité par
 * séance tous centres confondus (KB-SEA-94 : {@link UniciteParSeanceViolee}, comme la contrainte {@code UNIQUE (seance_id)}).
 */
public class VoletsParamedicauxEnMemoire implements VoletsParamedicaux {

    /** Tient lieu de l'erreur d'accès aux données levée par la base sur l'unicité par séance. */
    public static class UniciteParSeanceViolee extends RuntimeException {
        UniciteParSeanceViolee() {
            super("Unique index or primary key violation: volet_paramedical(seance_id)");
        }
    }

    private final List<VoletParamedical> volets = new ArrayList<>();

    @Override
    public Optional<VoletParamedical> deLaSeance(CentreId centre, SeanceId seance) {
        // KB-SEA-94 (corrigé, L4) : par la séance seule, comme la table ; le volet garde son centre
        return volets.stream().filter(v -> v.seance().equals(seance)).findFirst().map(VoletsParamedicauxEnMemoire::copie);
    }

    @Override
    public void enregistrer(VoletParamedical volet) {
        for (int i = 0; i < volets.size(); i++) {
            VoletParamedical existant = volets.get(i);
            if (existant.id().equals(volet.id()) && existant.centre().equals(volet.centre())) {
                volets.set(i, VoletParamedical.restituer(existant.id(), existant.centre(), existant.seance(), volet.mesures(),
                        existant.creeLe(), volet.modifieLe()));
                return;
            }
        }
        if (volets.stream().anyMatch(v -> v.seance().equals(volet.seance()))) throw new UniciteParSeanceViolee();
        volets.add(copie(volet));
    }

    @Override
    public void effacer(CentreId centre, SeanceId seance) {
        volets.removeIf(v -> v.centre().equals(centre) && v.seance().equals(seance));
    }

    public int nombre() {
        return volets.size();
    }

    private static VoletParamedical copie(VoletParamedical v) {
        return VoletParamedical.restituer(v.id(), v.centre(), v.seance(), v.mesures(), v.creeLe(), v.modifieLe());
    }
}
