package com.hemodialyse.backend.contexts.planification.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import com.hemodialyse.backend.contexts.planification.domain.RessourcesDuCentre;

import java.time.LocalDate;

/**
 * Port sortant : les ressources d'un centre lues chez leurs propriétaires (salles et créneaux d'Organisation, générateurs en
 * service de GMAO par {@code GenerateursDuCentre}, jours d'ouverture du paramétrage, fermetures datées des 90 jours suivant le jour
 * donné). Seuls les générateurs de dialyse {@code EN_SERVICE} rattachés à une salle sont fournis (RG-PLN-011).
 */
public interface LectureDesRessources {

    RessourcesDuCentre duCentre(CentreId centre, LocalDate aPartirDu);
}
