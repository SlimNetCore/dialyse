package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Changement du jour d'une séance (PUT /api/v1/seances/{id}, administrateur seul). La séance est lue sous verrou de ligne (409 si le verrou
 * n'est pas obtenu) et SEULE la colonne du jour est écrite : la facture rattachée, le forfait imposé, les signatures et la place sont
 * préservés (KB-SEA-49, corrigé : le legacy réécrivait toute la ligne et effaçait {@code facture_id}). Un jour absent ou vide est le jour du
 * centre ({@link HorlogeDuCentre}, jamais la JVM). Aucune règle de calendrier, aucune diffusion (KB-SEA-54, 55, 56, reproduits : ADR 0093).
 * Ordre des refus : centre (403), jour illisible (400, lu par {@code jourDemande} APRÈS le centre : 403 avant 400), séance introuvable
 * (400), séance facturée (422).
 */
public class ChangerLeJourDUneSeance {

    private final SessionCourante session;
    private final Seances seances;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public ChangerLeJourDUneSeance(SessionCourante session, Seances seances, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.seances = seances;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    /**
     * @param jourDemande lit le jour du corps ; rend nul s'il est absent (jour du centre) ; lève {@code SaisieInvalide} s'il est illisible
     */
    public Seance executer(UUID centreDemande, UUID seance, Supplier<LocalDate> jourDemande) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        LocalDate lu = jourDemande.get();
        LocalDate jour = lu != null ? lu : horloge.aujourdhui(centre);
        SeanceId id = new SeanceId(seance);
        return uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee.changerDeJour(jour);
            seances.enregistrerLeJour(trouvee);
            return trouvee;
        });
    }
}
