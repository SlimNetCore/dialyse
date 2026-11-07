package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.medical.allergie.port.AllergieRepositoryPort;
import com.hemodialyse.backend.domain.medical.allergie.service.AllergieDomainService;
import com.hemodialyse.backend.domain.medical.antecedent.port.AntecedentRepositoryPort;
import com.hemodialyse.backend.domain.medical.antecedent.service.AntecedentDomainService;
import com.hemodialyse.backend.domain.medical.greffe.port.BilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.port.DonneurVivantRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.port.EtapeBilanPreGreffeRepositoryPort;
import com.hemodialyse.backend.domain.medical.greffe.service.BilanPreGreffeDomainService;
import com.hemodialyse.backend.domain.medical.greffe.service.DonneurVivantDomainService;
import com.hemodialyse.backend.domain.medical.greffe.service.EtapeBilanPreGreffeDomainService;
import com.hemodialyse.backend.domain.medical.observation.port.ObservationBiologiqueRepositoryPort;
import com.hemodialyse.backend.domain.medical.observation.service.ObservationBiologiqueDomainService;
import com.hemodialyse.backend.domain.seance.port.AbordVasculaireRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.DossierMedicalPatientRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.ResultatAnalyseRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.SeanceRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletMedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.port.VoletParamedicalRepositoryPort;
import com.hemodialyse.backend.domain.seance.service.AbordVasculaireDomainService;
import com.hemodialyse.backend.domain.seance.service.DossierMedicalPatientDomainService;
import com.hemodialyse.backend.domain.seance.service.ResultatAnalyseDomainService;
import com.hemodialyse.backend.domain.seance.service.VoletMedicalDomainService;
import com.hemodialyse.backend.domain.seance.service.VoletParamedicalDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires pure domain services (no Spring/JPA dependency) as Spring beans.
 * <p>
 * Keeping the {@code @Service} / {@code @Transactional} annotations out of the
 * {@code domain/} package enforces the hexagonal boundary (AGENTS.md §3 &amp; §4):
 * high-level use cases stay framework-agnostic and are assembled here, in the
 * infrastructure layer. Transaction atomicity is provided by the persistence
 * adapters (Spring Data repositories are transactional by default).
 */
@Configuration
public class DomainServiceConfig {

    @Bean
    public com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService referentialAdminDomainService(
            com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminRepositoryPort repo) {
        return new com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService(repo);
    }

    @Bean
    public VoletMedicalDomainService voletMedicalDomainService(SeanceRepositoryPort seanceRepo,
                                                               VoletMedicalRepositoryPort voletRepo) {
        return new VoletMedicalDomainService(seanceRepo, voletRepo);
    }

    @Bean
    public VoletParamedicalDomainService voletParamedicalDomainService(SeanceRepositoryPort seanceRepo,
                                                                       VoletParamedicalRepositoryPort voletRepo) {
        return new VoletParamedicalDomainService(seanceRepo, voletRepo);
    }

    @Bean
    public AbordVasculaireDomainService abordVasculaireDomainService(AbordVasculaireRepositoryPort repo) {
        return new AbordVasculaireDomainService(repo);
    }

    @Bean
    public ResultatAnalyseDomainService resultatAnalyseDomainService(ResultatAnalyseRepositoryPort repo) {
        return new ResultatAnalyseDomainService(repo);
    }

    @Bean
    public DossierMedicalPatientDomainService dossierMedicalPatientDomainService(DossierMedicalPatientRepositoryPort repo) {
        return new DossierMedicalPatientDomainService(repo);
    }

    @Bean
    public AntecedentDomainService antecedentDomainService(AntecedentRepositoryPort repo) {
        return new AntecedentDomainService(repo);
    }

    @Bean
    public AllergieDomainService allergieDomainService(AllergieRepositoryPort repo) {
        return new AllergieDomainService(repo);
    }

    @Bean
    public ObservationBiologiqueDomainService observationBiologiqueDomainService(ObservationBiologiqueRepositoryPort repo) {
        return new ObservationBiologiqueDomainService(repo);
    }

    @Bean
    public BilanPreGreffeDomainService bilanPreGreffeDomainService(BilanPreGreffeRepositoryPort repo) {
        return new BilanPreGreffeDomainService(repo);
    }

    @Bean
    public EtapeBilanPreGreffeDomainService etapeBilanPreGreffeDomainService(EtapeBilanPreGreffeRepositoryPort repo) {
        return new EtapeBilanPreGreffeDomainService(repo);
    }

    @Bean
    public DonneurVivantDomainService donneurVivantDomainService(DonneurVivantRepositoryPort repo) {
        return new DonneurVivantDomainService(repo);
    }


}








