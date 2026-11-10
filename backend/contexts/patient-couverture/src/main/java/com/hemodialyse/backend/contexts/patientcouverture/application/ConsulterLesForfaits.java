package com.hemodialyse.backend.contexts.patientcouverture.application;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.ForfaitsDuCentre.ForfaitPublie;

import java.util.List;
import java.util.UUID;

/**
 * Consulter les forfaits du catalogue du centre de la session (liste de choix des écrans). Tout utilisateur authentifié lit son
 * centre ; un autre centre est refusé par la session.
 */
public class ConsulterLesForfaits {

    private final SessionCourante session;
    private final ForfaitsDuCentre forfaits;

    public ConsulterLesForfaits(SessionCourante session, ForfaitsDuCentre forfaits) {
        this.session = session;
        this.forfaits = forfaits;
    }

    /** Le centre à lire : celui de la session (refus si un autre est demandé). */
    public UUID centreDeLaLecture(UUID centreDemande) {
        return session.centre(centreDemande).valeur();
    }

    public List<ForfaitPublie> forfaits(UUID centre) {
        return forfaits.lister(centre);
    }
}
