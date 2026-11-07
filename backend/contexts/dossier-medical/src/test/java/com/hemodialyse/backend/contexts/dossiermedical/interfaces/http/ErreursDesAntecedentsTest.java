package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAntecedentsDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.ResoudreUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AntecedentsEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Antecedents;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnAntecedent;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDAntecedent;
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
 * Les refus des antécédents au format commun, par l'advice du module : verrou non obtenu (le port lève
 * {@link EcritureConcurrenteSurUnAntecedent}, ce que fait l'adapter JDBC sur le SQLSTATE 55P03) 409 {@code ANTECEDENT_ECRITURE_CONCURRENTE} ;
 * patient inconnu 404 ; antécédent introuvable 400 (reproduit) ; entrée illisible ou inconnue 400 sans nom de classe interne, et 403 AVANT
 * 400 pour un profil sans droit (KB-MED-58 et 59) ; règle de gestion 422 avec son code ; suppression sans effet 204 et marquée « sans trace ».
 */
class ErreursDesAntecedentsTest {
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID ANTECEDENT = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String URL = "/api/v1/patients/" + PATIENT + "/antecedents";
    private static final String VALIDE = "{\"type\":\"MEDICAL\",\"libelleLibre\":\"Libre\",\"dateDebut\":\"2026-01-05\"}";

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc controleurSur(Antecedents antecedents) {
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
        var controleur = new AntecedentsControleur(new ConsulterLesAntecedentsDuPatient(session, antecedents),
                new EnregistrerUnAntecedent(session, (centre, patient) -> !patient.valeur().equals(INCONNU), antecedents, unite, UUID::randomUUID,
                        horloge),
                new CorrigerUnAntecedent(session, antecedents, unite, horloge), new ResoudreUnAntecedent(session, antecedents, unite, horloge),
                new SupprimerUnAntecedent(session, antecedents, unite));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static Antecedents verrouille() {
        return new AntecedentsEnMemoire() {
            @Override
            public Optional<Antecedent> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUnAntecedent();
            }

            @Override
            public boolean unDiagnosticActifExisteEnVerrouillant(CentreId centre, PatientId patient, CodeClinique diagnostic, UUID sauf) {
                throw new EcritureConcurrenteSurUnAntecedent();
            }
        };
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_l_antecedent() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(verrouille());
        mvc.perform(put(URL + "/" + ANTECEDENT).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ANTECEDENT_ECRITURE_CONCURRENTE"));
        mvc.perform(put(URL + "/" + ANTECEDENT + "/resoudre").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isConflict());
        mvc.perform(delete(URL + "/" + ANTECEDENT)).andExpect(status().isConflict());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MEDICAL\",\"codeSystem\":\"CIM10\",\"code\":\"N18\",\"dateDebut\":\"2026-01-05\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ANTECEDENT_ECRITURE_CONCURRENTE"));
    }

    @Test
    void un_patient_inconnu_repond_404_et_un_antecedent_introuvable_400() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AntecedentsEnMemoire());
        mvc.perform(post("/api/v1/patients/" + INCONNU + "/antecedents").contentType(MediaType.APPLICATION_JSON).content(VALIDE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
        mvc.perform(put(URL + "/" + ANTECEDENT).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.detail").value("Antécédent introuvable"));
    }

    @Test
    void un_type_un_systeme_ou_un_statut_inconnu_repond_400_sans_nom_de_classe_interne() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AntecedentsEnMemoire());
        for (String corps : List.of("{\"type\":\"AUTRE\",\"libelleLibre\":\"x\"}", "{\"libelleLibre\":\"x\"}", "{}",
                "{\"type\":\"MEDICAL\",\"code\":\"B20\"}", "{\"type\":\"MEDICAL\",\"codeSystem\":\"cim10\",\"code\":\"B20\"}")) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.hemodialyse"))));
        }
        mvc.perform(put(URL + "/" + ANTECEDENT).contentType(MediaType.APPLICATION_JSON).content("{\"statutClinique\":\"GUERI\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No enum constant"))));
    }

    @Test
    void un_corps_ou_un_identifiant_illisible_repond_400_pour_un_medecin_et_403_pour_un_profil_sans_droit() throws Exception {
        var mvc = controleurSur(new AntecedentsEnMemoire());
        profil("ROLE_MEDECIN");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"MEDICAL\",\"dateDebut\":\"demain\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patients/pas-un-uuid/antecedents")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isBadRequest());
        profil("ROLE_INFIRMIER");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isForbidden());
        profil("ROLE_ADMIN");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void les_regles_de_gestion_repondent_422_avec_leur_code() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AntecedentsEnMemoire());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"MEDICAL\",\"dateDebut\":\"2026-01-05\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ANTECEDENT_DIAGNOSTIC_REQUIS"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"MEDICAL\",\"libelleLibre\":\"x\",\"dateDebut\":\"2026-01-11\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ANTECEDENT_DATE_FUTURE"));
        String codee = "{\"type\":\"MEDICAL\",\"codeSystem\":\"CIM10\",\"code\":\"N18\",\"dateDebut\":\"2026-01-05\"}";
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(codee)).andExpect(status().isOk());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(codee)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void une_valeur_hors_borne_de_colonne_repond_400_au_lieu_de_500() throws Exception {
        profil("ROLE_MEDECIN");
        controleurSur(new AntecedentsEnMemoire()).perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"MEDICAL\",\"libelleLibre\":\"x\",\"dateDebut\":\"2026-01-05\",\"severite\":\"" + "s".repeat(51) + "\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void une_suppression_sans_effet_repond_204_et_demande_de_ne_rien_inscrire_au_journal_d_audit() throws Exception {
        profil("ROLE_MEDECIN");
        var sansEffet = controleurSur(new AntecedentsEnMemoire()).perform(delete(URL + "/" + ANTECEDENT)).andExpect(status().isNoContent())
                .andReturn();
        assertThat(sansEffet.getRequest().getAttribute(AntecedentsControleur.ATTRIBUT_SANS_TRACE)).isEqualTo(Boolean.TRUE);
        var antecedents = new AntecedentsEnMemoire();
        antecedents.ajouter(Antecedent.enregistrer(ANTECEDENT, new PatientId(PATIENT), new CentreId(CENTRE), TypeDAntecedent.MEDICAL, null, "Libre",
                LocalDate.of(2026, 1, 5), null, null, null, LocalDate.of(2026, 1, 10), Instant.parse("2026-01-05T10:00:00Z")));
        var effectif = controleurSur(antecedents).perform(delete(URL + "/" + ANTECEDENT)).andExpect(status().isNoContent()).andReturn();
        assertThat(effectif.getRequest().getAttribute(AntecedentsControleur.ATTRIBUT_SANS_TRACE)).isNull();
        assertThat(antecedents.duPatient(new CentreId(CENTRE), new PatientId(PATIENT), 0, 10).total()).isZero();
    }
}
