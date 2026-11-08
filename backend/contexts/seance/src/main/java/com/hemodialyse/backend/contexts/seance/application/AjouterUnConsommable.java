package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.ArticleId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.QuantiteConsommee;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Ajout d'un consommable à une séance validée ou signée (POST /api/v1/seances/{id}/consommables, administrateur et infirmier). Dans une
 * seule unité de travail, sous verrou de la ligne de la séance (deux ajouts simultanés se suivent, 409 si le verrou n'est pas obtenu) :
 * séance, garde de statut, patient LU AVANT d'écrire (KB-SEA-27 corrigé : plus d'écriture suivie d'un 400), déclaration au Stock. Les
 * diffusions partent après la validation de la transaction, à chaque appel (KB-SEA-28, reproduit). L'ajout CUMULE (KB-SEA-23), sans clé
 * d'idempotence : un double clic compte deux fois (reproduit, à décider). L'auteur est le compte de la session (KB-SEA-33 : le Stock ne
 * l'enregistre pas).
 */
public class AjouterUnConsommable {

    private final SessionCourante session;
    private final Seances seances;
    private final PatientsDeLaSeance patients;
    private final ConsommablesDeLaSeance consommables;
    private final UniteDeTravail uniteDeTravail;
    private final DiffusionsDeSeance diffusions;

    public AjouterUnConsommable(SessionCourante session, Seances seances, PatientsDeLaSeance patients, ConsommablesDeLaSeance consommables,
                                UniteDeTravail uniteDeTravail, DiffusionsDeSeance diffusions) {
        this.session = session;
        this.seances = seances;
        this.patients = patients;
        this.consommables = consommables;
        this.uniteDeTravail = uniteDeTravail;
        this.diffusions = diffusions;
    }

    public void executer(UUID centreDemande, UUID seance, UUID article, BigDecimal quantite) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04, KB-SEA-26)
        if (centreDemande == null) throw new SaisieInvalide("Centre requis");
        ArticleId articleDemande = new ArticleId(article);
        QuantiteConsommee aAjouter = QuantiteConsommee.aAjouter(quantite);
        SeanceId id = new SeanceId(seance);
        String auteur = session.utilisateur();

        SeanceDeclaree declaration =uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee.exigerLAjoutDeConsommables();
            FicheDuPatient fiche = patients.fiche(centre, trouvee.patient()).orElseThrow(PatientIntrouvable::new);
            consommables.ajouter(centre, trouvee, articleDemande, aAjouter, auteur);
            return new SeanceDeclaree(trouvee, fiche);
        });
        diffusions.consommablesModifies(centre, declaration.seance());
        diffusions.saisieDeLInfirmier(centre, "CONSOMMABLE_AJOUT", declaration.seance(), declaration.patient(), auteur);
    }
}
