package com.hemodialyse.backend.contexts.seance.publishedlanguage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Langage publié (fourni à la Séance par le Dossier médical, LEC-PLN-GMAO c, ADR 0180) : les poids secs cibles prescrits à un patient,
 * données du Dossier médical que la Séance ne peut pas lire directement (le Dossier médical dépend de la Séance). La Séance en retient,
 * pour chaque séance, la première prescription datée au plus tard du jour de la séance.
 */
public interface PoidsSecDesPrescriptions {

    /**
     * Poids secs cibles prescrits au patient du centre (seules les prescriptions qui en portent un), du plus récent au plus ancien : date
     * de prescription décroissante, mise à jour décroissante (inconnue en dernier), identifiant décroissant. Vide si aucun.
     */
    List<PoidsSecPrescrit> poidsSecsCiblesPrescrits(UUID centre, UUID patient);

    /** Un poids sec cible (kg) prescrit à la date donnée. */
    record PoidsSecPrescrit(LocalDate datePrescription, BigDecimal poidsSecCibleKg) {
    }
}
