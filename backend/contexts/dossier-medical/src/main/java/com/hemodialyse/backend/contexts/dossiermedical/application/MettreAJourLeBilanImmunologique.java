package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.BilanPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.BilansPreGreffe;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Remplace les quatre champs du bilan immunologique (groupe sanguin confirmé, typage HLA, PRA des classes I et II ; un champ absent est
 * effacé, REPRODUIT, KB-MED-91). Bornes de colonne en 400 ; bilan verrouillé (409 en cas d'écriture simultanée) ; patient inconnu ou d'un
 * autre centre : 404. Droit : MEDECIN.
 */
public class MettreAJourLeBilanImmunologique {
    private final SessionCourante session;
    private final BilansPreGreffe bilans;
    private final UniteDeTravail uniteDeTravail;
    private final HorlogeDuCentre horloge;
    private final OuvertureDuBilanPreGreffe ouverture;

    public MettreAJourLeBilanImmunologique(SessionCourante session, PatientsDuCentre patients, BilansPreGreffe bilans,
                                           UniteDeTravail uniteDeTravail, GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.bilans = bilans;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.ouverture = new OuvertureDuBilanPreGreffe(patients, bilans, identifiants, horloge);
    }

    public BilanPreGreffe executer(UUID centreDemande, UUID patientDemande, String groupeSanguinConfirme, String typageHla,
                                   BigDecimal praClasseI, BigDecimal praClasseII) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        return uniteDeTravail.executer(() -> {
            BilanPreGreffe bilan = ouverture.verrouiller(centre, patient);
            bilan.mettreAJourLeBilanImmunologique(groupeSanguinConfirme, typageHla, praClasseI, praClasseII, horloge.maintenant());
            bilans.enregistrer(bilan);
            return bilan;
        });
    }
}
