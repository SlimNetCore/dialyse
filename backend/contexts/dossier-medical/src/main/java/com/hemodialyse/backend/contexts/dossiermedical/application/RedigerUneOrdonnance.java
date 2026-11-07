package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Medicament;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Rédige une ordonnance (brouillon) pour un patient du centre de la session. Écart décidé (KB-MED-19) : un patient inconnu ou
 * d'un autre centre est refusé (404), le legacy créait une ordonnance orpheline. Droit : MEDECIN (porté par l'interface).
 */
public class RedigerUneOrdonnance {

    /** Ligne demandée telle que saisie ; un code blanc est ignoré (reproduit), un code non blanc exige une nomenclature. */
    public record LigneDemandee(String nomenclature, String code, String libelleDuCode, String libelle, String posologie, String voie,
                                Integer dureeJours, Integer quantite, String instructions) {
    }

    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final Ordonnances ordonnances;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public RedigerUneOrdonnance(SessionCourante session, PatientsDuCentre patients, Ordonnances ordonnances,
                                UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.ordonnances = ordonnances;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public Ordonnance executer(UUID centreDemande, UUID patientDemande, String medecin, LocalDate datePrescription,
                               List<LigneDemandee> lignesDemandees) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        if (lignesDemandees == null) throw new SaisieInvalide("Les lignes de l'ordonnance sont requises");
        var lignes = new ArrayList<LigneDOrdonnance>();
        for (LigneDemandee demandee : lignesDemandees) {
            if (demandee == null) throw new SaisieInvalide("Une ligne d'ordonnance est vide");
            Medicament medicament = demandee.code() == null || demandee.code().isBlank() ? null
                    : new Medicament(demandee.nomenclature(), demandee.code(), demandee.libelleDuCode());
            lignes.add(LigneDOrdonnance.nouvelle(identifiants.suivant(), medicament, demandee.libelle(), demandee.posologie(),
                    demandee.voie(), demandee.dureeJours(), demandee.quantite(), demandee.instructions()));
        }
        var ordonnance = Ordonnance.rediger(identifiants.suivant(), patient, centre, medecin,
                datePrescription != null ? datePrescription : horloge.aujourdhui(centre), lignes, horloge.maintenant());
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            ordonnances.ajouter(ordonnance);
            return ordonnance;
        });
    }
}
