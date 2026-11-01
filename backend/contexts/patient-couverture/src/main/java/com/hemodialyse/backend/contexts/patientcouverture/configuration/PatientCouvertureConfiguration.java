package com.hemodialyse.backend.contexts.patientcouverture.configuration;

import com.hemodialyse.backend.contexts.patientcouverture.application.AssureService;
import com.hemodialyse.backend.contexts.patientcouverture.application.AssureUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationService;
import com.hemodialyse.backend.contexts.patientcouverture.application.AttestationUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.application.PecService;
import com.hemodialyse.backend.contexts.patientcouverture.application.PecUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientService;
import com.hemodialyse.backend.contexts.patientcouverture.application.PatientUseCases;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssurePatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AssureRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.AttestationRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.MouvementPatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PatientRepository;
import com.hemodialyse.backend.contexts.patientcouverture.domain.PecRepository;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssurePatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AssuresJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.AttestationsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.MouvementsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PatientsJdbc;
import com.hemodialyse.backend.contexts.patientcouverture.infrastructure.persistence.PecsJdbc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

@Configuration
public class PatientCouvertureConfiguration {
    @Bean
    public PatientRepository patientRepository(JdbcTemplate jdbc) {
        return new PatientsJdbc(jdbc);
    }

    @Bean
    public MouvementPatientRepository mouvementPatientRepository() {
        return new MouvementsJdbc();
    }

    @Bean
    public PatientUseCases patientUseCases(PatientRepository p, MouvementPatientRepository m) {
        return new PatientService(p, m, UUID::randomUUID);
    }

    @Bean
    public AssureRepository assureRepository(JdbcTemplate jdbc) {
        return new AssuresJdbc(jdbc);
    }

    @Bean
    public AssurePatientRepository assurePatientRepository(JdbcTemplate jdbc) {
        return new AssurePatientsJdbc(jdbc);
    }

    @Bean
    public AssureUseCases assureUseCases(AssureRepository a, AssurePatientRepository ap, PatientRepository p) {
        return new AssureService(a, ap, p, UUID::randomUUID);
    }

    @Bean
    public PecRepository pecRepository(JdbcTemplate jdbc) {
        return new PecsJdbc(jdbc);
    }

    @Bean
    public PecUseCases pecUseCases(PecRepository p, PatientRepository patients) {
        return new PecService(p, patients, UUID::randomUUID);
    }

    @Bean
    public AttestationRepository attestationRepository(JdbcTemplate jdbc) {
        return new AttestationsJdbc(jdbc);
    }

    @Bean
    public AttestationUseCases attestationUseCases(AttestationRepository a, PatientRepository patients) {
        return new AttestationService(a, patients, UUID::randomUUID);
    }
}