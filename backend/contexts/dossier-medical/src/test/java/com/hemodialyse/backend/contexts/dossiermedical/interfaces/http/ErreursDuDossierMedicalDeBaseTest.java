package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLeDossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerLeDossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.application.GenerateurDIdentifiants;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.PatientsDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.DossiersMedicauxDeBaseEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DossierMedicalDeBase;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnDossierMedical;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les refus du dossier médical de base au format commun, par l'advice du module : patient inconnu 404 (KB-MED-98) ; écriture
 * concurrente 409 {@code DOSSIER_MEDICAL_ECRITURE_CONCURRENTE} ; colonne trop longue 400 {@code VALIDATION_ERROR}, corps ou date illisible
 * 400 {@code BAD_REQUEST}, 403 AVANT 400 pour un profil sans droit (KB-MED-99) ; dossier absent 404 sans corps.
 */
class ErreursDuDossierMedicalDeBaseTest {
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final String API = "/api/v1/patients/" + PATIENT + "/dossier-medical";
    private static final UniteDeTravail UNITE = new UniteDeTravail() {
        @Override
        public <T> T executer(Supplier<T> action) {
            return action.get();
        }

        @Override
        public <T> T independante(Supplier<T> action) {
            return action.get();
        }
    };

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc sur(DossiersMedicauxDeBaseEnMemoire dossiers) {
        SessionCourante session = demande -> new CentreId(CENTRE);
        PatientsDuCentre patients = (centre, patient) -> !patient.valeur().equals(INCONNU);
        GenerateurDIdentifiants identifiants = UUID::randomUUID;
        var horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC));
        var controleur = new DossierMedicalDeBaseControleur(new ConsulterLeDossierMedicalDeBase(session, dossiers),
                new EnregistrerLeDossierMedicalDeBase(session, patients, dossiers, UNITE, identifiants, horloge));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    @Test
    void un_patient_inconnu_est_refuse_en_404_a_l_ecriture() throws Exception {
        profil("ROLE_MEDECIN");
        sur(new DossiersMedicauxDeBaseEnMemoire()).perform(post("/api/v1/patients/" + INCONNU + "/dossier-medical")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nephropathieInitiale\":\"x\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409() throws Exception {
        profil("ROLE_MEDECIN");
        var dossiers = new DossiersMedicauxDeBaseEnMemoire() {
            @Override
            public Optional<DossierMedicalDeBase> duPatientEnVerrouillant(CentreId centre, PatientId patient) {
                throw new EcritureConcurrenteSurUnDossierMedical();
            }
        };
        sur(dossiers).perform(put(API).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOSSIER_MEDICAL_ECRITURE_CONCURRENTE"));
    }

    @Test
    void une_colonne_trop_longue_est_un_400_de_validation() throws Exception {
        profil("ROLE_MEDECIN");
        sur(new DossiersMedicauxDeBaseEnMemoire()).perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hepatiteBStatut\":\"" + "x".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void un_corps_ou_une_date_illisible_est_un_400_et_le_403_passe_avant() throws Exception {
        var mvc = sur(new DossiersMedicauxDeBaseEnMemoire());
        profil("ROLE_MEDECIN");
        for (String corps : new String[]{"{pas du json", "[]", "{\"dateMiseEnDialyse\":\"2026-02-30\"}", "{\"centerId\":\"abc\"}"}) {
            mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)).andExpect(status().isBadRequest());
        profil("ROLE_INFIRMIER");
        mvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void un_dossier_absent_est_un_404_sans_corps() throws Exception {
        profil("ROLE_MEDECIN");
        sur(new DossiersMedicauxDeBaseEnMemoire()).perform(get(API)).andExpect(status().isNotFound()).andExpect(content().string(""));
    }
}
