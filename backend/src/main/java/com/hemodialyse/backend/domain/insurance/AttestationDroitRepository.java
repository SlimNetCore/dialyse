package com.hemodialyse.backend.domain.insurance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public interface AttestationDroitRepository extends JpaRepository<AttestationDroit, UUID> {

    @Query("""
        select case when count(a) > 0 then true else false end
        from AttestationDroit a
        where a.centerId = :centerId
          and a.patientId = :patientId
          and :atDate between a.dateDebut and a.dateFin
        """)
    boolean existsValidAt(UUID centerId, UUID patientId, LocalDate atDate);
}

