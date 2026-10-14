package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.SalleId;

/**
 * Port sortant : la capacité des salles (Organisation / Planification, aujourd'hui le legacy). Refuse l'affectation d'un
 * générateur à une salle qui n'a plus de place (422 {@code SALLE_CAPACITE_DEPASSEE}). Sans salle, rien à contrôler ; une
 * salle sans capacité, inconnue ou d'un autre centre n'impose aucune limite. {@code equipement} est le générateur déjà
 * affecté (ignoré dans le décompte), nul à l'enregistrement.
 */
public interface CapaciteDesSalles {

    void verifierAffectation(CentreId centre, SalleId salle, EquipementId equipement);
}
