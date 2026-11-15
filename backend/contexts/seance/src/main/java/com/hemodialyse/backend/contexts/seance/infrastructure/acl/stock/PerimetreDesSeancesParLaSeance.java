package com.hemodialyse.backend.contexts.seance.infrastructure.acl.stock;

import com.hemodialyse.backend.contexts.seance.publishedlanguage.PlacesDesSeancesPubliees;
import com.hemodialyse.backend.contexts.stock.publishedlanguage.PerimetreDesSeancesFourni;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/**
 * ACL du Stock vers la Séance (ADR 0096, SE7c1) : la salle d'une séance est le code de sa salle, sa demi-journée se lit dans le code ou le
 * libellé de son créneau, tels que la Séance les publie ({@link PlacesDesSeancesPubliees}) : « matin » donne le matin, « après-midi »,
 * « midi » ou « soir » donne l'après-midi ; sinon aucune (KB-SEA-68, reproduit). Séance inconnue ou d'un autre centre : la sortie de la
 * journée (KB-SEA-69, reproduit). Remplace {@code PerimetreDesSeancesLegacy} (SQL direct sur {@code seances}). Hébergé par la Séance, qui
 * dépend du Stock : elle fournit au Stock son port {@link PerimetreDesSeancesFourni} (ADR 0191).
 */
public class PerimetreDesSeancesParLaSeance implements PerimetreDesSeancesFourni {

    private final PlacesDesSeancesPubliees places;

    public PerimetreDesSeancesParLaSeance(PlacesDesSeancesPubliees places) {
        this.places = places;
    }

    static String simplifier(String texte) {
        return Normalizer.normalize(texte == null ? "" : texte, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    static DemiJourneeFournie demiJournee(String code, String libelle) {
        String texte = simplifier(code) + " " + simplifier(libelle);
        if (texte.contains("APRES") || texte.contains("MIDI") || texte.contains("SOIR")) {
            return DemiJourneeFournie.APRES_MIDI;
        }
        return texte.contains("MATIN") ? DemiJourneeFournie.MATIN : null;
    }

    @Override
    public PerimetreFourni perimetreDe(UUID centre, UUID seance) {
        return places.de(centre, seance)
                .map(place -> new PerimetreFourni(place.codeSalle(), demiJournee(place.codeCreneau(), place.libelleCreneau())))
                .orElseGet(PerimetreFourni::journee);
    }
}
