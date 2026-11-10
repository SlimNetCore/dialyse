package com.hemodialyse.backend.contexts.patientcouverture.application;

import java.util.List;
import java.util.UUID;

/**
 * Port sortant de lecture : les listes de choix des écrans (caisses, agences, centres payeurs, centres payeurs détaillés) d'un centre,
 * triées par nom (RF3c, ADR 0128). Servies jusque-là par l'Organisation ({@code /referentials/*}). Toutes bornées au centre indiqué ;
 * l'accès (centre de la session) est garanti par l'appelant.
 */
public interface ChoixDesPayeurs {

    List<ChoixDePayeur> choixDeCaisses(UUID centre);

    List<ChoixDePayeur> choixDAgences(UUID centre);

    List<ChoixDePayeur> choixDeCentresPayeurs(UUID centre);

    /** Chaque centre payeur avec le code et le nom de son agence et de sa caisse (absents si l'agence ou la caisse n'existe plus). */
    List<DetailDeCentrePayeur> detailsDesCentresPayeurs(UUID centre);

    /** Une ligne de choix ; {@code adresse} pour un centre payeur, {@code type} (STANDARD ou VACANCIER) pour une caisse. */
    record ChoixDePayeur(UUID id, String code, String nom, String adresse, String type) {
    }

    record DetailDeCentrePayeur(UUID id, String codeCentrePayeur, String libelleCentrePayeur, String adresseCentrePayeur,
                                String codeAgence, String libelleAgence, String codeCaisse, String libelleCaisse) {
    }
}
