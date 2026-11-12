package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/** Port sortant : la session de l'utilisateur (Identité et accès, ACL). Seul le centre importe à Documents. */
public interface SessionCourante {

    /**
     * Le centre à utiliser pour la requête : celui de la session, ou le centre demandé pour un accès transverse (SUPERADMIN).
     *
     * @param centreDemande {@code null} = « celui de ma session »
     * @throws AccesRefuse si le centre demandé n'est pas celui de la session, ou la session est invalide
     */
    CentreId exigerLeCentre(UUID centreDemande);
}
