package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.List;
import java.util.UUID;

/**
 * Consulte les allergies d'un patient du centre de la session : la liste paginée (de la constatation la plus récente à la plus ancienne)
 * et les allergies critiques du bandeau (toute criticité HAUTE, y compris une allergie réfutée : reproduit, KB-MED-66). Bornes
 * REPRODUITES du legacy (KB-MED-71) : page négative = 0, taille nulle ou négative = 20, plafond 200. Patient inconnu ou d'un autre
 * centre : liste vide (200, reproduit). Lectures non tracées dans le journal d'audit (KB-MED-68, à confirmer avec le DPO). Droits :
 * MEDECIN et ADMIN, y compris pour les critiques ; l'infirmier n'a accès à rien (KB-MED-69, à confirmer avec les métiers).
 */
public class ConsulterLesAllergiesDuPatient {
    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 200;

    private final SessionCourante session;
    private final Allergies allergies;

    public ConsulterLesAllergiesDuPatient(SessionCourante session, Allergies allergies) {
        this.session = session;
        this.allergies = allergies;
    }

    public PageDeFiches<Allergie> liste(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        int taillePosee = taille <= 0 ? TAILLE_PAR_DEFAUT : Math.min(taille, TAILLE_MAXIMALE);
        return allergies.duPatient(centre, new PatientId(patient), Math.max(page, 0), taillePosee);
    }

    public List<Allergie> critiques(UUID centreDemande, UUID patient) {
        var centre = session.centre(centreDemande);
        return allergies.critiquesDuPatient(centre, new PatientId(patient));
    }
}
