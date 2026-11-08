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
 * Modification de la quantité TOTALE d'un consommable (PUT /api/v1/seances/{id}/consommables/{articleId}, administrateur et infirmier) :
 * absolue, elle crée la ligne d'un article jamais déclaré (KB-SEA-23, reproduit). Tous les statuts sauf facturée (KB-SEA-24, reproduit).
 * Même unité de travail, même verrou et mêmes diffusions que l'ajout ; une quantité identique est rediffusée (KB-SEA-28, reproduit).
 * L'auteur est celui du corps de la requête, absent : {@code system} (KB-SEA-33 : usurpable, et le Stock ne l'enregistre pas).
 */
public class ModifierLaQuantiteDUnConsommable {

    private final SessionCourante session;
    private final Seances seances;
    private final PatientsDeLaSeance patients;
    private final ConsommablesDeLaSeance consommables;
    private final UniteDeTravail uniteDeTravail;
    private final DiffusionsDeSeance diffusions;

    public ModifierLaQuantiteDUnConsommable(SessionCourante session, Seances seances, PatientsDeLaSeance patients,
                                            ConsommablesDeLaSeance consommables, UniteDeTravail uniteDeTravail,
                                            DiffusionsDeSeance diffusions) {
        this.session = session;
        this.seances = seances;
        this.patients = patients;
        this.consommables = consommables;
        this.uniteDeTravail = uniteDeTravail;
        this.diffusions = diffusions;
    }

    /** @param auteurDuCorps compte du corps de la requête (absent : {@code system}) */
    public void executer(UUID centreDemande, UUID seance, UUID article, BigDecimal quantite, String auteurDuCorps) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-26)
        if (centreDemande == null) throw new SaisieInvalide("Centre requis");
        ArticleId articleDemande = new ArticleId(article);
        QuantiteConsommee nouvelle = QuantiteConsommee.nouvelle(quantite);
        SeanceId id = new SeanceId(seance);
        String auteur = auteurDuCorps == null ? "system" : auteurDuCorps;

        Declaration declaration = uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            trouvee.exigerLaModificationDesConsommables();
            FicheDuPatient fiche = patients.fiche(centre, trouvee.patient()).orElseThrow(PatientIntrouvable::new);
            consommables.remplacer(centre, trouvee, articleDemande, nouvelle, auteur);
            return new Declaration(trouvee, fiche);
        });
        diffusions.consommablesModifies(centre, declaration.seance());
        diffusions.saisieDeLInfirmier(centre, "CONSOMMABLE_MODIF", declaration.seance(), declaration.patient(), session.utilisateur());
    }
}
