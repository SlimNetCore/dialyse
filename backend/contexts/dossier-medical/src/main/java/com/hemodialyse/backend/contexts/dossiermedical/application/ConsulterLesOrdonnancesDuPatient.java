package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDOrdonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Liste paginée des ordonnances d'un patient du centre de la session, de la plus récente à la plus ancienne. Bornes REPRODUITES
 * du legacy : page négative = 0, taille nulle ou négative = 20, plafond 200. Patient inconnu : page vide (200). Droits :
 * MEDECIN et ADMIN.
 */
public class ConsulterLesOrdonnancesDuPatient {

    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 200;

    private final SessionCourante session;
    private final Ordonnances ordonnances;

    public ConsulterLesOrdonnancesDuPatient(SessionCourante session, Ordonnances ordonnances) {
        this.session = session;
        this.ordonnances = ordonnances;
    }

    public PageDOrdonnances executer(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        int taillePosee = taille <= 0 ? TAILLE_PAR_DEFAUT : Math.min(taille, TAILLE_MAXIMALE);
        return ordonnances.duPatient(centre, new PatientId(patient), Math.max(page, 0), taillePosee);
    }
}
