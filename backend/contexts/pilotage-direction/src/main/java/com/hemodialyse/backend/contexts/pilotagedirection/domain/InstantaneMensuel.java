package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Un instantané mensuel de la société (agrégat à une seule entité, PD2, ADR 0103) : les chiffres de la société sur un mois écoulé, figés
 * à la première capture. IMMUABLE : aucune méthode ne le modifie ; il n'existe qu'un instantané par société et par mois (unicité portée aussi
 * par la base). L'horodatage de capture est posé par la base à l'enregistrement (KB-DIR-06 c).
 */
public final class InstantaneMensuel {

    private final UUID societeId;
    private final MoisDInstantane mois;
    private final ContenuFige contenu;

    private InstantaneMensuel(UUID societeId, MoisDInstantane mois, ContenuFige contenu) {
        this.societeId = Objects.requireNonNull(societeId, "societeId");
        this.mois = Objects.requireNonNull(mois, "mois");
        this.contenu = Objects.requireNonNull(contenu, "contenu");
    }

    /**
     * Fige le mois de la société.
     *
     * @throws RegleDuPilotageViolee si le mois n'est pas capturable à la date courante (écoulé et de moins de cinq ans)
     */
    public static InstantaneMensuel figer(UUID societeId, MoisDInstantane mois, YearMonth courant, ContenuFige contenu) {
        mois.exigerCapturable(courant);
        return new InstantaneMensuel(societeId, mois, contenu);
    }

    /** Le mois précédent le mois courant, tel que la tâche du 1er du mois le fige (écoulé par construction). */
    public static InstantaneMensuel figerLeMoisEcoule(UUID societeId, YearMonth courant, ContenuFige contenu) {
        return figer(societeId, MoisDInstantane.precedent(courant), courant, contenu);
    }

    public UUID societeId() {
        return societeId;
    }

    public MoisDInstantane mois() {
        return mois;
    }

    public ContenuFige contenu() {
        return contenu;
    }
}
