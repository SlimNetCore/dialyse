package com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage;

import java.util.Set;
import java.util.UUID;

/**
 * Langage publié du Dossier médical (DM9, ADR 0101) : les patients à risque infectieux d'un centre, pour l'isolement de la Planification
 * (RG-PLN-012) : un patient est à risque si, pour au moins un marqueur AgHBs, anti-VHC, ARN du VHC ou VIH, sa DERNIÈRE sérologie du centre
 * est {@code POSITIF} (départage : date de prélèvement puis date de saisie). Reproduit SANS correction, à trancher par les médecins au plus
 * tard le 2026-12-15 : un résultat DOUTEUX ou EN_COURS plus récent lève le risque (KB-MED-106, RISQUE CLINIQUE) ; à égalité totale le
 * positif l'emporte (KB-MED-107) ; le risque est lu par le centre de la SÉROLOGIE, pas de la fiche (KB-MED-108). Lecture seule.
 */
public interface RisquesInfectieuxPublies {

    /** Identifiants des patients à risque du centre (vide pour un centre sans sérologie). */
    Set<UUID> patientsARisque(UUID centre);
}
