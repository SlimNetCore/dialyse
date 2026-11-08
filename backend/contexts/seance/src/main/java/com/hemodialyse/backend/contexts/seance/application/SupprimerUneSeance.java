package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.MotifDeRefus;
import com.hemodialyse.backend.contexts.seance.domain.MotifDeSuppression;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.SuppressionDeSeance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Suppression d'une séance (DELETE /api/v1/seances/{id}, administrateur seul), TOUT OU RIEN dans une seule unité de travail, la séance lue
 * sous verrou de ligne (409 si le verrou n'est pas obtenu). Ordre du legacy, reproduit (KB-SEA-52) : centre (403), séance du centre (422
 * {@code SEANCE_INTROUVABLE}), motif (422), puis statut facturé (422) et, CORRECTION de KB-SEA-50, facture rattachée (422). Puis : le
 * Stock annule les consommations (sans restitution, KB-SEA-51), les saisies sont effacées, la séance est supprimée, la suppression est
 * journalisée. Ni la date, ni les signatures, ni le patient ne sont regardés (KB-SEA-53, reproduit). La diffusion {@code SEANCE_SUPPRIMEE}
 * part après la validation de l'unité de travail, sans notification.
 */
public class SupprimerUneSeance {

    private final SessionCourante session;
    private final Seances seances;
    private final AnnulationDesConsommations consommations;
    private final SaisiesDeLaSeance saisies;
    private final JournalDesSuppressions journal;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final DiffusionsDeSeance diffusions;

    public SupprimerUneSeance(SessionCourante session, Seances seances, AnnulationDesConsommations consommations,
                              SaisiesDeLaSeance saisies, JournalDesSuppressions journal, UniteDeTravail uniteDeTravail,
                              GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge, DiffusionsDeSeance diffusions) {
        this.session = session;
        this.seances = seances;
        this.consommations = consommations;
        this.saisies = saisies;
        this.journal = journal;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.diffusions = diffusions;
    }

    public SuppressionDeSeance executer(UUID centreDemande, UUID seance, String motif) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        SeanceId id = new SeanceId(seance);
        SuppressionDeSeance suppression = uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id)
                    .orElseThrow(() -> new SeanceRefusee(MotifDeRefus.SEANCE_A_SUPPRIMER_INTROUVABLE, "Séance introuvable"));
            MotifDeSuppression raison = new MotifDeSuppression(motif); // avant le statut : ordre du legacy
            SuppressionDeSeance faite = SuppressionDeSeance.de(identifiants.suivant(), trouvee, raison, session.utilisateur(),
                    horloge.maintenant());
            consommations.annuler(centre, id, faite.auteur());
            saisies.effacer(centre, id);
            seances.supprimer(trouvee);
            journal.enregistrer(faite);
            return faite;
        });
        diffusions.seanceSupprimee(centre, suppression);
        return suppression;
    }
}
