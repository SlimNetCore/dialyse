package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port sortant : l'ordonnance telle que l'état imprimé la lit (DOC5d, ADR 0196), sans passer par l'agrégat. L'impression historique lisait
 * les lignes brutes, sans contrôle du statut ni du contenu : une ordonnance au statut illisible ou à la ligne incohérente s'imprime
 * quand même, ce que la reconstitution de l'agrégat refuserait ({@code Ordonnances}, KB-MED-102).
 */
public interface OrdonnancesAImprimer {

    /** L'ordonnance de CE centre ; inconnue ou d'un autre centre : vide. Les lignes sont dans l'ordre où la base les rend (non spécifié). */
    Optional<OrdonnanceImprimable> trouver(CentreId centre, UUID ordonnance);

    /** L'en-tête et les lignes d'une ordonnance : valeurs telles qu'enregistrées (nulles possibles). */
    record OrdonnanceImprimable(UUID patient, String numero, LocalDate datePrescription, String medecin, List<LigneImprimable> lignes) {
        public OrdonnanceImprimable {
            lignes = List.copyOf(lignes);
        }
    }

    /** Une ligne : le libellé du code affiché prime sur le nom saisi (l'un et l'autre peuvent manquer). */
    record LigneImprimable(String libelleDuCode, String libelle, String posologie, String voie, Integer dureeJours, Integer quantite,
                           String instructions) {
    }
}
