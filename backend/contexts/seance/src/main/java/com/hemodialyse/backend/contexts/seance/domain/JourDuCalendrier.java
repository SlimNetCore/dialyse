package com.hemodialyse.backend.contexts.seance.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Un jour du calendrier du centre où il ne dialyse pas : jour férié ou fermeture exceptionnelle, avec son libellé (facultatif). Le
 * centre, le jour et la nature sont requis ; le libellé tient dans la colonne (255 caractères, KB-SEA-58 corrigé : le legacy répondait 500
 * au-delà). Aucune règle de cohérence avec les séances déjà posées ce jour-là, ni de borne de calendrier (an 1900 accepté, reproduit).
 * Un seul jour du même genre par centre et par date ({@link JourDejaAuCalendrier}).
 * <p>
 * Le calendrier reste dans la Séance pour cette slice (ADR 0094, décision à confirmer) ; le transfert à l'Organisation est un résidu daté.
 */
public final class JourDuCalendrier {

    /** Longueur de la colonne {@code label} / {@code reason}. */
    public static final int LONGUEUR_MAXIMALE_DU_LIBELLE = 255;

    private final UUID id;
    private final CentreId centre;
    private final LocalDate jour;
    private final NatureDeJour nature;
    private final String libelle;

    public JourDuCalendrier(UUID id, CentreId centre, LocalDate jour, NatureDeJour nature, String libelle) {
        if (id == null) throw new SaisieInvalide("Identifiant du jour requis");
        if (centre == null) throw new SaisieInvalide("Centre requis");
        if (jour == null) throw new SaisieInvalide("Jour requis");
        if (nature == null) throw new SaisieInvalide("Nature du jour requise");
        if (libelle != null && libelle.length() > LONGUEUR_MAXIMALE_DU_LIBELLE) {
            throw new SaisieInvalide("Libellé trop long : " + LONGUEUR_MAXIMALE_DU_LIBELLE + " caractères au plus");
        }
        this.id = id;
        this.centre = centre;
        this.jour = jour;
        this.nature = nature;
        this.libelle = libelle;
    }

    public UUID id() {
        return id;
    }

    public CentreId centre() {
        return centre;
    }

    public LocalDate jour() {
        return jour;
    }

    public NatureDeJour nature() {
        return nature;
    }

    /** Libellé tel que saisi (nul s'il n'y en a pas). */
    public String libelle() {
        return libelle;
    }
}
