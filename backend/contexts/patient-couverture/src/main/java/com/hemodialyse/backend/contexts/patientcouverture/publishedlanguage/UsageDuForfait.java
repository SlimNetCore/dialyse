package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : ce qui retient un forfait du catalogue (RF2). Chaque propriétaire d'une référence
 * à un forfait publie le nombre de ses références (Patient et couverture : les prises en charge ; Facturation : les lignes de
 * facture ; Séance : les séances auxquelles le forfait est imposé) ; le catalogue les additionne pour refuser la suppression d'un
 * forfait utilisé. Le centre du forfait est transmis : seules les prises en charge de ce centre comptent (KB-RF2-02 corrigé,
 * ADR 0125) ; les lignes de facture et les séances gardent leur compte sans centre (KB-SEA-91).
 */
@FunctionalInterface
public interface UsageDuForfait {

    /** Nombre de références à ce forfait (du centre donné) chez le propriétaire ; 0 si sa table n'existe pas (schéma allégé). */
    long nombreDeReferences(UUID centre, UUID forfait);
}
