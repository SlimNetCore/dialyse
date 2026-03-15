package com.hemodialyse.backend.domain.referential;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

public class ReferentialRepositories {

    @Repository
    public interface CentrePayeurRepository extends JpaRepository<CentrePayeur, UUID> {
        List<CentrePayeur> findByCenterId(UUID centerId);
    }

    @Repository
    public interface AgenceRepository extends JpaRepository<Agence, UUID> {
        List<Agence> findByCenterId(UUID centerId);
    }

    @Repository
    public interface CaisseAssuranceRepository extends JpaRepository<CaisseAssurance, UUID> {
        List<CaisseAssurance> findByCenterId(UUID centerId);
    }

    @Repository
    public interface MedecinRepository extends JpaRepository<Medecin, UUID> {
        List<Medecin> findByCenterId(UUID centerId);
    }

    @Repository
    public interface SalleRepository extends JpaRepository<Salle, UUID> {
        List<Salle> findByCenterId(UUID centerId);
    }

    @Repository
    public interface PositionCreneauRepository extends JpaRepository<PositionCreneau, UUID> {
        List<PositionCreneau> findByCenterId(UUID centerId);
    }

    @Repository
    public interface TransporteurRepository extends JpaRepository<Transporteur, UUID> {
        List<Transporteur> findByCenterId(UUID centerId);
    }

    @Repository
    public interface CategorieTransportRepository extends JpaRepository<CategorieTransport, UUID> {
        List<CategorieTransport> findByCenterId(UUID centerId);
    }
}

