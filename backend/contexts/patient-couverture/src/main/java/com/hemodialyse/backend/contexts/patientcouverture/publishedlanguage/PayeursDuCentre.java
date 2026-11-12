package com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Langage publié {@code patientcouverture.v1} : l'annuaire des payeurs d'un centre (RF3b, ADR 0127), pour les contextes qui nomment ou
 * regroupent par caisse d'assurance, agence ou centre payeur (séance, pilotage de la direction, impressions, listes de choix).
 * Lecture seule, TOUJOURS bornée au centre indiqué, à chaque étage de la chaîne caisse d'assurance, agence, centre payeur : une agence
 * ou une caisse d'un autre centre n'est jamais suivie (ADR 0120). Aucune règle d'accès (le consommateur a déjà garanti le centre).
 * Remplace les jointures SQL dupliquées dans les lecteurs « annuaire ».
 */
public interface PayeursDuCentre {

    /** Les caisses d'assurance du centre, par code. */
    List<CaissePubliee> caisses(UUID centre);

    /** Les agences du centre, par code. */
    List<AgencePubliee> agences(UUID centre);

    /** Les centres payeurs du centre, par code. */
    List<CentrePayeurPublie> centresPayeurs(UUID centre);

    /** Le centre payeur de ce centre avec son agence et sa caisse ; vide s'il est inconnu, nul ou d'un autre centre. */
    Optional<ChaineDuPayeur> detail(UUID centre, UUID centrePayeur);

    /** La chaîne de CHAQUE centre payeur du centre, par code de centre payeur (une seule lecture pour un regroupement). */
    List<ChaineDuPayeur> chaines(UUID centre);

    /**
     * La caisse de chacune de ces agences, par identité d'agence, SANS borne de centre (ni l'agence ni la caisse) : exception unique à la
     * règle du centre, qui reproduit KB-FAC-65 / KB-RF3-07 (tableau de bord et chiffre d'affaires par caisse de la Facturation, instantané
     * figé de la facture) en attendant la décision de la direction. Agence inconnue ou sans caisse : absente de la réponse.
     */
    Map<UUID, CaissePubliee> caissesDesAgences(Collection<UUID> agences);

    /** Ce centre payeur existe-t-il dans ce centre ? */
    boolean existe(UUID centre, UUID centrePayeur);

    /** Une caisse d'assurance ; {@code type} : {@code STANDARD} ou {@code VACANCIER}. */
    record CaissePubliee(UUID id, String code, String nom, String type) {
    }

    /** Une agence et la caisse dont elle relève (par identité, telle qu'enregistrée). */
    record AgencePubliee(UUID id, String code, String nom, UUID caisse) {
    }

    /** Un centre payeur : {@code adresse} peut être absente ; {@code agence} est l'agence enregistrée, présente ou non dans le centre. */
    record CentrePayeurPublie(UUID id, String code, String nom, String adresse, UUID agence) {
    }

    /**
     * Un centre payeur et sa chaîne bornée au centre : {@code agence} est absente (nulle) si l'agence enregistrée n'est pas de ce centre ;
     * {@code caisse} est absente si l'agence l'est ou si la caisse enregistrée n'est pas de ce centre.
     */
    record ChaineDuPayeur(CentrePayeurPublie centrePayeur, AgencePubliee agence, CaissePubliee caisse) {
    }
}
