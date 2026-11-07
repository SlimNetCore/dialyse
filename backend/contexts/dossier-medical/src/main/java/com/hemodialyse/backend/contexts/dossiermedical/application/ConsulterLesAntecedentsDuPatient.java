package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeFiches;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;

import java.util.UUID;

/**
 * Consulte les antécédents d'un patient du centre de la session, du début le plus récent au plus ancien. Bornes REPRODUITES du legacy
 * (KB-MED-71) : page négative = 0, taille nulle ou négative = 20, plafond 200. Patient inconnu ou d'un autre centre : page vide (200,
 * reproduit comme DM3 et DM4). Lectures non tracées dans le journal d'audit (KB-MED-68, à confirmer avec le DPO). Droits : MEDECIN et
 * ADMIN (l'infirmier n'a pas accès : KB-MED-69).
 */
public class ConsulterLesAntecedentsDuPatient {
    static final int TAILLE_PAR_DEFAUT = 20;
    static final int TAILLE_MAXIMALE = 200;

    private final SessionCourante session;
    private final Antecedents antecedents;

    public ConsulterLesAntecedentsDuPatient(SessionCourante session, Antecedents antecedents) {
        this.session = session;
        this.antecedents = antecedents;
    }

    public PageDeFiches<Antecedent> liste(UUID centreDemande, UUID patient, int page, int taille) {
        var centre = session.centre(centreDemande);
        int taillePosee = taille <= 0 ? TAILLE_PAR_DEFAUT : Math.min(taille, TAILLE_MAXIMALE);
        return antecedents.duPatient(centre, new PatientId(patient), Math.max(page, 0), taillePosee);
    }
}
