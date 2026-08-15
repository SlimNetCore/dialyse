package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.facturation.aggregate.TypeTVA;
import com.hemodialyse.backend.domain.facturation.port.TypeTvaRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class TypeTvaJdbcAdapter implements TypeTvaRepositoryPort {

    private final JdbcTemplate jdbc;

    public TypeTvaJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<TypeTVA> findActiveAt(UUID centerId, String typePrestation, LocalDate date) {
        List<TypeTVA> rows = jdbc.query(
                """
                        SELECT id, center_id, libelle, taux, type_prestation, exonere,
                               date_debut_validite, date_fin_validite, texte_reference, actif, created_at, created_by
                        FROM tva_types
                        WHERE center_id = ?
                          AND type_prestation = ?
                          AND actif = TRUE
                          AND date_debut_validite <= ?
                          AND (date_fin_validite IS NULL OR date_fin_validite >= ?)
                        ORDER BY date_debut_validite DESC
                        LIMIT 1
                        """,
                this::mapRow,
                centerId, typePrestation, date, date
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    @Override
    public PagedResult<TypeTVA> findAll(UUID centerId, int page, int size) {
        int offset = page * size;
        List<TypeTVA> items = jdbc.query(
                """
                        SELECT id, center_id, libelle, taux, type_prestation, exonere,
                               date_debut_validite, date_fin_validite, texte_reference, actif, created_at, created_by
                        FROM tva_types
                        WHERE center_id = ?
                        ORDER BY date_debut_validite DESC, libelle
                        LIMIT ? OFFSET ?
                        """,
                this::mapRow,
                centerId, size, offset
        );
        int total = countByCenterId(centerId);
        return new PagedResult<>(items, total, page, size);
    }

    @Override
    public TypeTVA save(TypeTVA typeTva) {
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT COUNT(*) > 0 FROM tva_types WHERE id = ?", Boolean.class, typeTva.id()
        ));

        if (exists) {
            jdbc.update(
                    """
                            UPDATE tva_types
                            SET libelle = ?, taux = ?, type_prestation = ?, exonere = ?,
                                date_debut_validite = ?, date_fin_validite = ?, texte_reference = ?,
                                actif = ?, created_by = ?
                            WHERE id = ? AND center_id = ?
                            """,
                    typeTva.libelle(), typeTva.taux(), typeTva.typePrestation(), typeTva.exonere(),
                    typeTva.dateDebutValidite(), typeTva.dateFinValidite() != null ? typeTva.dateFinValidite() : null,
                    typeTva.texteReference(), typeTva.actif(), typeTva.createdBy(),
                    typeTva.id(), typeTva.centerId()
            );
        } else {
            jdbc.update(
                    """
                            INSERT INTO tva_types (id, center_id, libelle, taux, type_prestation, exonere,
                                                  date_debut_validite, date_fin_validite, texte_reference,
                                                  actif, created_at, created_by)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP, ?)
                            """,
                    typeTva.id(), typeTva.centerId(), typeTva.libelle(), typeTva.taux(),
                    typeTva.typePrestation(), typeTva.exonere(), typeTva.dateDebutValidite(),
                    typeTva.dateFinValidite(), typeTva.texteReference(), typeTva.createdBy()
            );
        }

        return findById(typeTva.id(), typeTva.centerId()).orElseThrow();
    }

    @Override
    public void deactivate(UUID id, UUID centerId) {
        jdbc.update(
                "UPDATE tva_types SET actif = FALSE WHERE id = ? AND center_id = ?",
                id, centerId
        );
    }

    private Optional<TypeTVA> findById(UUID id, UUID centerId) {
        List<TypeTVA> rows = jdbc.query(
                """
                        SELECT id, center_id, libelle, taux, type_prestation, exonere,
                               date_debut_validite, date_fin_validite, texte_reference, actif, created_at, created_by
                        FROM tva_types WHERE id = ? AND center_id = ?
                        """,
                this::mapRow, id, centerId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    private int countByCenterId(UUID centerId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tva_types WHERE center_id = ?", Integer.class, centerId
        );
        return count == null ? 0 : count;
    }

    private TypeTVA mapRow(ResultSet rs, int rowNum) throws SQLException {
        Date finDate = rs.getDate("date_fin_validite");
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new TypeTVA(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("center_id")),
                rs.getString("libelle"),
                rs.getBigDecimal("taux"),
                rs.getString("type_prestation"),
                rs.getBoolean("exonere"),
                rs.getDate("date_debut_validite").toLocalDate(),
                finDate != null ? finDate.toLocalDate() : null,
                rs.getString("texte_reference"),
                rs.getBoolean("actif"),
                createdAt != null ? createdAt.toInstant().atOffset(ZoneOffset.UTC) : null,
                rs.getString("created_by")
        );
    }
}

