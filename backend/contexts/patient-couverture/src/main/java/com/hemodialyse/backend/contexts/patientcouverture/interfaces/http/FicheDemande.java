package com.hemodialyse.backend.contexts.patientcouverture.interfaces.http;

import com.hemodialyse.backend.contexts.patientcouverture.application.CouvertureDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.application.SaisieDeLaFiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Affectation;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Coordonnees;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Couverture;
import com.hemodialyse.backend.contexts.patientcouverture.domain.EtatDuPatient;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Fiche;
import com.hemodialyse.backend.contexts.patientcouverture.domain.Identite;
import com.hemodialyse.backend.contexts.patientcouverture.domain.NumeroAssurance;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.patientcouverture.domain.SuiviMedical;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Corps de {@code POST} et {@code PUT /api/v1/patients} : mêmes champs, mêmes noms que la requête actuelle (le front ne change
 * pas). Les booléens et {@code nombreEnfants} sont obligatoires : absents ou nuls, 400 {@code BAD_REQUEST} (avant : 500).
 * L'identifiant de place s'appelle {@code positionId} dans le corps (le créneau de la fiche).
 */
record FicheDemande(
        UUID centerId, String userId,
        String civilite, String nom, String prenom, String sexe, String groupeSanguin, int nombreEnfants,
        LocalDate dateAdmission, LocalDate dateNaissance, String lieuNaissance, String situationFamiliale,
        String profession, String adresse, String telPersonnel, String telMobile, String telBureau, String email,
        boolean sousKt, boolean epoEnabled, LocalDate epoDate, boolean ferEnabled, LocalDate ferDate,
        String observation, String qualiteAssure, String photoBase64, boolean enSommeil,
        String numeroAssurance, UUID centrePayeurId, String assureNumeroAssurance,
        String assureSexe, String assureNom, String assurePrenom, String assureDateNaissance,
        String assureTelPersonnel, String assureAdresse, String assureGroupeSanguin,
        String assureTelMobile, String assureTelBureau,
        String assureHistoryJson,
        String piecesJointesJson,
        UUID medecinTraitantId, UUID salleId, UUID positionId,
        UUID transporteurAllerId, UUID transporteurRetourId, UUID categorieTransportId,
        UUID generateurId,
        String etatPatient,
        LocalDate dateEvenementEtat,
        boolean jourDimanche, boolean jourLundi, boolean jourMardi,
        boolean jourMercredi, boolean jourJeudi, boolean jourVendredi, boolean jourSamedi,
        UUID attestationId, LocalDate attestationDebut, LocalDate attestationFin,
        UUID pecId, LocalDate pecDateDebutDemande, LocalDate pecDateFinDemande, UUID pecForfaitDemandeId
) {

    SaisieDeLaFiche enSaisie() {
        if (centerId == null) throw new SaisieInvalide("CenterId cannot be null");
        // Assuré lui-même : la fiche recopie l'état civil saisi, téléphones tels que saisis (contrat actuel de assureInfo)
        boolean luiMeme = Couverture.ASSURE_LUI_MEME.equals(qualiteAssure);
        Couverture.AssureSurLaFiche assure = luiMeme
                ? new Couverture.AssureSurLaFiche(sexe, nom, prenom, dateNaissance == null ? null : dateNaissance.toString(),
                telPersonnel, adresse, groupeSanguin, telMobile, telBureau)
                : new Couverture.AssureSurLaFiche(assureSexe, assureNom, assurePrenom, assureDateNaissance, assureTelPersonnel,
                assureAdresse, assureGroupeSanguin, assureTelMobile, assureTelBureau);
        Fiche fiche = new Fiche(
                Identite.saisie(civilite, nom, prenom, sexe, groupeSanguin, nombreEnfants, dateNaissance, lieuNaissance,
                        situationFamiliale),
                Coordonnees.saisies(profession, adresse, telPersonnel, telMobile, telBureau, email),
                new SuiviMedical(sousKt, epoEnabled, epoDate, ferEnabled, ferDate, enSommeil, observation, photoBase64),
                new Couverture(new NumeroAssurance(numeroAssurance), qualiteAssure, centrePayeurId, assureNumeroAssurance,
                        assure, assureHistoryJson, piecesJointesJson),
                new Affectation(medecinTraitantId, salleId, positionId, transporteurAllerId, transporteurRetourId,
                        categorieTransportId, generateurId,
                        new Affectation.Jours(jourDimanche, jourLundi, jourMardi, jourMercredi, jourJeudi, jourVendredi,
                                jourSamedi)),
                dateAdmission, etatPatient == null ? null : EtatDuPatient.depuis(etatPatient), dateEvenementEtat);
        return new SaisieDeLaFiche(centerId, fiche,
                new CouvertureDeLaFiche.AttestationSaisie(attestationId, attestationDebut, attestationFin),
                new CouvertureDeLaFiche.DemandeDePec(pecId, pecDateDebutDemande, pecDateFinDemande, pecForfaitDemandeId));
    }
}
