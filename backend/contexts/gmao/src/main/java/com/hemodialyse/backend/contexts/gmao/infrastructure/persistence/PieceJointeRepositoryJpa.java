package com.hemodialyse.backend.contexts.gmao.infrastructure.persistence;

import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeId;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Adapter JPA du repository des pièces jointes. L'écriture est vidée tout de suite pour que la base refuse (valeur trop
 * longue) pendant le cas d'usage, avant la publication des événements. La suppression est physique, et le retrait ne lit pas les octets ({@link #trouverPourRetrait}).
 */
public class PieceJointeRepositoryJpa implements PieceJointeRepository {

    private final PieceJointeSpringData donnees;

    public PieceJointeRepositoryJpa(PieceJointeSpringData donnees) {
        this.donnees = donnees;
    }

    @Override
    public void enregistrer(PieceJointe piece) {
        UUID id = piece.id().valeur();
        PieceJointeJpa entite = donnees.findById(id).orElseGet(() -> new PieceJointeJpa(id));
        PieceJointeMapper.recopier(piece, entite);
        donnees.saveAndFlush(entite);
    }

    @Override
    public Optional<PieceJointe> trouverParId(PieceJointeId id) {
        return donnees.findById(id.valeur()).map(PieceJointeMapper::versDomaine);
    }

    @Override
    public Optional<PieceJointe> trouverPourRetrait(PieceJointeId id) {
        return donnees.enTeteDe(id.valeur()).map(PieceJointeMapper::versDomaineSansOctets);
    }

    @Override
    public long compter(InterventionId intervention) {
        return donnees.countByInterventionId(intervention.valeur());
    }

    @Override
    public void supprimer(PieceJointeId id) {
        donnees.supprimerParId(id.valeur());
    }
}
