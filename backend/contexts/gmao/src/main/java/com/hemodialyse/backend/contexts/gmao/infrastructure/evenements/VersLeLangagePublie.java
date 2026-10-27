package com.hemodialyse.backend.contexts.gmao.infrastructure.evenements;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementMisHorsService;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementReforme;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.TypeEquipement;
import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateurDevenuIndisponible;

import java.util.Optional;

/**
 * Traduit un événement de domaine en événement du langage publié {@code gmao.v1}. Seuls les générateurs de dialyse sont
 * publiés ; les autres équipements ne concernent aucun consommateur.
 */
final class VersLeLangagePublie {

    private VersLeLangagePublie() {
    }

    static Optional<GenerateurDevenuIndisponible> traduire(Object evenement) {
        if (evenement instanceof EquipementMisHorsService e && e.type() == TypeEquipement.GENERATEUR_DIALYSE) {
            return Optional.of(new GenerateurDevenuIndisponible(e.equipementId().valeur(), e.centreId().valeur(),
                    e.code().valeur(), e.designation(), e.statutPrecedent().name(), e.statut().name(), e.survenuLe()));
        }
        if (evenement instanceof EquipementReforme e && e.type() == TypeEquipement.GENERATEUR_DIALYSE) {
            return Optional.of(new GenerateurDevenuIndisponible(e.equipementId().valeur(), e.centreId().valeur(),
                    e.code().valeur(), e.designation(), e.statutPrecedent().name(), StatutEquipement.REFORME.name(),
                    e.survenuLe()));
        }
        return Optional.empty();
    }
}
