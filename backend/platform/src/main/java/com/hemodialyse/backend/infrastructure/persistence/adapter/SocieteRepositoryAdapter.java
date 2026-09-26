package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.organisation.model.Centre;
import com.hemodialyse.backend.domain.organisation.model.Coordonnees;
import com.hemodialyse.backend.domain.organisation.model.Societe;
import com.hemodialyse.backend.domain.organisation.port.SocieteRepositoryPort;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.infrastructure.persistence.entity.CenterJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.entity.SocieteJpaEntity;
import com.hemodialyse.backend.infrastructure.persistence.repository.CenterJpaRepository;
import com.hemodialyse.backend.infrastructure.persistence.repository.SocieteJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Adapter Out — persiste l'agrégat {@link Societe} : une ligne {@code societes} et, pour chaque centre de
 * l'agrégat, la ligne {@code centers} correspondante (rattachée par {@code centers.societe_id}).
 * <p>
 * Un centre retiré de l'agrégat (transfert) n'est pas modifié ici : c'est la société de destination qui le
 * rattache à elle en enregistrant son propre agrégat.
 */
@Component
public class SocieteRepositoryAdapter implements SocieteRepositoryPort {

    private final SocieteJpaRepository societes;
    private final CenterJpaRepository centers;

    public SocieteRepositoryAdapter(SocieteJpaRepository societes, CenterJpaRepository centers) {
        this.societes = societes;
        this.centers = centers;
    }

    private static Societe toDomain(SocieteJpaEntity e, List<CenterJpaEntity> rows) {
        List<Centre> centres = rows.stream().map(SocieteRepositoryAdapter::toDomain).toList();
        Societe societe = new Societe(e.getId(), e.getCode(), e.getRaisonSociale(), e.getNif(), e.getNis(), e.getRc(),
                new Coordonnees(e.getAdresse(), e.getVille(), e.getWilaya(), e.getTelephone(), e.getEmail(), e.getSiteWeb()),
                e.isActif(), e.getCreatedAt(), centres);
        societe.definirPiedDePage(e.getPiedPage());
        return societe;
    }

    private static Centre toDomain(CenterJpaEntity row) {
        return new Centre(row.getId(), row.getCode(), row.getName(),
                new Coordonnees(row.getAdresse(), row.getVille(), row.getWilaya(), row.getTelephone(), row.getEmail(), row.getSiteWeb()),
                row.isActif());
    }

    @Override
    public Optional<Societe> findById(UUID id) {
        return societes.findById(id).map(entity -> toDomain(entity, centers.findBySocieteIdOrderByNameAsc(id)));
    }

    @Override
    public Societe save(Societe societe) {
        SocieteJpaEntity entity = societes.findById(societe.id()).orElseGet(SocieteJpaEntity::new);
        entity.setId(societe.id());
        entity.setCode(societe.code());
        entity.setRaisonSociale(societe.raisonSociale());
        entity.setNif(societe.nif());
        entity.setNis(societe.nis());
        entity.setRc(societe.rc());
        Coordonnees c = societe.coordonnees();
        entity.setAdresse(c.adresse());
        entity.setVille(c.ville());
        entity.setWilaya(c.wilaya());
        entity.setTelephone(c.telephone());
        entity.setEmail(c.email());
        entity.setSiteWeb(c.siteWeb());
        entity.setPiedPage(societe.piedDePage());
        entity.setActif(societe.actif());
        entity.setCreatedAt(societe.createdAt());
        societes.save(entity);

        for (Centre centre : societe.centres()) {
            CenterJpaEntity row = centers.findById(centre.id()).orElseGet(CenterJpaEntity::new);
            row.setId(centre.id());
            row.setSocieteId(societe.id());
            row.setCode(centre.code());
            row.setName(centre.nom());
            Coordonnees cc = centre.coordonnees();
            row.setAdresse(cc.adresse());
            row.setVille(cc.ville());
            row.setWilaya(cc.wilaya());
            row.setTelephone(cc.telephone());
            row.setEmail(cc.email());
            row.setSiteWeb(cc.siteWeb());
            row.setActif(centre.actif());
            centers.save(row);
        }
        return societe;
    }

    @Override
    public boolean existsSocieteCode(String code, UUID excludedSocieteId) {
        return societes.existsOtherWithCode(code, excludedSocieteId != null ? excludedSocieteId : new UUID(0, 0));
    }

    @Override
    public boolean existsCentreCode(String code, UUID excludedCentreId) {
        return centers.existsOtherWithCode(code, excludedCentreId != null ? excludedCentreId : new UUID(0, 0));
    }

    @Override
    public PagedResult<Societe> findPaged(String search, int page, int size) {
        String term = search == null ? "" : search.trim();
        Page<SocieteJpaEntity> result = societes.search(term, PageRequest.of(page, size, Sort.by("raisonSociale")));
        Map<UUID, List<CenterJpaEntity>> centresBySociete = centers
                .findBySocieteIdIn(result.getContent().stream().map(SocieteJpaEntity::getId).toList())
                .stream().collect(Collectors.groupingBy(CenterJpaEntity::getSocieteId));
        List<Societe> items = result.getContent().stream()
                .map(e -> toDomain(e, centresBySociete.getOrDefault(e.getId(), List.of())))
                .toList();
        return PagedResult.of(items, result.getTotalElements(), page, size);
    }
}
