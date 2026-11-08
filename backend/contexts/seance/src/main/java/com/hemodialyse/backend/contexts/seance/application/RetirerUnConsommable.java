package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Retrait d'un consommable (DELETE /api/v1/seances/{id}/consommables/{articleId}?centerId=, administrateur et infirmier) : la quantité de
 * l'article passe à zéro, SANS restitution de stock (KB-SEA-22 : le legacy promettait « restoring stock », rien ne sort ni ne rentre).
 * Tous les statuts sauf facturée (KB-SEA-24). Le retrait d'un article non déclaré répond comme un succès (KB-SEA-23) et est rediffusé
 * (KB-SEA-28), reproduits. L'auteur est le paramètre {@code userId} (défaut {@code system}), que le Stock ne lit pas (KB-SEA-33).
 */
public class RetirerUnConsommable {

    private final SessionCourante session;
    private final Seances seances;
    private final PatientsDeLaSeance patients;
    private final ConsommablesDeLaSeance consommables;
    private final UniteDeTravail uniteDeTravail;
    private final DiffusionsDeSeance diffusions;

    public RetirerUnConsommable(SessionCourante session, Seances seances, PatientsDeLaSeance patients, ConsommablesDeLaSeance consommables,
                                UniteDeTravail uniteDeTravail, DiffusionsDeSeance diffusions) {
        this.session = session;
        this.seances = seances;
        this.patients = patients;
        this.consommables = consommables;
        this.uniteDeTravail = uniteDeTravail;
        this.diffusions = diffusions;
    }

    /** @param auteurDuParametre paramètre {@code userId} (absent : {@code system}) */
    public void executer(UUID centreDemande, UUID seance, UUID article, String auteurDuParametre) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-26)
        if (centreDemande == null) throw new SaisieInvalide("Centre requis");
        ArticleId articleDemande = new ArticleId(article);
        SeanceId id = new SeanceId(seance);
        String auteur = auteurDuParametre == null ? "system" : auteurDuParametre;

        Declaration declaration = uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee.exigerLaModificationDesConsommables();
            FicheDuPatient fiche = patients.fiche(centre, trouvee.patient()).orElseThrow(PatientIntrouvable::new);
            consommables.retirer(centre, trouvee, articleDemande, auteur);
            return new Declaration(trouvee, fiche);
        });
        diffusions.consommablesModifies(centre, declaration.seance());
        diffusions.saisieDeLInfirmier(centre, "CONSOMMABLE_RETRAIT", declaration.seance(), declaration.patient(), session.utilisateur());
    }
}
