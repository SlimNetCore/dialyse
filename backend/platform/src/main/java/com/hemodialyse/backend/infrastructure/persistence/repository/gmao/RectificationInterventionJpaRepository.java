package com.hemodialyse.backend.infrastructure.persistence.repository.gmao;

import com.hemodialyse.backend.infrastructure.persistence.entity.gmao.RectificationInterventionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RectificationInterventionJpaRepository extends JpaRepository<RectificationInterventionEntity, UUID> {

    List<RectificationInterventionEntity> findByInterventionIdOrderByLeAsc(UUID interventionId);

    void deleteByInterventionId(UUID interventionId);
}
