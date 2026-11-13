package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.ProfilDeLectureDesOrdonnances;

import java.util.UUID;

/**
 * La garde de l'impression d'une ordonnance par l'impression générique de Documents (KB-MED-18 corrigé, ADR 0081 et 0166) : le profil de
 * lecture des ordonnances (MEDECIN ou ADMIN), puis le contrôle du centre du corps de la demande. Refus ({@link AccesRefuse}, 403) avant tout
 * accès aux données.
 */
public final class GarderLImpressionDeLOrdonnance {

    private final ProfilDeLaSession profil;
    private final SessionCourante session;

    public GarderLImpressionDeLOrdonnance(ProfilDeLaSession profil, SessionCourante session) {
        this.profil = profil;
        this.session = session;
    }

    /** @param centreDemande le centre du corps de la demande, {@code null} = « celui de ma session » */
    public void executer(UUID centreDemande) {
        if (!ProfilDeLectureDesOrdonnances.autorise(profil.roles())) {
            throw new AccesRefuse("Profil insuffisant pour imprimer une ordonnance");
        }
        session.centre(centreDemande);
    }
}
