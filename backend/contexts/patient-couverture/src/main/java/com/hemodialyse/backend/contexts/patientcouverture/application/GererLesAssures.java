package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.application.AssuresDuCentre.Assure;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePrincipal;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Assurés et affectations de la fiche (routes {@code /patients/assures/**} actuelles, slice PC2 à reprendre) : garde de centre sur
 * toutes les lectures et toutes les écritures (décision du 02/11, KB-PAT-07), jour d'affectation = jour du centre (KB-PAT-17).
 */
public class GererLesAssures {

    /** Ligne de l'historique : l'affectation et l'identité de l'assuré (nulle si l'assuré a disparu). */
    public record LigneDHistorique(Affectation affectation, Assure assure) {
    }

    private final SessionCourante session;
    private final HorlogeDesCentres horloge;
    private final PatientRepository patients;
    private final AssuresDuCentre assures;
    private final UniteDeTravail unite;
    private final VerrouDuCentre verrou;

    public GererLesAssures(SessionCourante session, HorlogeDesCentres horloge, PatientRepository patients,
                           AssuresDuCentre assures, UniteDeTravail unite, VerrouDuCentre verrou) {
        this.verrou = verrou;
        this.session = session;
        this.horloge = horloge;
        this.patients = patients;
        this.assures = assures;
        this.unite = unite;
    }

    public List<Assure> lister(UUID centreDemande, String texte) {
        return assures.rechercher(session.centre(centreDemande), texte);
    }

    public List<LigneDHistorique> historique(UUID centreDemande, UUID patient) {
        CentreId centre = session.centre(centreDemande);
        return assures.historique(centre, new PatientId(patient)).stream()
                .map(a -> new LigneDHistorique(a, assures.trouver(a.numeroAssurance()).orElse(null))).toList();
    }

    /** Affecte l'assuré comme principal, à compter d'aujourd'hui (jour du centre) ; ferme l'affectation précédente. */
    public record Affectee(UUID id, Assure assure, LocalDate dateDebut) {
    }

    public Affectee affecter(UUID centreDemande, UUID patientDemande, String numeroAssurance) {
        CentreId centre = session.centre(centreDemande);
        return unite.executer(() -> {
            // R1 : verrou du centre EN TÊTE (deux affectations simultanées ne laissent plus deux principaux actifs, KB-PAT-29)
            verrou.prendre(centre);
            PatientId id = new PatientId(patientDemande);
            var patient = patients.trouver(centre, id).orElseThrow(PatientIntrouvable::new);
            if (patient.fiche().couverture().assureLuiMeme()) {
                throw new SaisieInvalide("Impossible d'affecter un assuré si le patient est assuré lui-même");
            }
            Assure assure = assures.trouver(numeroAssurance).orElseThrow(() -> new SaisieInvalide("Assuré introuvable"));
            verifierLeCentre(assure, centre);
            LocalDate debut = horloge.aujourdhui(centre);
            assures.fermerLAffectationPrincipale(centre, id, debut);
            UUID nouvelle = assures.ajouterUneAffectationPrincipale(centre, id, numeroAssurance, debut);
            return new Affectee(nouvelle, assure, debut);
        });
    }

    public void modifierLAffectation(UUID centreDemande, UUID patient, UUID affectation, LocalDate debut, LocalDate fin) {
        CentreId centre = session.centre(centreDemande);
        unite.executer(() -> {
            verrou.prendre(centre);
            Affectation courante = assures.trouverAffectation(affectation)
                    .orElseThrow(() -> new SaisieInvalide("Affectation introuvable: " + affectation));
            if (!courante.patientId().equals(patient) || !courante.centre().equals(centre.valeur())) {
                throw new AccesRefuse();
            }
            AssurePrincipal.verifierLesDatesDeLAffectation(debut, fin);
            assures.modifierLesDates(affectation, debut, fin);
            return null;
        });
    }

    public Assure modifierUnAssure(UUID centreDemande, String numeroAssurance, Assure saisie) {
        CentreId centre = session.centre(centreDemande);
        AssurePrincipal.verifierPourModification(saisie.nom());
        return unite.executer(() -> {
            verrou.prendre(centre);
            Assure existant = assures.trouver(numeroAssurance)
                    .orElseThrow(() -> new SaisieInvalide("Assuré introuvable: " + numeroAssurance));
            verifierLeCentre(existant, centre);
            Assure modifie = new Assure(existant.numeroAssurance(), saisie.nom(), saisie.prenom(), saisie.sexe(),
                    saisie.dateNaissance(), saisie.telPersonnel(), saisie.telMobile(), saisie.telBureau(), saisie.adresse(),
                    saisie.groupeSanguin(), existant.centre());
            assures.mettreAJour(modifie);
            return modifie;
        });
    }

    private static void verifierLeCentre(Assure assure, CentreId centre) {
        if (!centre.valeur().equals(assure.centre())) throw new AccesRefuse();
    }

    /** Qualité d'assuré « lui-même » (constante du domaine, rappelée ici pour les interfaces). */
    public static final String ASSURE_LUI_MEME = Couverture.ASSURE_LUI_MEME;
}
