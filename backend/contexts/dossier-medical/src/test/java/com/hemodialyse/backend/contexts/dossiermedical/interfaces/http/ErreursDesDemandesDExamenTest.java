package com.hemodialyse.backend.contexts.dossiermedical.interfaces.http;

import com.hemodialyse.backend.contexts.dossiermedical.application.EnregistrerLePrelevement;
import com.hemodialyse.backend.contexts.dossiermedical.application.HorlogeDuCentre;
import com.hemodialyse.backend.contexts.dossiermedical.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeDemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Les refus des demandes d'examen au format commun, par l'advice du module : une écriture qui n'obtient pas le verrou de la demande
 * (le port lève {@link EcritureConcurrenteSurUneDemandeDExamen}, ce que fait l'adapter JDBC sur le SQLSTATE 55P03) répond 409
 * {@code DEMANDE_EXAMEN_ECRITURE_CONCURRENTE} ; une demande introuvable répond 400 {@code BAD_REQUEST} (reproduit).
 */
class ErreursDesDemandesDExamenTest {

    private static final UUID CENTRE = UUID.fromString("00000000-0000-0000-0000-00000000c001");
    private static final UUID PATIENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID DEMANDE = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

    private static MockMvc controleurSur(DemandesDExamen demandes) {
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
        var prelever = new EnregistrerLePrelevement(demande -> new CentreId(CENTRE), demandes, unite, HorlogeDuCentre.enUtc(Clock.systemUTC()));
        var controleur = new DemandesDExamenControleur(null, null, prelever, null, null, null);
        return MockMvcBuilders.standaloneSetup(controleur).setControllerAdvice(new ErreursDuDossierMedicalAdvice()).build();
    }

    private static DemandesDExamen port(Optional<DemandeExamen> trouvee, RuntimeException aLever) {
        return new DemandesDExamen() {
            @Override
            public void ajouter(DemandeExamen demande) {
            }

            @Override
            public void enregistrerLaTransition(DemandeExamen demande) {
            }

            @Override
            public Optional<DemandeExamen> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
                if (aLever != null) throw aLever;
                return trouvee;
            }

            @Override
            public PageDeDemandesDExamen duPatient(CentreId centre, PatientId patient, int page, int taille) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    void une_ecriture_qui_n_obtient_pas_le_verrou_repond_409_avec_le_code_de_la_demande_d_examen() throws Exception {
        controleurSur(port(Optional.empty(), new EcritureConcurrenteSurUneDemandeDExamen()))
                .perform(put("/api/v1/patients/" + PATIENT + "/demandes-examen/" + DEMANDE + "/preleve")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEMANDE_EXAMEN_ECRITURE_CONCURRENTE"));
    }

    @Test
    void une_demande_introuvable_repond_400_bad_request() throws Exception {
        controleurSur(port(Optional.empty(), null))
                .perform(put("/api/v1/patients/" + PATIENT + "/demandes-examen/" + DEMANDE + "/preleve")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
