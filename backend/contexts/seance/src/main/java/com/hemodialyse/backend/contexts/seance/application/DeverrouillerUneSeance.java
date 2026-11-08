package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.Signataire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Déverrouillage pour régularisation (POST /api/v1/seances/{id}/deverrouiller-regularisation, administrateur seul) : une séance « créée »
 * d'un jour passé (le jour du centre, KB-SEA-01) devient validable par l'infirmier. L'auteur est le compte de la session. Sous verrou de la
 * ligne (KB-SEA-18). La diffusion part après la validation de la transaction, à chaque appel même si la séance l'était déjà (KB-SEA-14,
 * reproduit).
 */
public class DeverrouillerUneSeance {

    private final SessionCourante session;
    private final PatientsDeLaSeance patients;
    private final Seances seances;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;
    private final DiffusionsDeSeance diffusions;

    public DeverrouillerUneSeance(SessionCourante session, PatientsDeLaSeance patients, Seances seances, UniteDeTravail uniteDeTravail,
                                  HorlogeDuCentre horloge, DiffusionsDeSeance diffusions) {
        this.session = session;
        this.patients = patients;
        this.seances = seances;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.diffusions = diffusions;
    }

    public Seance executer(UUID centreDemande, UUID seance) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        SeanceId id = new SeanceId(seance);
        Signataire auteur = Signataire.lire(session.utilisateur());
        FicheDuPatient[] fiche = new FicheDuPatient[1];
        Seance deverrouillee = uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee.deverrouillerPourRegularisation(horloge.maintenant(), auteur, horloge.aujourdhui(centre));
            fiche[0] = patients.fiche(centre, trouvee.patient()).orElseThrow(PatientIntrouvable::new);
            seances.enregistrer(trouvee);
            return trouvee;
        });
        diffusions.seanceDeverrouillee(centre, deverrouillee, fiche[0]);
        return deverrouillee;
    }
}
