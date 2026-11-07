package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AllergieIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CriticiteDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeVerification;

import java.util.UUID;

/**
 * Corrige la criticité, les manifestations et le statut d'une allergie du patient : la substance, la catégorie, le type de réaction et
 * la date ne changent jamais ; les manifestations du corps REMPLACENT les anciennes, même absentes ; un statut absent est conservé
 * (KB-MED-66, reproduit, à confirmer avec les métiers et le DPO). Allergie inconnue, d'un autre patient ou d'un autre centre : refus 400
 * (reproduit, KB-MED-60). Droit : MEDECIN.
 */
public class CorrigerUneAllergie {
    private final SessionCourante session;
    private final Allergies allergies;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public CorrigerUneAllergie(SessionCourante session, Allergies allergies, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.allergies = allergies;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Allergie executer(UUID centreDemande, UUID patientDemande, UUID id, String criticiteSaisie, String manifestations,
                             String statutSaisi) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        var criticite = CriticiteDAllergie.lire(criticiteSaisie);
        var statut = StatutDeVerification.lireFacultatif(statutSaisi);
        return uniteDeTravail.executer(() -> {
            Allergie allergie = allergies.trouverEnVerrouillant(centre, patient, id).orElseThrow(AllergieIntrouvable::new);
            allergie.corriger(criticite, manifestations, statut, horloge.maintenant());
            allergies.enregistrerLaCorrection(allergie);
            return allergie;
        });
    }
}
