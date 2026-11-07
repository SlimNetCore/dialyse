package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.OrdonnanceIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/** Annule une ordonnance (depuis tout statut non terminal). Le numéro d'une ordonnance signée est conservé. Droit : MEDECIN. */
public class AnnulerUneOrdonnance {

    private final SessionCourante session;
    private final Ordonnances ordonnances;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public AnnulerUneOrdonnance(SessionCourante session, Ordonnances ordonnances, UniteDeTravail uniteDeTravail,
                                HorlogeDuCentre horloge) {
        this.session = session;
        this.ordonnances = ordonnances;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Ordonnance executer(UUID centreDemande, UUID patient, UUID id) {
        var centre = session.centre(centreDemande);
        return uniteDeTravail.executer(() -> {
            Ordonnance ordonnance = ordonnances.trouverEnVerrouillant(centre, new PatientId(patient), id)
                    .orElseThrow(OrdonnanceIntrouvable::new);
            ordonnance.annuler(horloge.maintenant());
            ordonnances.enregistrerLaTransition(ordonnance);
            return ordonnance;
        });
    }
}
