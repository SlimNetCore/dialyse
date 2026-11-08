package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.ObservationsMedicales;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.VoletMedical;
import com.hemodialyse.backend.contexts.seance.domain.VoletsMedicaux;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Saisie du volet médical d'une séance (PUT /api/v1/seances/{id}/volet-medical, administrateur ou médecin). REMPLACE les six observations en
 * entier : une observation absente du corps efface la valeur déjà saisie, sans historique (KB-SEA-59, reproduit avec alerte, ADR 0094) ; la
 * route reste ouverte sur une séance signée. La séance est lue sous verrou de ligne : deux saisies simultanées se suivent, verrou non obtenu
 * dans le délai : 409 {@code ECRITURE_CONCURRENTE}. Ordre des refus : centre (403), séance introuvable
 * (400), statut (422 : absente, créée, facturée). Aucune diffusion, aucune notification (reproduit).
 */
public class RenseignerLeVoletMedical {

    private final SessionCourante session;
    private final Seances seances;
    private final VoletsMedicaux volets;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public RenseignerLeVoletMedical(SessionCourante session, Seances seances, VoletsMedicaux volets, UniteDeTravail uniteDeTravail,
                                    GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.seances = seances;
        this.volets = volets;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    /** @param observationsDemandees lit les observations du corps, lues APRÈS le contrôle du centre ; aucun plafond de taille (KB-SEA-59) */
    public VoletMedical executer(UUID centreDemande, UUID seance, Supplier<ObservationsMedicales> observationsDemandees) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400
        ObservationsMedicales observations = observationsDemandees.get();
        SeanceId id = new SeanceId(seance);
        return uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee.exigerUnVoletMedical();
            var maintenant = horloge.maintenant();
            VoletMedical volet = volets.deLaSeance(centre, id).orElse(null);
            if (volet == null) {
                volet = VoletMedical.ouvrir(identifiants.suivant(), centre, id, observations, maintenant);
            } else {
                volet.remplacerLesObservations(observations, maintenant);
            }
            volets.enregistrer(volet);
            return volet;
        });
    }
}
