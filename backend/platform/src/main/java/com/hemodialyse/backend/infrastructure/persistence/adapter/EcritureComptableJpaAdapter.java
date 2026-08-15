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
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EcritureComptableJpaAdapter implements EcritureComptableRepositoryPort {

    private final EcritureComptableJpaRepository repo;

    public EcritureComptableJpaAdapter(EcritureComptableJpaRepository repo) {
        this.repo = repo;
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
    public Optional<EcritureComptable> findBySourceId(UUID sourceId, UUID centerId, JournalCode journalCode) {
        return repo.findByCenterIdAndJournalCodeAndSourceId(centerId, journalCode.name(), sourceId)
                .map(this::toDomain);
    }

    @Override
    public PagedResult<EcritureComptable> findByCenterAndPeriod(UUID centerId, LocalDate from, LocalDate to,
                                                                JournalCode journalCode, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("dateEcriture").descending().and(Sort.by("numeroPiece").descending()));
        Page<EcritureComptableJpaEntity> pageResult;
        if (journalCode != null) {
            pageResult = repo.findByCenterIdAndJournalCodeAndDateEcritureBetween(
                    centerId, journalCode.name(), from, to, pageable);
        } else {
            pageResult = repo.findByCenterIdAndDateEcritureBetween(centerId, from, to, pageable);
        }
        List<EcritureComptable> items = pageResult.getContent().stream().map(this::toDomain).toList();
        return PagedResult.of(items, pageResult.getTotalElements(), page, size);
    }

    @Override
    public List<EcritureComptable> findForExport(UUID centerId, LocalDate from, LocalDate to, JournalCode journalCode) {
        String code = journalCode != null ? journalCode.name() : null;
        if (code == null) return List.of();
        return repo.findByCenterIdAndJournalCodeAndDateEcritureBetween(centerId, code, from, to)
                .stream().map(this::toDomain).toList();
    }

    @Override
    public String nextNumeroPiece(UUID centerId, JournalCode journalCode, int year) {
        String prefix = journalCode.name() + "-" + year + "-";
        Optional<EcritureComptableJpaEntity> last = repo
                .findTopByCenterIdAndJournalCodeAndNumeroPieceLikeOrderByNumeroPieceDesc(
                        centerId, journalCode.name(), prefix + "%");
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

    // ─── Mapping ─────────────────────────────────────────────────────────────

    private EcritureComptableJpaEntity toEntity(EcritureComptable domain) {
        EcritureComptableJpaEntity entity = new EcritureComptableJpaEntity();
        entity.setId(domain.getId());
        entity.setCenterId(domain.getCenterId());
        entity.setJournalCode(domain.getJournalCode().name());
        entity.setDateEcriture(domain.getDateEcriture());
        entity.setDatePiece(domain.getDatePiece());
        entity.setNumeroPiece(domain.getNumeroPiece());
        entity.setLibelle(domain.getLibelle());
        entity.setStatut(domain.getStatut().name());
        entity.setSourceId(domain.getSourceId());

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
        entity.setLignes(lignes);
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
                JournalCode.valueOf(entity.getJournalCode()),
                entity.getDateEcriture(), entity.getDatePiece(),
                entity.getNumeroPiece(), entity.getLibelle(),
                lignes, StatutEcriture.valueOf(entity.getStatut()),
                entity.getSourceId()
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

