package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDEtape;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;

import java.util.UUID;

/**
 * Ajoute une étape libre à la checklist (statut {@code A_FAIRE}). Patient inconnu ou d'un autre centre : 404 (écart décidé KB-MED-86,
 * avant : étape orpheline). REPRODUIT : aucune unicité, la même étape peut être ajoutée plusieurs fois. Droit : MEDECIN et INFIRMIER.
 */
public class AjouterUneEtapeDeBilan {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final EtapesDeBilan etapes;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public AjouterUneEtapeDeBilan(SessionCourante session, PatientsDuCentre patients, EtapesDeBilan etapes, UniteDeTravail uniteDeTravail,
                                  GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.etapes = etapes;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public EtapeDeBilan executer(UUID centreDemande, UUID patientDemande, CategorieDEtape categorie, String libelle) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            EtapeDeBilan etape = EtapeDeBilan.ajouter(identifiants.suivant(), patient, centre, categorie, libelle, horloge.maintenant());
            etapes.ajouter(etape);
            return etape;
        });
    }
}
