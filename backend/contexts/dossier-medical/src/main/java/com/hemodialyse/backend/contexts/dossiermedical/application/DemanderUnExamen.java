package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDeDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Demande un examen pour un patient du centre de la session. Écart décidé (KB-MED-38) : un patient inconnu ou d'un autre centre est
 * refusé (404), le legacy créait une demande orpheline. Droit : MEDECIN (porté par l'interface). Ordre des contrôles reproduit :
 * lignes, puis catégorie, puis règles de la demande (au moins une ligne, date non future), puis existence du patient.
 */
public class DemanderUnExamen {

    /** Ligne demandée telle que saisie ; un code blanc est ignoré (reproduit), un code non blanc exige une nomenclature. */
    public record LigneDemandee(String nomenclature, String code, String libelleDuCode, String libelle, String commentaire) {
    }

    private final SessionCourante session;
    private final PatientsDuCentre patients;
    private final DemandesDExamen demandes;
    private final UniteDeTravail uniteDeTravail;
    private final GenerateurDIdentifiants identifiants;
    private final HorlogeDuCentre horloge;

    public DemanderUnExamen(SessionCourante session, PatientsDuCentre patients, DemandesDExamen demandes, UniteDeTravail uniteDeTravail,
                            GenerateurDIdentifiants identifiants, HorlogeDuCentre horloge) {
        this.session = session;
        this.patients = patients;
        this.demandes = demandes;
        this.uniteDeTravail = uniteDeTravail;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    public DemandeExamen executer(UUID centreDemande, UUID patientDemande, String prescripteur, LocalDate dateDemande, String categorie,
                                  Boolean urgent, String motif, List<LigneDemandee> lignesDemandees) {
        var centre = session.centre(centreDemande);
        var patient = new PatientId(patientDemande);
        if (lignesDemandees == null) throw new SaisieInvalide("Les lignes de la demande sont requises");
        var lignes = new ArrayList<LigneDeDemandeDExamen>();
        for (LigneDemandee demandee : lignesDemandees) {
            if (demandee == null) throw new SaisieInvalide("Une ligne de la demande est vide");
            CodeDExamen code = demandee.code() == null || demandee.code().isBlank() ? null
                    : new CodeDExamen(demandee.nomenclature(), demandee.code(), demandee.libelleDuCode());
            lignes.add(LigneDeDemandeDExamen.nouvelle(identifiants.suivant(), code, demandee.libelle(), demandee.commentaire()));
        }
        var categorieLue = CategorieDExamen.lire(categorie);
        if (urgent == null) throw new SaisieInvalide("Le caractère urgent de la demande est requis");
        var aujourdhui = horloge.aujourdhui(centre);
        var demande = DemandeExamen.demander(identifiants.suivant(), patient, centre, prescripteur,
                dateDemande != null ? dateDemande : aujourdhui, aujourdhui, categorieLue, urgent, motif, lignes, horloge.maintenant());
        return uniteDeTravail.executer(() -> {
            if (!patients.existe(centre, patient)) throw new PatientIntrouvable();
            demandes.ajouter(demande);
            return demande;
        });
    }
}
