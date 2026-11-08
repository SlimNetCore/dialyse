package com.hemodialyse.backend.contexts.seance.interfaces.http;

import com.hemodialyse.backend.contexts.seance.application.AccesRefuse;
import com.hemodialyse.backend.contexts.seance.application.ConsulterUneSeance;
import com.hemodialyse.backend.contexts.seance.application.CreerUneSeance;
import com.hemodialyse.backend.contexts.seance.application.FicheDuPatient;
import com.hemodialyse.backend.contexts.seance.application.ForfaitsDeSeance;
import com.hemodialyse.backend.contexts.seance.application.HistoriqueDesSeances;
import com.hemodialyse.backend.contexts.seance.application.ListerLesSeances;
import com.hemodialyse.backend.contexts.seance.application.ScannerUnPatient;
import com.hemodialyse.backend.contexts.seance.application.SeanceEnregistree;
import com.hemodialyse.backend.contexts.seance.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.seance.domain.MotifDeRefus;
import com.hemodialyse.backend.contexts.seance.domain.PatientId;
import com.hemodialyse.backend.contexts.seance.domain.PatientIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.seance.domain.Seance;
import com.hemodialyse.backend.contexts.seance.domain.SeanceDuJourAmbigue;
import com.hemodialyse.backend.contexts.seance.domain.SeanceId;
import com.hemodialyse.backend.contexts.seance.domain.SeanceIntrouvable;
import com.hemodialyse.backend.contexts.seance.domain.SeanceRefusee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adapter entrant : formes JSON identiques au legacy, table des refus (statut + code) et garde « 403 avant 400 ». Les cas d'usage sont
 * simulés : leurs règles sont prouvées dans les tests de l'application.
 */
class SeancesControleurTest {

    private static final UUID CENTRE = new UUID(0, 0xA);
    private static final UUID PATIENT = new UUID(1, 1);
    private static final UUID SEANCE = new UUID(2, 2);
    private static final LocalDate JOUR = LocalDate.of(2026, 6, 15);

    private CreerUneSeance creer;
    private ScannerUnPatient scanner;
    private ConsulterUneSeance consulter;
    private ListerLesSeances lister;
    private MockMvc mvc;

    @BeforeEach
    void monter() {
        creer = Mockito.mock(CreerUneSeance.class);
        scanner = Mockito.mock(ScannerUnPatient.class);
        consulter = Mockito.mock(ConsulterUneSeance.class);
        lister = Mockito.mock(ListerLesSeances.class);
        valider = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance.class);
        signer = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.SignerUneSeance.class);
        deverrouiller = Mockito.mock(com.hemodialyse.backend.contexts.seance.application.DeverrouillerUneSeance.class);
        mvc = MockMvcBuilders.standaloneSetup(new SeancesControleur(creer, scanner, consulter, lister, valider, signer, deverrouiller))
                .setControllerAdvice(new ErreursDeSeanceAdvice()).build();
        avecLeRole("INFIRMIER");
    }

    private com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance valider;
    private com.hemodialyse.backend.contexts.seance.application.SignerUneSeance signer;
    private com.hemodialyse.backend.contexts.seance.application.DeverrouillerUneSeance deverrouiller;

    @Test
    void la_validation_repond_avec_les_quatre_champs_du_legacy() throws Exception {
        Seance validee = seance();
        validee.validerParLInfirmier(Instant.parse("2026-06-15T09:00:00Z"), new com.hemodialyse.backend.contexts.seance.domain.Signataire("inf-01"));
        Mockito.when(valider.executer(eq(CENTRE), eq(SEANCE), eq("inf-01"), any()))
                .thenReturn(new com.hemodialyse.backend.contexts.seance.application.ValiderUneSeance.Validation(validee, fiche()));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"userId\":\"inf-01\",\"consommations\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEANCE.toString()))
                .andExpect(jsonPath("$.status").value("VALIDEE"))
                .andExpect(jsonPath("$.validatedAt").exists())
                .andExpect(jsonPath("$.signedByInfirmierAt").exists());
    }

    @Test
    void la_signature_et_le_deverrouillage_repondent_avec_les_champs_du_legacy() throws Exception {
        Seance signee = Seance.restituer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR,
                com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance.VALIDEE, null, null, null);
        signee.signerParLeMedecin(Instant.parse("2026-06-15T09:00:00Z"), new com.hemodialyse.backend.contexts.seance.domain.Signataire("med"));
        Mockito.when(signer.executer(eq(CENTRE), eq(SEANCE), eq("med"))).thenReturn(signee);
        avecLeRole("MEDECIN");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/signer-medecin").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"userId\":\"med\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNEE"))
                .andExpect(jsonPath("$.signedByMedecinAt").exists());

        Seance deverrouillee = seance();
        deverrouillee.deverrouillerPourRegularisation(Instant.parse("2026-06-20T09:00:00Z"),
                new com.hemodialyse.backend.contexts.seance.domain.Signataire("u"), JOUR.plusDays(5));
        Mockito.when(deverrouiller.executer(CENTRE, SEANCE)).thenReturn(deverrouillee);
        avecLeRole("ADMIN");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/deverrouiller-regularisation").param("centerId", CENTRE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREE"))
                .andExpect(jsonPath("$.regularisationDeverrouilleeAt").exists());
    }

    @Test
    void les_routes_de_la_vie_de_la_seance_refusent_en_403_avant_400_et_en_400_sans_centre() throws Exception {
        avecLeRole("SECRETAIRE");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON).content("pas du json"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/deverrouiller-regularisation"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        avecLeRole("ADMIN");
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/deverrouiller-regularisation"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("Centre requis"));
        mvc.perform(post("/api/v1/seances/pas-un-uuid/valider").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void un_refus_du_stock_repond_422_avec_le_code_du_stock() throws Exception {
        Mockito.when(valider.executer(eq(CENTRE), eq(SEANCE), any(), any())).thenThrow(
                new com.hemodialyse.backend.contexts.seance.application.ConsommationRefusee("PIECE_DEJA_RAPPROCHEE", "pièce rapprochée"));
        mvc.perform(post("/api/v1/seances/" + SEANCE + "/valider").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\"}"))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("PIECE_DEJA_RAPPROCHEE"));
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private static void avecLeRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("marie", "n/a", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private static Seance seance() {
        return Seance.creer(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), JOUR, Instant.parse("2026-06-15T08:00:00Z"));
    }

    private static FicheDuPatient fiche() {
        return new FicheDuPatient(new PatientId(PATIENT), "P-1", "Nom", "Prenom", "F", JOUR.minusYears(40), "123", "A+", "0550", null, null,
                null, new UUID(3, 3), "G1", "Fresenius", "ACTIF");
    }

    @Test
    void la_creation_repond_avec_les_champs_du_legacy() throws Exception {
        Mockito.when(creer.executer(eq(CENTRE), eq(PATIENT), any())).thenReturn(new SeanceEnregistree(seance(), fiche()));
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"patientId\":\"" + PATIENT + "\",\"dateSeance\":\"2026-06-15\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SEANCE.toString()))
                .andExpect(jsonPath("$.status").value("CREE"))
                .andExpect(jsonPath("$.horsPlanning").value(false))
                .andExpect(jsonPath("$.motifHorsPlanning").doesNotExist())
                .andExpect(jsonPath("$.generateurNom").value("G1"))
                .andExpect(jsonPath("$.generateurMarque").value("Fresenius"))
                .andExpect(jsonPath("$.generateurEtat").value("ACTIF"));
    }

    @Test
    void le_scan_ajoute_l_identite_et_les_drapeaux() throws Exception {
        Mockito.when(scanner.executer(eq(CENTRE), eq("P-1"), any(), any()))
                .thenReturn(new ScannerUnPatient.ResultatDuScan(seance(), fiche(), true, true, false));
        mvc.perform(post("/api/v1/seances/scan").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"qrCode\":\"P-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientNom").value("Nom"))
                .andExpect(jsonPath("$.patientPrenom").value("Prenom"))
                .andExpect(jsonPath("$.patientCode").value("P-1"))
                .andExpect(jsonPath("$.created").value(true))
                .andExpect(jsonPath("$.validatedNow").value(true))
                .andExpect(jsonPath("$.alreadyValidated").value(false));
    }

    @Test
    void le_detail_porte_les_sept_blocs_du_legacy() throws Exception {
        var ligne = new com.hemodialyse.backend.contexts.seance.application.ConsommationsDeLaSeance.Ligne(new UUID(7, 7), "ART", "Aiguille",
                "u", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN);
        Mockito.when(consulter.executer(CENTRE, SEANCE)).thenReturn(new ConsulterUneSeance.DetailDeSeance(seance(), fiche(), Optional.empty(),
                Optional.empty(), List.of(ligne), BigDecimal.TEN, Optional.empty()));
        avecLeRole("MEDECIN");
        mvc.perform(get("/api/v1/seances/" + SEANCE).param("centerId", CENTRE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seance.status").value("CREE"))
                .andExpect(jsonPath("$.seance.precisionHorsPlanning").doesNotExist())
                .andExpect(jsonPath("$.patient.codePatient").value("P-1"))
                .andExpect(jsonPath("$.patient.numeroAssurance").value("123"))
                .andExpect(jsonPath("$.patient.telMobile").value("0550"))
                .andExpect(jsonPath("$.consommables[0].articleCode").value("ART"))
                .andExpect(jsonPath("$.consommablesTotalValorise").value(10))
                .andExpect(jsonPath("$.paramedical").doesNotExist())
                .andExpect(jsonPath("$.medical").doesNotExist())
                .andExpect(jsonPath("$.forfait").doesNotExist());
    }

    @Test
    void la_liste_porte_les_lignes_le_forfait_et_la_pagination() throws Exception {
        var ligne = new HistoriqueDesSeances.LigneDeSeance(new SeanceId(SEANCE), new CentreId(CENTRE), new PatientId(PATIENT), "P-1", "Nom",
                "Prenom", JOUR, com.hemodialyse.backend.contexts.seance.domain.StatutDeSeance.VALIDEE, null, null, null, null, null, false, null);
        var forfait = new ForfaitsDeSeance.ForfaitCourant(new UUID(8, 8), "F1", "Forfait", BigDecimal.valueOf(100), 0, null, null, false);
        Mockito.when(lister.executer(any())).thenReturn(PagedResult.of(
                List.of(new ListerLesSeances.LigneDeLHistorique(ligne, Optional.of(forfait))), 1L, 0, 20));
        avecLeRole("SECRETAIRE");
        mvc.perform(get("/api/v1/seances").param("centerId", CENTRE.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].status").value("VALIDEE"))
                .andExpect(jsonPath("$.items[0].patientCode").value("P-1"))
                .andExpect(jsonPath("$.items[0].forfait.code").value("F1"))
                .andExpect(jsonPath("$.items[0].forfait.nombreSeances").value(0))
                .andExpect(jsonPath("$.items[0].forfait.updatedAt").doesNotExist());
    }

    @Test
    void les_refus_ont_le_statut_et_le_code_decides() throws Exception {
        refus(new SeanceRefusee(MotifDeRefus.CENTRE_FERME, "Centre fermé"), 422, "SEANCE_CENTRE_FERME");
        refus(new SeanceRefusee(MotifDeRefus.PRISE_EN_CHARGE_ABSENTE, "Pas de prise en charge"), 422, "BUSINESS_RULE_VIOLATION");
        refus(new SaisieInvalide("Patient requis"), 400, "BAD_REQUEST");
        refus(new SeanceIntrouvable(), 400, "BAD_REQUEST");
        refus(new PatientIntrouvable(), 404, "PATIENT_INTROUVABLE");
        refus(new SeanceDuJourAmbigue(), 409, "SEANCE_DU_JOUR_AMBIGUE");
        refus(new EcritureConcurrente(), 409, "ECRITURE_CONCURRENTE");
        refus(new AccesRefuse("Accès refusé : le centre demandé ne correspond pas à la session"), 403, "ACCESS_DENIED");
    }

    private void refus(RuntimeException erreur, int statut, String code) throws Exception {
        Mockito.doThrow(erreur).when(creer).executer(any(), any(), any());
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"centerId\":\"" + CENTRE + "\",\"patientId\":\"" + PATIENT + "\"}"))
                .andExpect(status().is(statut))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void un_corps_illisible_est_un_400_pour_un_profil_autorise() throws Exception {
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void une_creation_sans_centre_dans_le_corps_est_un_400_sans_appeler_le_cas_d_usage() throws Exception {
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON).content("{\"patientId\":\"" + PATIENT + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        Mockito.verify(creer, Mockito.never()).executer(any(), any(), any());
    }

    @Test
    void un_corps_illisible_est_un_403_pour_un_profil_sans_droit_sur_la_route() throws Exception {
        avecLeRole("SECRETAIRE");
        mvc.perform(post("/api/v1/seances").contentType(MediaType.APPLICATION_JSON).content("{pas du json"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void un_scan_sans_qr_est_un_400_de_validation_pour_un_profil_autorise_et_un_403_sinon() throws Exception {
        String corps = "{\"centerId\":\"" + CENTRE + "\",\"qrCode\":\" \"}";
        mvc.perform(post("/api/v1/seances/scan").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        avecLeRole("MEDECIN");
        mvc.perform(post("/api/v1/seances/scan").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isForbidden());
        Mockito.verify(scanner, Mockito.never()).executer(any(), any(), any(), any());
        Mockito.verify(lister, Mockito.never()).executer(any());
        Mockito.verify(consulter, Mockito.never()).executer(any(), any());
    }
}
