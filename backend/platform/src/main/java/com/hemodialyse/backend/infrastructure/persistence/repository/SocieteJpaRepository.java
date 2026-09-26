package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.SocieteJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SocieteJpaRepository extends JpaRepository<SocieteJpaEntity, UUID> {

    @Query("select s.id from SocieteJpaEntity s where s.logo is not null and s.id in :ids")
    java.util.List<UUID> findIdsWithLogo(@Param("ids") java.util.Collection<UUID> ids);

    @Query("select count(s) > 0 from SocieteJpaEntity s where upper(s.code) = upper(:code) and s.id <> :excludedId")
    boolean existsOtherWithCode(@Param("code") String code, @Param("excludedId") UUID excludedId);

    @Query("select s from SocieteJpaEntity s where :search = '' "
            + "or lower(s.code) like lower(concat('%', :search, '%')) "
            + "or lower(s.raisonSociale) like lower(concat('%', :search, '%'))")
    Page<SocieteJpaEntity> search(@Param("search") String search, Pageable pageable);
}
