package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.AvisRcp;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ajoute une décision de RCP au bilan (date de réunion et avis obligatoires, date jamais dans le futur du jour de l'horloge : 422,
 * reproduit). Le bilan est verrouillé : deux décisions simultanées sont toutes les deux conservées, l'une attend l'autre (au plus 5 s puis
 * 409), plus d'écrasement silencieux (KB-MED-91). Patient inconnu ou d'un autre centre : 404. Droit : MEDECIN.
 */
public class AjouterUneDecisionDeRcp {
    private final SessionCourante session;
    private final BilansPreGreffe bilans;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;
    private final OuvertureDuBilanPreGreffe ouverture;

    public AjouterUneDecisionDeRcp(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans, UniteDeTravail uniteDeTravail,
                                   GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.bilans = bilans;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
        this.ouverture = new OuvertureDuBilanPreGreffe(patients, bilans, identifiants, horloge);
    }

    public BilanPreGreffe executer(UUID centreDemande, UUID patientDemande, LocalDate dateReunion, AvisRcp avis, String compteRendu,
                                   LocalDate prochaineDateRevue) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            BilanPreGreffe bilan = ouverture.verrouiller(centre, patient);
            bilan.ajouterUneDecision(identifiants.suivant(), dateReunion, avis, compteRendu, prochaineDateRevue, horloge.aujourdhui(centre),
                    horloge.maintenant());
            bilans.enregistrer(bilan);
            return bilan;
        });
    }
}
