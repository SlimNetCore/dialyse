package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.OrdonnanceIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Signe une ordonnance : numéro {@code ORD-nnnnn} du centre dans l'ordre des signatures. Un refus (ordonnance annulée,
 * déjà signée...) CONSOMME un numéro : trou de la séquence légale reproduit du legacy (KB-MED-20, décision du 07/11, à confirmer
 * avec les métiers). Une ordonnance introuvable n'en consomme pas. Droit : MEDECIN.
 */
public class SignerUneOrdonnance {

    private final SessionCourante session;
    private final Ordonnances ordonnances;
    private final NumerotationDesOrdonnances numerotation;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public SignerUneOrdonnance(SessionCourante session, Ordonnances ordonnances, NumerotationDesOrdonnances numerotation,
                               UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.ordonnances = ordonnances;
        this.numerotation = numerotation;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public Ordonnance executer(UUID centreDemande, UUID patient, UUID id) {
        var centre = session.centre(centreDemande);
        return uniteDeTravail.executer(() -> {
            Ordonnance ordonnance = ordonnances.trouverEnVerrouillant(centre, new PatientId(patient), id)
                    .orElseThrow(OrdonnanceIntrouvable::new);
            // Le numéro est réservé dans sa propre transaction AVANT le contrôle de la transition (reproduit, KB-MED-20).
            String numero = uniteDeTravail.independante(() -> numerotation.suivant(centre));
            ordonnance.signer(numero, horloge.maintenant());
            ordonnances.enregistrerLaTransition(ordonnance);
            return ordonnance;
        });
    }
}
