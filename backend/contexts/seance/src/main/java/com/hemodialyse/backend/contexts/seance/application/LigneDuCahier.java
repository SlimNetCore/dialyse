package com.hemodialyse.backend.contexts.seance.application;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Une ligne du jeu de données du cahier de dialyse imprimé (DOC5a, ADR 0193) : une séance et l'un de ses consommables (une seule ligne, sans
 * consommable, pour une séance qui n'en a pas). Les textes sont DÉJÀ mis en forme pour l'impression (tiret pour une valeur absente,
 * virgule décimale, libellés des statuts et des fréquences) ; les montants restent des décimaux, la mise en forme des nombres monétaires est
 * celle du gabarit. {@code null} signifie « absent » : le gabarit imprime alors rien (forfait sans prix, séance sans consommable).
 */
public record LigneDuCahier(
        String seance, LocalDate jour, String statut, String forfaitCode, String forfaitNom, BigDecimal forfaitPrix,
        String poidsAvant, String poidsApres, String tensionAvant, String tensionApres, String duree, String debitSang,
        String ultrafiltration, String dialysat, String anticoagulant, String incidents,
        String articleCode, String articleLibelle, String articleUnite, BigDecimal quantite, BigDecimal valeurUnitaire,
        BigDecimal totalValorise,
        String nephropathie, LocalDate miseEnDialyse, String hepatiteB, String hepatiteC, String observationGlobale,
        String conclusionDuDossier, String poidsSec, String epo, String fer,
        String prescription, String tolerance, String examen, String resultats, String ajustements, String conclusionMedicale) {
}
