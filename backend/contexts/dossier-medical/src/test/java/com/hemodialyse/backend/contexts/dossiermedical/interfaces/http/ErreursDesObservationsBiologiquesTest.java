package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesObservationsBiologiquesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.ObservationsBiologiquesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneObservation;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationBiologique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ObservationsBiologiques;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.ValeurMesuree;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les refus des observations biologiques au format commun, par l'advice du module : verrou non obtenu 409
 * {@code OBSERVATION_ECRITURE_CONCURRENTE} ; patient inconnu 404 ; observation introuvable 400 (reproduit, KB-MED-76) ; entrée illisible ou
 * inconnue 400 sans nom de classe interne, et 403 AVANT 400 pour un profil sans droit (KB-MED-72) ; règle de gestion 422 avec son code ;
 * suppression sans effet 204 et marquée « sans trace » (KB-MED-75).
 */
class ErreursDesObservationsBiologiquesTest {
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID OBSERVATION = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String URL = "/api/v1/patients/" + PATIENT + "/observations";
    private static final String VALIDE = "{\"codeSystem\":\"LOINC\",\"code\":\"718-7\",\"valeurNum\":12.5,\"unite\":\"g/dL\","
            + "\"datePrelevement\":\"2026-01-05\"}";

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc controleurSur(ObservationsBiologiques observations) {
        UniteDeTravail unite = new UniteDeTravail() {
            @Override
            public <T> T executer(Supplier<T> action) {
                return action.get();
            }

            @Override
            public <T> T independante(Supplier<T> action) {
                return action.get();
            }
        };
        SessionCourante session = demande -> new CentreId(CENTRE);
        var horloge = HorlogeDuCentre.enUtc(Clock.fixed(Instant.parse("2026-01-10T10:00:00Z"), ZoneOffset.UTC));
        var controleur = new ObservationsBiologiquesControleur(
                new ConsulterLesObservationsBiologiquesDuPatient(session, observations, (centre, patient, demande) -> false),
                new EnregistrerUneObservationBiologique(session, (centre, patient) -> !patient.valeur().equals(INCONNU), observations, unite,
                        UUID::randomUUID, horloge),
                new CorrigerUneObservationBiologique(session, observations, unite, horloge),
                new SupprimerUneObservationBiologique(session, observations, unite));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static ObservationsBiologiques verrouillee() {
        return new ObservationsBiologiquesEnMemoire() {
            @Override
            public Optional<ObservationBiologique> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUneObservation();
            }
        };
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_l_observation() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(verrouillee());
        mvc.perform(put(URL + "/" + OBSERVATION).contentType(MediaType.APPLICATION_JSON).content("{\"valeurTexte\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("OBSERVATION_ECRITURE_CONCURRENTE"));
        mvc.perform(delete(URL + "/" + OBSERVATION)).andExpect(status().isConflict());
    }

    @Test
    void un_patient_inconnu_repond_404_et_une_observation_introuvable_400() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new ObservationsBiologiquesEnMemoire());
        mvc.perform(post("/api/v1/patients/" + INCONNU + "/observations").contentType(MediaType.APPLICATION_JSON).content(VALIDE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
        mvc.perform(put(URL + "/" + OBSERVATION).contentType(MediaType.APPLICATION_JSON).content("{\"valeurTexte\":\"x\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Observation introuvable"));
    }

    @Test
    void un_systeme_un_statut_ou_une_date_inconnu_ou_illisible_repond_400_sans_nom_de_classe_interne() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new ObservationsBiologiquesEnMemoire());
        for (String corps : List.of("{}", VALIDE.replace("LOINC", "SNOMED"), VALIDE.replace("LOINC", "loinc"), VALIDE.replace("\"codeSystem\":\"LOINC\",", ""),
                VALIDE.replace("}", ",\"statut\":\"ANNULE\"}"), VALIDE.replace("2026-01-05", "demain"), VALIDE.replace("12.5", "\"douze\""))) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.hemodialyse"))))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No enum constant"))));
        }
    }

    @Test
    void un_corps_ou_un_parametre_illisible_repond_400_pour_un_medecin_et_403_pour_un_profil_sans_droit() throws Exception {
        var mvc = controleurSur(new ObservationsBiologiquesEnMemoire());
        profil("ROLE_MEDECIN");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("from", "demain")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patients/pas-un-uuid/observations")).andExpect(status().isBadRequest());
        profil("ROLE_INFIRMIER");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isForbidden());
        profil("ROLE_ADMIN");
        mvc.perform(put(URL + "/" + OBSERVATION).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void les_regles_de_gestion_repondent_422_avec_leur_code_et_les_bornes_de_colonne_400() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new ObservationsBiologiquesEnMemoire());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE.replace("2026-01-05", "2026-01-11")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("OBSERVATION_DATE_FUTURE"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE.replace("LOINC", "ATC").replace("718-7", "J01CA04")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("OBSERVATION_ANALYTE_NON_LOINC"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE.replace("\"unite\":\"g/dL\",", "")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALEUR_MESUREE_UNITE_REQUISE"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE.replace("g/dL", "u".repeat(21))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isOk());
    }

    @Test
    void une_suppression_sans_effet_repond_204_et_demande_de_ne_rien_inscrire_au_journal_d_audit() throws Exception {
        profil("ROLE_MEDECIN");
        var sansEffet = controleurSur(new ObservationsBiologiquesEnMemoire()).perform(delete(URL + "/" + OBSERVATION))
                .andExpect(status().isNoContent()).andReturn();
        assertThat(sansEffet.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isEqualTo(Boolean.TRUE);
        var observations = new ObservationsBiologiquesEnMemoire();
        observations.ajouter(ObservationBiologique.enregistrer(OBSERVATION, new PatientId(PATIENT), new CentreId(CENTRE), null,
                CodeClinique.saisir(SystemeDeCodage.LOINC, "718-7", null), new ValeurMesuree(BigDecimal.ONE, "u"), null, LocalDate.of(2026, 1, 5), null,
                LocalDate.of(2026, 1, 10), Instant.parse("2026-01-05T10:00:00Z")));
        var effectif = controleurSur(observations).perform(delete(URL + "/" + OBSERVATION)).andExpect(status().isNoContent()).andReturn();
        assertThat(effectif.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isNull();
        assertThat(observations.duPatient(new CentreId(CENTRE), new PatientId(PATIENT), null, null, null, 0, 10).total()).isZero();
    }
}
