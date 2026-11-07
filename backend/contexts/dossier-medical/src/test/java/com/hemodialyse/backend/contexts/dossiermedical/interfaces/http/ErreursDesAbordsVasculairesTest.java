package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAbordsVasculairesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.ModifierUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUnAbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AbordsVasculairesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordVasculaire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.AbordsVasculaires;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUnAbordVasculaire;
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
 * Les refus des abords vasculaires au format commun, par l'advice du module : verrou non obtenu 409
 * {@code ABORD_VASCULAIRE_ECRITURE_CONCURRENTE} ; patient inconnu 404 ; PUT d'un abord inconnu, d'un autre patient ou d'un autre centre 404
 * {@code ABORD_VASCULAIRE_INTROUVABLE} (KB-MED-62) ; entrée illisible ou type absent 400, et 403 AVANT 400 pour un profil sans droit
 * (KB-MED-58) ; l'infirmier peut lire mais pas écrire (KB-MED-69) ; règle de gestion 422 ; suppression sans effet 204 et marquée « sans trace ».
 */
class ErreursDesAbordsVasculairesTest {
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID ABORD = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String URL = "/api/v1/patients/" + PATIENT + "/abords-vasculaires";
    private static final String VALIDE = "{\"typeAbord\":\"FAV\",\"dateCreation\":\"2026-01-05\"}";

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc controleurSur(AbordsVasculaires abords) {
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
        var controleur = new AbordsVasculairesControleur(new ConsulterLesAbordsVasculairesDuPatient(session, abords),
                new EnregistrerUnAbordVasculaire(session, (centre, patient) -> !patient.valeur().equals(INCONNU), abords, unite, UUID::randomUUID,
                        horloge),
                new ModifierUnAbordVasculaire(session, abords, unite, horloge), new SupprimerUnAbordVasculaire(session, abords, unite));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static AbordsVasculaires verrouille() {
        return new AbordsVasculairesEnMemoire() {
            @Override
            public Optional<AbordVasculaire> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUnAbordVasculaire();
            }
        };
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_l_abord() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(verrouille());
        mvc.perform(put(URL + "/" + ABORD).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ABORD_VASCULAIRE_ECRITURE_CONCURRENTE"));
        mvc.perform(delete(URL + "/" + ABORD)).andExpect(status().isConflict());
    }

    @Test
    void un_patient_inconnu_repond_404_et_un_put_d_abord_introuvable_repond_404() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AbordsVasculairesEnMemoire());
        mvc.perform(post("/api/v1/patients/" + INCONNU + "/abords-vasculaires").contentType(MediaType.APPLICATION_JSON).content(VALIDE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
        mvc.perform(put(URL + "/" + ABORD).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ABORD_VASCULAIRE_INTROUVABLE"));
    }

    @Test
    void un_type_absent_ou_hors_borne_repond_400_et_un_type_libre_est_accepte() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AbordsVasculairesEnMemoire());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"typeAbord\":\"" + "u".repeat(21) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"typeAbord\":\"N'IMPORTE QUOI\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(PATIENT.toString())).andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void un_corps_ou_un_identifiant_illisible_repond_400_pour_un_medecin_403_pour_un_profil_sans_droit_et_l_infirmier_lit() throws Exception {
        var mvc = controleurSur(new AbordsVasculairesEnMemoire());
        profil("ROLE_MEDECIN");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"typeAbord\":\"FAV\",\"actif\":\"peut-etre\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patients/pas-un-uuid/abords-vasculaires")).andExpect(status().isBadRequest());
        profil("ROLE_INFIRMIER");
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isBadRequest());
        mvc.perform(get(URL)).andExpect(status().isOk());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        profil("ROLE_SECRETAIRE");
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isForbidden());
    }

    @Test
    void la_date_de_fin_anterieure_repond_422_avec_son_code() throws Exception {
        profil("ROLE_MEDECIN");
        controleurSur(new AbordsVasculairesEnMemoire()).perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"typeAbord\":\"FAV\",\"dateCreation\":\"2026-01-05\",\"dateFin\":\"2026-01-04\"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ABORD_DATE_FIN_ANTERIEURE"));
    }

    @Test
    void une_suppression_sans_effet_repond_204_et_demande_de_ne_rien_inscrire_au_journal_d_audit() throws Exception {
        profil("ROLE_MEDECIN");
        var sansEffet = controleurSur(new AbordsVasculairesEnMemoire()).perform(delete(URL + "/" + ABORD)).andExpect(status().isNoContent()).andReturn();
        assertThat(sansEffet.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isEqualTo(Boolean.TRUE);
        var abords = new AbordsVasculairesEnMemoire();
        abords.ajouter(AbordVasculaire.enregistrer(ABORD, new PatientId(PATIENT), new CentreId(CENTRE), "FAV", null, null, LocalDate.of(2026, 1, 5), null,
                null, null, Instant.parse("2026-01-05T10:00:00Z")));
        var effectif = controleurSur(abords).perform(delete(URL + "/" + ABORD)).andExpect(status().isNoContent()).andReturn();
        assertThat(effectif.getRequest().getAttribute(AuditSansTrace.ATTRIBUT)).isNull();
        assertThat(abords.duPatient(new CentreId(CENTRE), new PatientId(PATIENT), 0, 10).total()).isZero();
    }
}
