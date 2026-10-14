package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.LecturePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PageDePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.application.piecejointe.PieceJointeVue;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.TypeDePiece;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Adapter JPA du read model {@link LecturePiecesJointes} : projection sans la colonne des octets, tri par date d'ajout
 * décroissante puis identifiant décroissant. Les bornes de page sont refusées par {@link PageRequest} (messages de Spring Data,
 * observés par le legacy : KB-GMAO-67).
 */
public class LecturePiecesJointesJpa implements LecturePiecesJointes {

    private final PieceJointeSpringData donnees;

    public LecturePiecesJointesJpa(PieceJointeSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public PageDePiecesJointes deLIntervention(InterventionId intervention, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("ajouteLe"), Sort.Order.desc("id")));
        Page<PieceMetadonnees> resultat = donnees.metadonneesDeLIntervention(intervention.valeur(), pageable);
        return new PageDePiecesJointes(resultat.getContent().stream()
                .map(m -> new PieceJointeVue(m.id(), m.interventionId(), TypeDePiece.depuis(m.type()), m.nom(), m.contentType(),
                        m.taille(), m.ajouteLe())).toList(), resultat.getTotalElements(), page, size);
    }
}
