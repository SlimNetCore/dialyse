package com.hemodialyse.backend.contexts.gmao.application.piecejointe;

import com.hemodialyse.backend.contexts.gmao.application.intervention.InterventionIntrouvable;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;

/**
 * Liste paginée des pièces d'une intervention du centre, métadonnées seules. Lecture permise sur tout statut, y compris une
 * intervention supprimée (KB-GMAO-64 ne touche que l'ajout et le retrait). Noms et types MIME sont re-contrôlés à la lecture
 * (KB-GMAO-61). Les bornes de page sont celles de Spring Data (KB-GMAO-67), aucun plafond sur {@code size}.
 */
public final class ListerPiecesJointes {

    private final InterventionsHotes hotes;
    private final LecturePiecesJointes lecture;

    public ListerPiecesJointes(InterventionsHotes hotes, LecturePiecesJointes lecture) {
        this.hotes = hotes;
        this.lecture = lecture;
    }

    public PageDePiecesJointes executer(InterventionId intervention, CentreId centre, int page, int size) {
        hotes.trouver(intervention).filter(h -> h.centre().equals(centre)).orElseThrow(InterventionIntrouvable::new);
        PageDePiecesJointes resultat = lecture.deLIntervention(intervention, page, size);
        return new PageDePiecesJointes(resultat.items().stream().map(PieceJointeVue::assainie).toList(), resultat.total(),
                resultat.page(), resultat.size());
    }
}
