package com.hemodialyse.backend.infrastructure.persistence.repository;

import com.hemodialyse.backend.infrastructure.persistence.entity.AssureJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AssureJpaRepository extends JpaRepository<AssureJpaEntity, String> {
    @Query("""
        select a from AssureJpaEntity a
        where a.centerId = :centerId
          and (
            :q is null or :q = ''
            or lower(a.numeroAssurance) like lower(concat('%', :q, '%'))
            or lower(coalesce(a.nom, '')) like lower(concat('%', :q, '%'))
            or lower(coalesce(a.prenom, '')) like lower(concat('%', :q, '%'))
          )
        order by a.nom asc, a.prenom asc
        """)
    List<AssureJpaEntity> searchByCenter(@Param("centerId") java.util.UUID centerId, @Param("q") String q);
}

