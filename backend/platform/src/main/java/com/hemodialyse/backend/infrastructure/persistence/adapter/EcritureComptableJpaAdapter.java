package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.entity.LigneEcriture;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.AxeAnalytique;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.EcritureComptableJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.LigneEcritureJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.EcritureComptableJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.sql.PreparedStatement;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EcritureComptableJpaAdapter implements EcritureComptableRepositoryPort {

    private final EcritureComptableJpaRepository repo;
    private final JdbcTemplate jdbc;

    public EcritureComptableJpaAdapter(EcritureComptableJpaRepository repo, JdbcTemplate jdbc) {
        this.repo = repo;
        this.jdbc = jdbc;
    }

    @Override
    public void save(EcritureComptable ecriture) {
        repo.save(toEntity(ecriture));
    }

    @Override
    public void saveAll(List<EcritureComptable> ecritures) {
        repo.saveAll(ecritures.stream().map(this::toEntity).toList());
    }

    @Override
    public Optional<EcritureComptable> findById(UUID id, UUID centerId) {
        return repo.findById(id).filter(e -> centerId.equals(e.getCenterId())).map(this::toDomain);
    }

    @Override
    public Optional<EcritureComptable> findBySourceId(UUID sourceId, UUID centerId) {
        if (sourceId != null) {
            verrouillerSequence(centerId, "SEQ_SOURCE_" + sourceId, "SOURCE-");
        }
        return repo.findFirstByCenterIdAndSourceIdOrderByCreatedAtAsc(centerId, sourceId).map(this::toDomain);
    }

    @Override
    public boolean existsByJournal(UUID centerId, JournalCode journalCode) {
        return repo.existsByCenterIdAndJournalCode(centerId, journalCode.valeur());
    }

    @Override
    public boolean existsByCompte(UUID centerId, String compte) {
        return repo.existsLigneByCompte(centerId, compte);
    }

    @Override
    public PagedResult<EcritureComptable> findByCenterAndPeriod(UUID centerId, LocalDate from, LocalDate to,
                                                                JournalCode journalCode, StatutEcriture statut,
                                                                int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateEcriture").descending().and(Sort.by("numeroPiece").descending()));
        Page<EcritureComptableJpaEntity> pageResult;
        String statutStr = statut != null ? statut.name() : null;

        if (journalCode != null && statutStr != null) {
            pageResult = repo.findByCenterIdAndJournalCodeAndStatutAndDateEcritureBetween(
                    centerId, journalCode.valeur(), statutStr, from, to, pageable);
        } else if (journalCode != null) {
            pageResult = repo.findByCenterIdAndJournalCodeAndDateEcritureBetween(
                    centerId, journalCode.valeur(), from, to, pageable);
        } else if (statutStr != null) {
            pageResult = repo.findByCenterIdAndStatutAndDateEcritureBetween(
                    centerId, statutStr, from, to, pageable);
        } else {
            pageResult = repo.findByCenterIdAndDateEcritureBetween(centerId, from, to, pageable);
        }
        List<EcritureComptable> items = pageResult.getContent().stream().map(this::toDomain).toList();
        return PagedResult.of(items, pageResult.getTotalElements(), page, size);
    }

    @Override
    public List<EcritureComptable> findForExport(UUID centerId, LocalDate from, LocalDate to, JournalCode journalCode) {
        String code = journalCode != null ? journalCode.valeur() : null;
        if (code == null) return List.of();
        return repo.findByCenterIdAndJournalCodeAndDateEcritureBetween(centerId, code, from, to)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public String nextNumeroPiece(UUID centerId, JournalCode journalCode, int year) {
        String prefix = journalCode.valeur() + "-" + year + "-";
        verrouillerSequence(centerId, "SEQ_ECRITURE_" + journalCode.valeur() + "_" + year, prefix);
        Optional<EcritureComptableJpaEntity> last = repo
                .findTopByCenterIdAndJournalCodeAndNumeroPieceLikeOrderByNumeroPieceDesc(
                        centerId, journalCode.valeur(), prefix + "%");
        int next = 1;
        if (last.isPresent()) {
            String lastNum = last.get().getNumeroPiece();
            try {
                next = Integer.parseInt(lastNum.substring(prefix.length())) + 1;
            } catch (NumberFormatException ignored) {
            }
        }
        return prefix + String.format("%06d", next);
    }

    private void verrouillerSequence(UUID centerId, String cle, String prefixe) {
        Boolean postgres = jdbc.execute((ConnectionCallback<Boolean>) connection -> "PostgreSQL"
                .equalsIgnoreCase(connection.getMetaData().getDatabaseProductName()));
        if (Boolean.TRUE.equals(postgres)) {
            String cleGlobale = "comptabilite:" + centerId + ":" + cle;
            jdbc.execute((ConnectionCallback<Void>) connection -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                    statement.setString(1, cleGlobale);
                    statement.execute();
                }
                return null;
            });
        } else {
            try {
                jdbc.update("INSERT INTO app_settings (center_id, cle, prefixe, dernier_compteur) "
                                + "SELECT ?, ?, ?, 0 WHERE NOT EXISTS (SELECT 1 FROM app_settings WHERE center_id = ? AND cle = ?)",
                        centerId, cle, prefixe, centerId, cle);
            } catch (DuplicateKeyException concurrente) {
                // Une transaction concurrente a matérialisé le verrou en premier.
            }
        }
        jdbc.queryForObject("SELECT dernier_compteur FROM app_settings WHERE center_id = ? AND cle = ? FOR UPDATE",
                Long.class, centerId, cle);
    }

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private EcritureComptableJpaEntity toEntity(EcritureComptable domain) {
        EcritureComptableJpaEntity entity = repo.findById(domain.getId())
                .orElseGet(EcritureComptableJpaEntity::new);
        entity.setId(domain.getId());
        entity.setCenterId(domain.getCenterId());
        entity.setJournalCode(domain.getJournalCode().valeur());
        entity.setDateEcriture(domain.getDateEcriture());
        entity.setDatePiece(domain.getDatePiece());
        entity.setNumeroPiece(domain.getNumeroPiece());
        entity.setLibelle(domain.getLibelle());
        entity.setStatut(domain.getStatut().name());
        entity.setSourceId(domain.getSourceId());
        entity.setModeleId(domain.getModeleId());

        List<LigneEcritureJpaEntity> lignes = domain.getLignes().stream().map(l -> {
            LigneEcritureJpaEntity le = new LigneEcritureJpaEntity();
            le.setId(l.getId());
            le.setEcriture(entity);
            le.setCompteSCF(l.getCompteSCF());
            le.setLibelleLigne(l.getLibelleLigne());
            le.setMontantDebit(l.getMontantDebit());
            le.setMontantCredit(l.getMontantCredit());
            le.setTiersId(l.getTiersId());
            le.setAxesAnalytiques(serializeAxes(l.getAxes()));
            return le;
        }).collect(Collectors.toList());

        entity.getLignes().clear();
        entity.getLignes().addAll(lignes);
        return entity;
    }

    private EcritureComptable toDomain(EcritureComptableJpaEntity entity) {
        List<LigneEcriture> lignes = entity.getLignes().stream().map(le ->
                new LigneEcriture(
                        le.getId(), le.getCompteSCF(), le.getLibelleLigne(),
                        le.getMontantDebit(), le.getMontantCredit(),
                        le.getTiersId(), deserializeAxes(le.getAxesAnalytiques())
                )
        ).toList();
        return new EcritureComptable(
                entity.getId(), entity.getCenterId(),
                JournalCode.de(entity.getJournalCode()),
                entity.getDateEcriture(), entity.getDatePiece(),
                entity.getNumeroPiece(), entity.getLibelle(),
                lignes, StatutEcriture.valueOf(entity.getStatut()),
                entity.getSourceId(), entity.getModeleId()
        );
    }

    private String serializeAxes(List<AxeAnalytique> axes) {
        if (axes == null || axes.isEmpty()) return null;
        return axes.stream().map(a -> a.code() + ":" + a.libelle()).collect(Collectors.joining(","));
    }

    private List<AxeAnalytique> deserializeAxes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(","))
                .map(s -> {
                    int idx = s.indexOf(':');
                    if (idx < 0) return new AxeAnalytique(s, s);
                    return new AxeAnalytique(s.substring(0, idx), s.substring(idx + 1));
                }).toList();
    }
}
