package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.Set;
import java.util.UUID;

/**
 * Interface REQUISE du langage publié de la Planification (DM9, ADR 0101), fournie par la plateforme depuis le langage publié du Dossier
 * médical ({@code RisquesInfectieuxPublies}) : les patients à risque infectieux d'un centre (dernière sérologie AgHBs, anti-VHC, ARN du
 * VHC ou VIH positive, RG-PLN-012). La Planification ne peut pas dépendre du module du Dossier médical (qui dépend déjà d'elle : cycle
 * Maven) ; elle en reçoit la lecture par ce port. Lecture seule, bornée au centre.
 */
public interface RisquesInfectieuxFournis {

    Set<UUID> patientsARisque(UUID centre);
}
