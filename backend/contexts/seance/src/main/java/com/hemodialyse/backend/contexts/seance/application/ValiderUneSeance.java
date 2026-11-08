package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seances;
import com.hemodialyse.backend.contexts.seance.domain.Signataire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Validation de la séance par l'infirmier (POST /api/v1/seances/{id}/valider, administrateur et infirmier). Dans une seule unité de
 * travail, sous verrou de la ligne (KB-SEA-18) : verrou des jours passés (avant le statut, KB-SEA-12), validation (idempotente), place du
 * patient mémorisée si elle ne l'est pas, consommables relevés (cumulés à chaque validation répétée, KB-SEA-15 reproduit), enregistrement.
 * Les diffusions partent après la validation de la transaction, à chaque appel même sans changement (KB-SEA-14, reproduit). Le signataire
 * est celui du corps de la requête (KB-SEA-11, reproduit : à faire confirmer avant la mise en production), borné à 100 caractères.
 */
public class ValiderUneSeance {

    private final SessionCourante session;
    private final PatientsDeLaSeance patients;
    private final Seances seances;
    private final PlaceDuPatient places;
    private final ConsommationsALaValidation consommations;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;
    private final DiffusionsDeSeance diffusions;

    public ValiderUneSeance(SessionCourante session, PatientsDeLaSeance patients, Seances seances, PlaceDuPatient places,
                            ConsommationsALaValidation consommations, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge,
                            DiffusionsDeSeance diffusions) {
        this.session = session;
        this.patients = patients;
        this.seances = seances;
        this.places = places;
        this.consommations = consommations;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.diffusions = diffusions;
    }

    /**
     * @param signataire compte du corps de la requête (absent : {@code system})
     * @param lignes     consommables relevés ({@code null} ou vide : aucun)
     */
    public Validation executer(UUID centreDemande, UUID seance, String signataire, List<Ligne> lignes) {
        CentreId centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        SeanceId id = new SeanceId(seance);
        Signataire auteur = Signataire.lire(signataire);
        List<Ligne> relevees = lignes == null ? List.of() : lignes;
        for (Ligne ligne : relevees) {
            if (ligne == null || ligne.article() == null) throw new SaisieInvalide("Article de consommation invalide");
        }
        String utilisateur = session.utilisateur();

        Validation resultat = uniteDeTravail.executer(() -> {
            Seance trouvee = seances.trouverPourModifier(centre, id).orElseThrow(SeanceIntrouvable::new);
            FicheDuPatient fiche = patients.fiche(centre, trouvee.patient()).orElseThrow(PatientIntrouvable::new);
            LocalDate aujourdhui = horloge.aujourdhui(centre);
            trouvee.exigerLaRegularisation(aujourdhui, session.estAdministrateur());
            trouvee.validerParLInfirmier(horloge.maintenant(), auteur);
            if (trouvee.traces().place() == null) {
                places.placeLe(centre, trouvee.patient(), trouvee.jour()).ifPresent(trouvee::memoriserPlace);
            }
            relever(centre, trouvee, relevees, auteur);
            seances.enregistrer(trouvee);
            return new Validation(trouvee, fiche);
        });
        diffusions.seanceValidee(centre, resultat.seance(), resultat.patient());
        diffusions.saisieDeLInfirmier(centre, "SEANCE_VALIDEE", resultat.seance(), resultat.patient(), utilisateur);
        return resultat;
    }

    private void relever(CentreId centre, Seance seance, List<Ligne> lignes, Signataire auteur) {
        if (lignes.isEmpty()) return;
        List<ConsommationsALaValidation.Ligne> retenues = new ArrayList<>();
        for (Ligne ligne : lignes) {
            var article = consommations.article(centre, ligne.article())
                    .orElseThrow(() -> new SaisieInvalide("Article introuvable: " + ligne.article()));
            if (!article.actif()) throw new SaisieInvalide("Article inactif: " + article.code());
            retenues.add(new ConsommationsALaValidation.Ligne(ligne.article(), ligne.quantite()));
        }
        consommations.enregistrer(centre, seance, retenues, auteur.valeur());
    }

    /** Un consommable relevé : l'article et la quantité (le Stock contrôle la quantité). */
    public record Ligne(UUID article, BigDecimal quantite) {
    }

    public record Validation(Seance seance, FicheDuPatient patient) {
    }
}
