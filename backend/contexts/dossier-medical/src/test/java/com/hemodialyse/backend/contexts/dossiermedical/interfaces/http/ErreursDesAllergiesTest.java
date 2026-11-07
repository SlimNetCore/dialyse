package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.ConsulterLesAllergiesDuPatient;
import com.hemodialyse.backend.contexts.dossiermedical.application.CorrigerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.application.DeclarerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.SessionCourante;
import com.hemodialyse.backend.contexts.dossiermedical.application.SupprimerUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.contrat.AllergiesEnMemoire;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Allergies;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeClinique;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CriticiteDAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneAllergie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SystemeDeCodage;
import com.hemodialyse.backend.contexts.dossiermedical.domain.TypeDeReaction;
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
 * Les refus des allergies au format commun, par l'advice du module : verrou non obtenu 409 {@code ALLERGIE_ECRITURE_CONCURRENTE} ; patient
 * inconnu 404 ; allergie introuvable 400 (reproduit) ; entrée illisible ou inconnue 400 sans nom de classe interne, et 403 AVANT 400 pour un
 * profil sans droit, l'infirmier compris, y compris pour {@code critiques} (KB-MED-58, 59 et 69) ; règle de gestion 422 avec son code ;
 * suppression sans effet 204 et marquée « sans trace ».
 */
class ErreursDesAllergiesTest {
    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID INCONNU = UUID.fromString("00000000-0000-0000-0000-0000000000ff");
    private static final UUID ALLERGIE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
    private static final String URL = "/api/v1/patients/" + PATIENT + "/allergies";
    private static final String VALIDE = "{\"codeSystem\":\"LOCAL\",\"code\":\"latex\",\"categorie\":\"ENVIRONNEMENT\",\"criticite\":\"BASSE\","
            + "\"typeReaction\":\"ALLERGIE\"}";

    @AfterEach
    void apres() {
        SecurityContextHolder.clearContext();
    }

    private static void profil(String... roles) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p", roles));
    }

    private static MockMvc controleurSur(Allergies allergies) {
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
        var controleur = new AllergiesControleur(new ConsulterLesAllergiesDuPatient(session, allergies),
                new DeclarerUneAllergie(session, (centre, patient) -> !patient.valeur().equals(INCONNU), allergies, unite, UUID::randomUUID, horloge),
                new CorrigerUneAllergie(session, allergies, unite, horloge), new SupprimerUneAllergie(session, allergies, unite));
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static Allergies verrouillee() {
        return new AllergiesEnMemoire() {
            @Override
            public Optional<Allergie> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                throw new EcritureConcurrenteSurUneAllergie();
            }
        };
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_l_allergie() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(verrouillee());
        mvc.perform(put(URL + "/" + ALLERGIE).contentType(MediaType.APPLICATION_JSON).content("{\"criticite\":\"BASSE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALLERGIE_ECRITURE_CONCURRENTE"));
        mvc.perform(delete(URL + "/" + ALLERGIE)).andExpect(status().isConflict());
    }

    @Test
    void un_patient_inconnu_repond_404_et_une_allergie_introuvable_400() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AllergiesEnMemoire());
        mvc.perform(post("/api/v1/patients/" + INCONNU + "/allergies").contentType(MediaType.APPLICATION_JSON).content(VALIDE))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PATIENT_INTROUVABLE"));
        mvc.perform(put(URL + "/" + ALLERGIE).contentType(MediaType.APPLICATION_JSON).content("{\"criticite\":\"BASSE\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Allergie introuvable"));
    }

    @Test
    void une_enumeration_ou_un_systeme_inconnu_ou_absent_repond_400_sans_nom_de_classe_interne() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AllergiesEnMemoire());
        for (String corps : List.of("{}", VALIDE.replace("ENVIRONNEMENT", "AUTRE"), VALIDE.replace("BASSE", "MOYENNE"),
                VALIDE.replace("\"typeReaction\":\"ALLERGIE\"", "\"typeReaction\":\"X\""), VALIDE.replace("LOCAL", "SNOMED"),
                VALIDE.replace("\"criticite\":\"BASSE\",", ""), VALIDE.replace("}", ",\"statutVerification\":\"PROBABLE\"}"))) {
            mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(corps)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST")).andExpect(jsonPath("$.detail")
                            .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("com.hemodialyse"))))
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("No enum constant"))));
        }
        mvc.perform(put(URL + "/" + ALLERGIE).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
    }

    @Test
    void un_corps_ou_un_identifiant_illisible_repond_400_pour_un_medecin_et_403_pour_un_profil_sans_droit() throws Exception {
        var mvc = controleurSur(new AllergiesEnMemoire());
        profil("ROLE_MEDECIN");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE.replace("}", ",\"dateConstatation\":\"demain\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patients/pas-un-uuid/allergies/critiques")).andExpect(status().isBadRequest());
        profil("ROLE_INFIRMIER");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get("/api/v1/patients/pas-un-uuid/allergies/critiques")).andExpect(status().isForbidden());
        profil("ROLE_ADMIN");
        mvc.perform(put(URL + "/" + ALLERGIE).contentType(MediaType.APPLICATION_JSON).content("pas du json")).andExpect(status().isForbidden());
        mvc.perform(get(URL).param("size", "x")).andExpect(status().isBadRequest());
    }

    @Test
    void les_regles_de_gestion_repondent_422_avec_leur_code() throws Exception {
        profil("ROLE_MEDECIN");
        var mvc = controleurSur(new AllergiesEnMemoire());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE.replace("BASSE", "HAUTE")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ALLERGIE_MANIFESTATIONS_REQUISES"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE.replace("}", ",\"dateConstatation\":\"2026-01-11\"}")))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ALLERGIE_DATE_FUTURE"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALIDE)).andExpect(status().isOk());
    }

    @Test
    void un_code_trop_long_repond_400_au_lieu_de_500() throws Exception {
        profil("ROLE_MEDECIN");
        controleurSur(new AllergiesEnMemoire()).perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content(VALIDE.replace("latex", "c".repeat(21)))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void une_suppression_sans_effet_repond_204_et_demande_de_ne_rien_inscrire_au_journal_d_audit() throws Exception {
        profil("ROLE_MEDECIN");
        var sansEffet = controleurSur(new AllergiesEnMemoire()).perform(delete(URL + "/" + ALLERGIE)).andExpect(status().isNoContent()).andReturn();
        assertThat(sansEffet.getRequest().getAttribute(AllergiesControleur.ATTRIBUT_SANS_TRACE)).isEqualTo(Boolean.TRUE);
        var allergies = new AllergiesEnMemoire();
        allergies.ajouter(Allergie.declarer(ALLERGIE, new PatientId(PATIENT), new CentreId(CENTRE), CodeClinique.saisir(SystemeDeCodage.LOCAL, "x", null),
                CategorieDAllergie.ALIMENT, CriticiteDAllergie.BASSE, TypeDeReaction.ALLERGIE, null, LocalDate.of(2026, 1, 5), null,
                LocalDate.of(2026, 1, 10), Instant.parse("2026-01-05T10:00:00Z")));
        var effectif = controleurSur(allergies).perform(delete(URL + "/" + ALLERGIE)).andExpect(status().isNoContent()).andReturn();
        assertThat(effectif.getRequest().getAttribute(AllergiesControleur.ATTRIBUT_SANS_TRACE)).isNull();
        assertThat(allergies.duPatient(new CentreId(CENTRE), new PatientId(PATIENT), 0, 10).total()).isZero();
    }
}
