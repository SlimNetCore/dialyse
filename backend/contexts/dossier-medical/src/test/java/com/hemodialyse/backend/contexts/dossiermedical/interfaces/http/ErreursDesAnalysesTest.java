package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAnalysesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.ModifierUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AnalysesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Analyses;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneAnalyse;
import com.hemodialyse.backend.contexts.dossiermedical.domain.MesuresDuBilan;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Periode;
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
 * Les refus des analyses au format commun, par l'advice du module : verrou non obtenu 409 {@code ANALYSE_ECRITURE_CONCURRENTE} ; patient
 * inconnu 404 ; analyse introuvable à la modification 404 {@code ANALYSE_INTROUVABLE} (plus de création ni d'écrasement, KB-MED-74) ; entrée
 * illisible 400 sans nom de classe interne et 403 AVANT 400 pour un profil sans droit (KB-MED-72) ; recherche hors borne 400
 * {@code VALIDATION_ERROR} ; suppression sans effet 204 et marquée « sans trace » (KB-MED-75).
 */
class ErreursDesAnalysesTest {
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID ANALYSE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String URL = "/api/v1/patients/" + PATIENT + "/analyses";
    private static final String VALIDE = "{\"datePrelevement\":\"2026-01-05\",\"hbGDl\":11.5,\"plaquettes\":220}";

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc controleurSur(Analyses analyses) {
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
        var controleur = new AnalysesControleur(new ConsulterLesAnalysesDuPatient(session, analyses),
                new EnregistrerUneAnalyse(session, (centre, patient) -> !patient.valeur().equals(INCONNU), analyses, unite, UUID::randomUUID, horloge),
                new ModifierUneAnalyse(session, analyses, unite, horloge), new SupprimerUneAnalyse(session, analyses, unite));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static Analyses verrouillee() {
        return new AnalysesEnMemoire() {
            @Override
            public Optional<Analyse> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUneAnalyse();
            }
        };
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_l_analyse() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(verrouillee());
        mvc.perform(put(URL + "/" + ANALYSE).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANALYSE_ECRITURE_CONCURRENTE"));
        mvc.perform(delete(URL + "/" + ANALYSE)).andExpect(status().isConflict());
    }

    @Test
    void un_patient_inconnu_repond_404_et_une_analyse_introuvable_404() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AnalysesEnMemoire());
        mvc.perform(post("/api/v1/patients/" + INCONNU + "/analyses").contentType(MediaType.APPLICATION_JSON).content(VALIDE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
        mvc.perform(put(URL + "/" + ANALYSE).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ANALYSE_INTROUVABLE"));
    }

    @Test
    void une_valeur_illisible_ou_hors_colonne_repond_400_sans_nom_de_classe_interne() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AnalysesEnMemoire());
        for (String corps : new String[]{"pas du json", VALIDE.replace("2026-01-05", "demain"), VALIDE.replace("220", "\"beaucoup\""),
                VALIDE.replace("220", "2147483648"), VALIDE.replace("11.5", "1" + "0".repeat(37))}) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.hemodialyse"))));
        }
    }

    @Test
    void un_corps_ou_un_parametre_illisible_repond_400_pour_un_medecin_et_403_pour_un_profil_sans_droit() throws Exception {
        var mvc = controleurSur(new AnalysesEnMemoire());
        profil("ROLE_MEDECIN");
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/patients/" + PATIENT + "/analyses/pas-un-uuid")).andExpect(status().isBadRequest());
        profil("ROLE_INFIRMIER");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/v1/patients/analyses/search").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isForbidden());
        profil("ROLE_ADMIN");
        mvc.perform(put(URL + "/" + ANALYSE).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/patients/analyses/search").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void une_recherche_hors_borne_repond_400_validation_error_et_sans_page_ni_taille_400() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AnalysesEnMemoire());
        String base = "{\"centerId\":\"" + CENTRE + "\",\"patientId\":\"" + PATIENT + "\",";
        mvc.perform(post("/api/v1/patients/analyses/search").contentType(MediaType.APPLICATION_JSON).content(base + "\"page\":0,\"size\":201}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Invalid request content."));
        mvc.perform(post("/api/v1/patients/analyses/search").contentType(MediaType.APPLICATION_JSON).content(base + "\"dateFrom\":\"2026-01-09\","
                        + "\"dateTo\":\"2026-01-01\",\"page\":0,\"size\":10}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/patients/analyses/search").contentType(MediaType.APPLICATION_JSON).content(base.substring(0, base.length() - 1) + "}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/patients/analyses/search").contentType(MediaType.APPLICATION_JSON).content(base + "\"page\":0,\"size\":10}"))
                .andExpect(status().isOk());
    }

    @Test
    void une_suppression_sans_effet_repond_204_et_demande_de_ne_rien_inscrire_au_journal_d_audit() throws Exception {
        profil("ROLE_MEDECIN");
        var sansEffet = controleurSur(new AnalysesEnMemoire()).perform(delete(URL + "/" + ANALYSE)).andExpect(status().isNoContent()).andReturn();
        assertThat(sansEffet.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isEqualTo(Boolean.TRUE);
        var analyses = new AnalysesEnMemoire();
        analyses.ajouter(Analyse.enregistrer(ANALYSE, new PatientId(PATIENT), new CentreId(CENTRE), LocalDate.of(2026, 1, 5),
                new MesuresDuBilan(BigDecimal.ONE, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                LocalDate.of(2026, 1, 10), Instant.parse("2026-01-05T10:00:00Z")));
        var effectif = controleurSur(analyses).perform(delete(URL + "/" + ANALYSE)).andExpect(status().isNoContent()).andReturn();
        assertThat(effectif.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isNull();
        assertThat(analyses.duPatient(new CentreId(CENTRE), new PatientId(PATIENT), Periode.ILLIMITEE, 0, 10).total()).isZero();
    }
}
