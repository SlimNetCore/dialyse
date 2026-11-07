package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.OrdonnanceIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/** Constate la remise de l'ordonnance signée au patient (statut IMPRIMEE, une seule fois). Ne génère aucun document. Droit : MEDECIN. */
public class MarquerUneOrdonnanceImprimee {

    private final SessionCourante session;
    private final Ordonnances ordonnances;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public MarquerUneOrdonnanceImprimee(SessionCourante session, Ordonnances ordonnances, UniteDeTravail uniteDeTravail,
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
            ordonnance.marquerImprimee(horloge.maintenant());
            ordonnances.enregistrerLaTransition(ordonnance);
            return ordonnance;
        });
    }
}
