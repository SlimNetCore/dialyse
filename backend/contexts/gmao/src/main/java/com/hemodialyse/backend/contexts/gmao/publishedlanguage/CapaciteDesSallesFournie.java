package com.hemodialyse.backend.contexts.gmao.publishedlanguage;

import java.util.UUID;

/**
 * Port fourni à la GMAO (ADR 0192, sur le modèle de ADR 0108/0185/0190/0191) : la capacité des salles, que la GMAO consulte avant
 * d'affecter un générateur à une salle (RG-PLN-061). La GMAO ne peut pas dépendre de la Planification (la Planification dépend de la GMAO) :
 * c'est la Planification qui implémente ce port, la GMAO traduit le refus par son propre ACL. Sans salle, rien à contrôler ; une salle
 * sans capacité, inconnue ou d'un autre centre n'impose aucune limite. Lecture seule.
 */
public interface CapaciteDesSallesFournie {

    /**
     * @param equipement le générateur déjà affecté à la salle (ignoré dans le décompte), {@code null} à l'enregistrement
     * @throws CapaciteDeLaSalleAtteinte si un générateur de plus dépasserait la capacité de la salle
     */
    void verifierAffectation(UUID centre, UUID salle, UUID equipement);

    /** Refus : la salle est à sa capacité maximale ; le message est celui, déjà formulé, de la Planification (RG-PLN-061). */
    class CapaciteDeLaSalleAtteinte extends RuntimeException {
        public CapaciteDeLaSalleAtteinte(String message) {
            super(message);
        }
    }
}
