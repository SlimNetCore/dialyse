package com.hemodialyse.backend.contexts.gmao.contrat;

import com.hemodialyse.backend.contexts.gmao.application.piecejointe.LecturePiecesJointes;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointe;
import com.hemodialyse.backend.contexts.gmao.domain.piecejointe.PieceJointeRepository;
import com.hemodialyse.backend.contexts.gmao.doubles.Fichiers;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.LecturePiecesJointesJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceJointeRepositoryJpa;
import com.hemodialyse.backend.contexts.gmao.infrastructure.persistence.PieceJointeSpringData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrat des pièces jointes exécuté contre l'adapter JPA sur H2 (mode PostgreSQL). Chaque appel de port s'exécute dans sa
 * propre transaction. Constate en plus que la liste ne lit jamais la colonne des octets (le SQL envoyé est inspecté). La
 * variante PostgreSQL 16 réelle est {@code PieceJointePostgresContractIT}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:gmao-contrat-piece-jointe;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.session_factory.statement_inspector=com.hemodialyse.backend.contexts.gmao.contrat.InspecteurDeRequetes"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PieceJointeJpaContratTest extends PieceJointeContrat {

    @Autowired
    private PieceJointeSpringData donnees;

    private PieceJointeRepositoryJpa repository;
    private LecturePiecesJointesJpa lecture;

    @BeforeEach
    void creerLesAdaptersEtViderLaTable() {
        repository = new PieceJointeRepositoryJpa(donnees);
        lecture = new LecturePiecesJointesJpa(donnees);
        donnees.deleteAll();
    }

    @Override
    protected PieceJointeRepository repository() {
        return repository;
    }

    @Override
    protected LecturePiecesJointes lecture() {
        return lecture;
    }

    @Test
    void should_never_read_the_bytes_column_when_listing() {
        PieceJointe piece = piece(INTERVENTION_1, "a.pdf", Fichiers.pdf((int) PieceJointe.TAILLE_MAX_OCTETS), T0);
        repository.enregistrer(piece);
        InspecteurDeRequetes.REQUETES.clear();

        assertThat(lecture.deLIntervention(INTERVENTION_1, 0, 20).items()).hasSize(1);

        assertThat(InspecteurDeRequetes.REQUETES).isNotEmpty().noneMatch(sql -> sql.toLowerCase().contains("contenu"));
    }

    @Test
    void should_not_read_the_bytes_when_counting_or_withdrawing() {
        PieceJointe piece = piece(INTERVENTION_1, "a.pdf", Fichiers.pdf(), T0);
        repository.enregistrer(piece);
        InspecteurDeRequetes.REQUETES.clear();

        repository.compter(INTERVENTION_1);
        repository.trouverPourRetrait(piece.id());
        repository.supprimer(piece.id());

        assertThat(InspecteurDeRequetes.REQUETES).isNotEmpty().noneMatch(sql -> sql.toLowerCase().contains("contenu"));
    }
}
