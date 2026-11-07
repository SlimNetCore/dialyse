package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilanIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDEtape;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Remplace le statut, la date de réalisation, le résultat, la date d'expiration et les références d'une étape (champ absent effacé, une
 * étape {@code FAIT} exige une date : 422, REPRODUITS). Étape inconnue, d'un autre patient ou d'un autre centre : 404
 * {@code ETAPE_BILAN_INTROUVABLE} (écart décidé KB-MED-88, avant : 400). Étape verrouillée : 409 en cas d'écriture simultanée.
 * Droit : MEDECIN et INFIRMIER.
 */
public class MettreAJourUneEtapeDeBilan {
    private final SessionCourante session;
    private final EtapesDeBilan etapes;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;

    public MettreAJourUneEtapeDeBilan(SessionCourante session, EtapesDeBilan etapes, UniteDeTravail uniteDeTravail, HorlogeDuCentre horloge) {
        this.session = session;
        this.etapes = etapes;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
    }

    public EtapeDeBilan executer(UUID centreDemande, UUID patientDemande, UUID id, StatutDEtape statut, LocalDate dateRealisation,
                                 String resultat, LocalDate dateExpiration, UUID demandeExamenId, UUID serologieId) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            EtapeDeBilan etape = etapes.trouverEnVerrouillant(centre, patient, id).orElseThrow(EtapeDeBilanIntrouvable::new);
            etape.mettreAJour(statut, dateRealisation, resultat, dateExpiration, demandeExamenId, serologieId, horloge.maintenant());
            etapes.enregistrer(etape);
            return etape;
        });
    }
}
