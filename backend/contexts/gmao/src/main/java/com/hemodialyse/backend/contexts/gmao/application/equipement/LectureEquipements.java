package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.util.List;
import java.util.Optional;

/**
 * Port sortant de lecture (read model) du parc. Contrat observé du legacy : {@code parId} ne filtre pas le centre (le use
 * case le fait) et rend aussi un équipement supprimé logiquement (KB-GMAO-34) ; la liste d'un centre exclut les supprimés,
 * est triée par date de création décroissante et filtrée sur le code de statut exact quand il est donné (un statut vide
 * ou nul : tous). {@code page} et {@code size} sont déjà validés.
 */
public interface LectureEquipements {

    Optional<EquipementVue> parId(EquipementId id);

    /** Tous les équipements d'un centre, supprimés logiquement exclus, dans l'ordre de lecture de la base (non garanti, KB-GMAO-78). */
    List<EquipementVue> parcDuCentre(CentreId centre);

    PageDEquipements duCentre(CentreId centre, String statut, int page, int size);
}
