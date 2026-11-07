package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.CauseDeNonFacturation;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.Seances;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Crée une séance pour un patient du centre de la session (POST /api/v1/seances). Le POST ne contrôle PAS le planning (KB-SEA-03,
 * REPRODUIT, question métier prioritaire de l'ADR 0088) : seule la facturation conditionne la création (prise en charge, validation,
 * attestation). Sans date : le jour du centre (KB-SEA-01). Plusieurs séances le même jour restent permises (KB-SEA-02). Les droits
 * (administrateur, infirmier) sont portés par l'interface ; l'auteur n'est pas conservé (KB-SEA-09, reproduit).
 */
public class CreerUneSeance {

    private final SessionCourante session;
    private final PatientsDeLaSeance patients;
    private final GenerateursDesPatients generateurs;
    private final EligibiliteALaFacturation eligibilite;
    private final Seances seances;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final DiffusionsDeSeance diffusions;

    public CreerUneSeance(SessionCourante session, PatientsDeLaSeance patients, GenerateursDesPatients generateurs,
                          EligibiliteALaFacturation eligibilite, Seances seances, UniteDeTravail uniteDeTravail,
                          GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge, DiffusionsDeSeance diffusions) {
        this.session = session;
        this.patients = patients;
        this.generateurs = generateurs;
        this.eligibilite = eligibilite;
        this.seances = seances;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.diffusions = diffusions;
    }

    public SeanceEnregistree executer(UUID centreDemande, UUID patientDemande, LocalDate date) {
        var centre = session.centre(centreDemande); // 403 avant tout 400 (KB-SEA-04)
        var patient = new PatientId(patientDemande);
        LocalDate jour = date != null ? date : horloge.aujourdhui(centre);
        var enregistree = uniteDeTravail.executer(() -> {
            FicheDuPatient fiche = patients.fiche(centre, patient).orElseThrow(PatientIntrouvable::new);
            CauseDeNonFacturation.exigerFacturable(eligibilite.cause(centre, patient, jour), jour);
            Seance seance = Seance.creer(new SeanceId(identifiants.suivant()), centre, patient, jour, horloge.maintenant());
            seances.ajouter(seance);
            return new SeanceEnregistree(seance, Generateurs.preciser(generateurs, centre, fiche));
        });
        diffusions.seanceCreee(centre, enregistree.seance(), enregistree.patient());
        diffusions.saisieDeLInfirmier(centre, "SEANCE_CREEE", enregistree.seance(), enregistree.patient(), session.utilisateur());
        return enregistree;
    }
}
