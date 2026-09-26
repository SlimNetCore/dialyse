package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.CenterJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CenterJpaRepository extends JpaRepository<CenterJpaEntity, UUID> {

    List<CenterJpaEntity> findBySocieteIdOrderByNameAsc(UUID societeId);

    List<CenterJpaEntity> findBySocieteIdIn(Collection<UUID> societeIds);

    /**
     * Vrai si un autre centre (id différent) porte déjà ce code, sans tenir compte de la casse.
     */
    @Query("select count(c) > 0 from CenterJpaEntity c where upper(c.code) = upper(:code) and c.id <> :excludedId")
    boolean existsOtherWithCode(@Param("code") String code, @Param("excludedId") UUID excludedId);
}
