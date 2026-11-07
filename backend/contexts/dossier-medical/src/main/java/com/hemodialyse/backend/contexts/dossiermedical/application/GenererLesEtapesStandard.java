package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapeDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EtapesDeBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ListeStandardDesEtapes;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Génère les étapes manquantes de la liste standard (23 étapes) : une étape déjà présente (même catégorie et même libellé, casse
 * comprise, quel que soit son statut ou son état de lecture) n'est ni recréée ni modifiée (REPRODUIT). Sous verrou de génération du
 * patient : deux générations simultanées ne doublent plus rien (écart décidé KB-MED-90). Patient inconnu ou d'un autre centre : 404.
 * Droit : MEDECIN et INFIRMIER.
 */
public class GenererLesEtapesStandard {
    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final EtapesDeBilan etapes;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public GenererLesEtapesStandard(SessionCourante session, PatientsDuCentre patients, EtapesDeBilan etapes, UniteDeTravail uniteDeTravail,
                                    GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.etapes = etapes;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public List<EtapeDeBilan> executer(UUID centreDemande, UUID patientDemande) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            etapes.verrouillerLaGeneration(centre, patient);
            Set<String> presentes = etapes.clesDuPatient(centre, patient);
            for (ListeStandardDesEtapes.EtapeStandard standard : ListeStandardDesEtapes.ETAPES) {
                if (!presentes.contains(standard.cle())) {
                    etapes.ajouter(EtapeDeBilan.ajouter(identifiants.suivant(), patient, centre, standard.categorie(), standard.libelle(),
                            horloge.maintenant()));
                }
            }
            return etapes.duPatient(centre, patient);
        });
    }
}
