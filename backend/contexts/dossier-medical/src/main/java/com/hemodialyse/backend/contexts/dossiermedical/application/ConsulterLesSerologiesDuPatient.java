package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeSerologies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutSerologique;

import java.util.List;
import java.util.UUID;

/**
 * Consulte les sérologies d'un patient du centre de la session : la liste paginée (de la plus récente à la plus ancienne) et la
 * synthèse (dernier résultat par marqueur). Bornes REPRODUITES du legacy : page négative = 0, taille nulle ou négative = 20, plafond
 * 200. Patient inconnu ou d'un autre centre : page ou liste vide (200, reproduit, KB-MED-50). Les lectures ne sont pas tracées dans le
 * journal d'audit (KB-MED-54, à confirmer avec le DPO). Droits : MEDECIN et ADMIN (l'infirmier n'a pas accès : KB-MED-55).
 */
public class ConsulterLesSerologiesDuPatient {

    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 200;

    private final SessionCourante session;
    private final Serologies serologies;

    public ConsulterLesSerologiesDuPatient(SessionCourante session, Serologies serologies) {
        this.session = session;
        this.serologies = serologies;
    }

    public PageDeSerologies liste(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        int taillePosee = taille <= 0 ? TAILLE_PAR_DEFAUT : Math.min(taille, TAILLE_MAXIMALE);
        return serologies.duPatient(centre, new PatientId(patient), Math.max(page, 0), taillePosee);
    }

    public List<Serologie> synthese(UUID centreDemande, UUID patient) {
        var centre = session.centre(centreDemande);
        return StatutSerologique.derniersResultats(serologies.toutesDuPatient(centre, new PatientId(patient)));
    }
}
