package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.IntervenantEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface IntervenantJpaRepository extends JpaRepository<IntervenantEntity, UUID> {

    @Query(value = "SELECT i FROM IntervenantEntity i WHERE i.centreId = :centreId",
            countQuery = "SELECT COUNT(i) FROM IntervenantEntity i WHERE i.centreId = :centreId")
    Page<IntervenantEntity> findPageByCentreId(@Param("centreId") UUID centreId, Pageable pageable);
}
