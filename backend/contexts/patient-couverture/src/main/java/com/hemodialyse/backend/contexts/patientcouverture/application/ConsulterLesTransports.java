package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CategoriesDeTransport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.CategoriesDeTransport.CategorieDeTransportPubliee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.Transporteurs.TransporteurPublie;

import java.util.List;
import java.util.UUID;

/**
 * Consulter les transporteurs et les catégories de transport du centre de la session (listes de choix des écrans). Tout
 * utilisateur authentifié lit son centre ; un autre centre est refusé par la session.
 */
public class ConsulterLesTransports {

    private final SessionCourante session;
    private final Transporteurs transporteurs;
    private final CategoriesDeTransport categories;

    public ConsulterLesTransports(SessionCourante session, Transporteurs transporteurs, CategoriesDeTransport categories) {
        this.session = session;
        this.transporteurs = transporteurs;
        this.categories = categories;
    }

    /** Le centre à lire : celui de la session (refus si un autre est demandé). */
    public UUID centreDeLaLecture(UUID centreDemande) {
        return session.centre(centreDemande).valeur();
    }

    public List<TransporteurPublie> transporteurs(UUID centre) {
        return transporteurs.lister(centre);
    }

    public List<CategorieDeTransportPubliee> categories(UUID centre) {
        return categories.lister(centre);
    }
}
