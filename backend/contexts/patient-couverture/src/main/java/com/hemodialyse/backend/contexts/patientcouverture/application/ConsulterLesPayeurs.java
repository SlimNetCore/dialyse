package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.List;
import java.util.UUID;

/**
 * Consulter les listes de choix des payeurs du centre de la session (caisses, agences, centres payeurs, RF3c). Tout utilisateur
 * authentifié lit son centre ; un autre centre est refusé par la session (403).
 */
public class ConsulterLesPayeurs {

    private final SessionCourante session;
    private final ChoixDesPayeurs choix;

    public ConsulterLesPayeurs(SessionCourante session, ChoixDesPayeurs choix) {
        this.session = session;
        this.choix = choix;
    }

    /** Le centre à lire : celui de la session (refus si un autre est demandé). */
    public UUID centreDeLaLecture(UUID centreDemande) {
        return session.centre(centreDemande).valeur();
    }

    public List<ChoixDesPayeurs.ChoixDePayeur> caisses(UUID centre) {
        return choix.choixDeCaisses(centre);
    }

    public List<ChoixDesPayeurs.ChoixDePayeur> agences(UUID centre) {
        return choix.choixDAgences(centre);
    }

    public List<ChoixDesPayeurs.ChoixDePayeur> centresPayeurs(UUID centre) {
        return choix.choixDeCentresPayeurs(centre);
    }

    public List<ChoixDesPayeurs.DetailDeCentrePayeur> details(UUID centre) {
        return choix.detailsDesCentresPayeurs(centre);
    }
}
