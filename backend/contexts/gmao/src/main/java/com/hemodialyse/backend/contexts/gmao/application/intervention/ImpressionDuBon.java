package com.hemodialyse.backend.contexts.gmao.application.intervention;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;

import java.time.ZoneId;

/**
 * Port sortant : imprimer le bon d'intervention avec le modèle de document du centre (le moteur d'impression, les formats,
 * l'identité de la société et du centre appartiennent à la plateforme). {@code zone} est le fuseau d'affichage des dates,
 * {@code editePar} l'identifiant de l'utilisateur qui édite. Un modèle désactivé est refusé par la plateforme (422).
 * Reproduit (KB-GMAO-70) : la première impression crée le modèle de document du centre.
 */
public interface ImpressionDuBon {

    DocumentImprime imprimer(CentreId centre, DonneesDuBon donnees, ZoneId zone, String editePar);
}
