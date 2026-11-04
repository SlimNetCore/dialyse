package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.domain.Patient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientId;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecIntrouvable;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PriseEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PrisesEnCharge;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cycle de la prise en charge (slice PC3) : création, validation, clôture, suppression, {@code session-allowed}, listes. Garde de
 * centre sur tout (centre du jeton) ; chaque écriture prend le verrou du centre en tête (ordre global centre, fiches, app_user)
 * dans une unité de travail ; une PEC inconnue ou d'un autre centre est introuvable (404).
 */
public class GererLesPec {

    /** Une PEC et le nom réel de son patient (pour les diffusions). */
    public record PecEtPatient(PriseEnCharge pec, String nomDuPatient) {
    }

    private final SessionCourante session;
    private final HorlogeDesCentres horloge;
    private final Identifiants identifiants;
    private final PrisesEnCharge pecs;
    private final PatientRepository patients;
    private final AttestationsDuPatient attestations;
    private final UniteDeTravail unite;
    private final VerrouDuCentre verrou;

    public GererLesPec(SessionCourante session, HorlogeDesCentres horloge, Identifiants identifiants, PrisesEnCharge pecs,
                       PatientRepository patients, AttestationsDuPatient attestations, UniteDeTravail unite, VerrouDuCentre verrou) {
        this.session = session;
        this.horloge = horloge;
        this.identifiants = identifiants;
        this.pecs = pecs;
        this.patients = patients;
        this.attestations = attestations;
        this.unite = unite;
        this.verrou = verrou;
    }

    /** Garde de centre seule (avant une lecture mise en cache). */
    public CentreId centre(UUID centreDemande) {
        return session.centre(centreDemande);
    }

    public PriseEnCharge creer(UUID centreDemande, UUID patientDemande, LocalDate debut, LocalDate fin, UUID forfait) {
        CentreId centre = session.centre(centreDemande);
        return unite.executer(() -> {
            verrou.prendre(centre);
            PatientId id = new PatientId(patientDemande);
            Patient patient = patients.trouver(centre, id).orElseThrow(() -> new SaisieInvalide("Patient introuvable"));
            PriseEnCharge.verifierAttestation(patient.vacancier(), attestations.valideLe(centre, id, horloge.aujourdhui(centre)));
            PriseEnCharge pec = PriseEnCharge.demander(identifiants.nouveau(), id, centre, debut, fin, forfait, horloge.maintenant());
            pecs.enregistrer(pec);
            return pec;
        });
    }

    public PecEtPatient valider(UUID centreDemande, UUID pecId, LocalDate debut, LocalDate fin, UUID forfait) {
        CentreId centre = session.centre(centreDemande);
        return unite.executer(() -> {
            verrou.prendre(centre);
            PriseEnCharge pec = pecs.trouver(centre, pecId).orElseThrow(PecIntrouvable::new);
            pec.valider(debut, fin, forfait);
            pecs.enregistrer(pec);
            return new PecEtPatient(pec, nomDuPatient(centre, pec));
        });
    }

    public PecEtPatient cloturer(UUID centreDemande, UUID pecId) {
        CentreId centre = session.centre(centreDemande);
        return unite.executer(() -> {
            verrou.prendre(centre);
            PriseEnCharge pec = pecs.trouver(centre, pecId).orElseThrow(PecIntrouvable::new);
            pec.cloturer();
            pecs.enregistrer(pec);
            return new PecEtPatient(pec, nomDuPatient(centre, pec));
        });
    }

    /** Suppression possible quel que soit le statut, y compris validée (reproduit, à décider avec les métiers, KB-PAT-24). */
    public void supprimer(UUID centreDemande, UUID pecId) {
        CentreId centre = session.centre(centreDemande);
        unite.executer(() -> {
            verrou.prendre(centre);
            pecs.trouver(centre, pecId).orElseThrow(PecIntrouvable::new);
            pecs.supprimer(pecId);
            return null;
        });
    }

    /**
     * Règle « session-allowed » : non vacancier sans attestation valide ce jour (du centre) : refusé ; sinon le statut seul décide
     * (reproduit, KB-PAT-25 : la règle de création de séance est une autre règle).
     */
    public boolean autoriseUneSeance(UUID centreDemande, UUID pecId) {
        CentreId centre = session.centre(centreDemande);
        PriseEnCharge pec = pecs.trouver(centre, pecId).orElseThrow(PecIntrouvable::new);
        Patient patient = patients.trouver(centre, pec.patient()).orElseThrow(PatientIntrouvable::new);
        return pec.autoriseSeance(patient.vacancier(), attestations.valideLe(centre, pec.patient(), horloge.aujourdhui(centre)));
    }

    public List<PriseEnCharge> duCentre(UUID centreDemande) {
        return pecs.duCentre(session.centre(centreDemande));
    }

    public List<PriseEnCharge> duPatient(UUID centreDemande, UUID patient) {
        return pecs.duPatient(session.centre(centreDemande), new PatientId(patient));
    }

    private String nomDuPatient(CentreId centre, PriseEnCharge pec) {
        return patients.trouver(centre, pec.patient()).map(p -> {
            var i = p.fiche().identite();
            String prenom = i.prenom() == null ? "" : i.prenom().trim();
            String nom = i.nom() == null ? "" : i.nom().trim();
            String complet = (prenom + " " + nom).trim();
            return complet.isEmpty() ? NOM_DE_REPLI : complet;
        }).orElse(NOM_DE_REPLI);
    }

    /** Nom diffusé quand la fiche n'en porte aucun (ligne héritée) : les diffusions n'acceptent pas de valeur nulle. */
    static final String NOM_DE_REPLI = "Patient";
}
