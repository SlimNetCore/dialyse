package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.application.SessionCourante;
import com.hemodialyse.backend.contexts.facturation.application.reglement.ConsulterLesReglements;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFacturesFixes;
import com.hemodialyse.backend.contexts.facturation.doubles.ReglementsEnMemoire;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Adapter entrant de la saisie des règlements paginée (RG-REG-009) : contrat HTTP de {@code GET /api/v1/reglements/page}
 * (enveloppe {@code items,total,page,size}, défauts 0/20, plafond 100, tri et filtres), 400 avec un {@code code} stable, 403
 * pour un rôle autre qu'ADMIN/SECRETAIRE (avant toute validation) et pour un centre autre que celui de la session.
 */
class ReglementControleurPageTest {

    private static final UUID CENTRE = UUID.fromString("99995820-0000-0000-0000-0000000000a1");
    private static final UUID AUTRE_CENTRE = UUID.fromString("99995820-0000-0000-0000-0000000000b1");

    private MockMvc mvc;

    private static final class SessionDuCentre implements SessionCourante {
        @Override
        public CentreId centre(UUID centreDemande) {
            if (centreDemande != null && !centreDemande.equals(CENTRE)) {
                throw new AccessDeniedException("Accès refusé : le centre demandé ne correspond pas à la session");
            }
            return new CentreId(CENTRE);
        }

        @Override
        public UUID utilisateur() {
            return null;
        }

        @Override
        public Collection<String> roles() {
            return List.of("ROLE_ADMIN");
        }
    }

    /** Le refus d'accès au format du gestionnaire commun de la plateforme ({@code ApiExceptionHandler}). */
    @RestControllerAdvice
    static class RefusDAccesCommun {
        @ExceptionHandler(AccessDeniedException.class)
        ProblemDetail refus(AccessDeniedException erreur) {
            ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
            probleme.setProperty("code", "ACCESS_DENIED");
            return probleme;
        }
    }

    @BeforeEach
    void monter() {
        SituationsDesFacturesFixes situations = new SituationsDesFacturesFixes();
        CentreId centre = new CentreId(CENTRE);
        situations.poser(centre, "FAC-0001", "2026-01-10", "BENALI", "Karim", "CNAS", "1000.00", "0.00", null);
        situations.poser(centre, "FAC-0002", "2026-01-20", "amrani", "Lina", "CASNOS", "500.00", "200.00", "REG-2");
        situations.poser(centre, "FAC-0003", "2026-02-05", "Chikhi", "Nadia", "CNAS", "800.00", "800.00", "REG-3");
        situations.poser(new CentreId(AUTRE_CENTRE), "FAC-B-0001", "2026-01-15", "Autre", "Centre", "CNAS", "900.00", "0.00", null);
        ConsulterLesReglements consulter = new ConsulterLesReglements(situations, new ReglementsEnMemoire());

        ProxyFactory securite = new ProxyFactory(new ReglementControleur(new SessionDuCentre(), null, consulter));
        securite.setProxyTargetClass(true);
        securite.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        mvc = MockMvcBuilders.standaloneSetup(securite.getProxy())
                .setControllerAdvice(new ReglementsAdvice(), new RefusDAccesCommun()).build();
        avecLeRole("SECRETAIRE");
    }

    @AfterEach
    void vider() {
        SecurityContextHolder.clearContext();
    }

    private static void avecLeRole(String role) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("marie", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private ResultActions page(String... parametres) throws Exception {
        var requete = get("/api/v1/reglements/page").param("centerId", CENTRE.toString()).param("year", "2026");
        for (int i = 0; i < parametres.length; i += 2) {
            requete = requete.param(parametres[i], parametres[i + 1]);
        }
        return mvc.perform(requete);
    }

    private void refuseeAvecLeCode(String code, String... parametres) throws Exception {
        page(parametres).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Requete invalide"))
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void la_page_par_defaut_renvoie_l_enveloppe_avec_les_lignes_de_situation_du_centre() throws Exception {
        page().andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0003", "FAC-0002", "FAC-0001")))
                .andExpect(jsonPath("$.items[0].etat").value("REGLEE"))
                .andExpect(jsonPath("$.items[0].soldeType").value("REGLE"))
                .andExpect(jsonPath("$.items[0].latestCodeReglement").value("REG-3"))
                .andExpect(jsonPath("$.items[0].montantFacture").value(800.00));
    }

    @Test
    void le_tri_les_filtres_et_la_page_sont_appliques_par_le_serveur() throws Exception {
        page("sort", "patientNom,asc", "size", "2").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0002", "FAC-0001")));
        page("etat", "NON_REGLEE,PARTIELLEMENT_REGLEE", "caisse", "cn").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0001")));
        page("soldeType", "RESTE", "montantFacture", "<800", "month", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0002")));
        page("page", "4", "size", "1").andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(4));
    }

    @Test
    void les_plages_et_la_difference_numeriques_sont_filtrees_par_le_serveur() throws Exception {
        page("montantFacture", "500..800").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0003", "FAC-0002")));
        page("montantRegle", "!=0").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0003", "FAC-0002")));
        page("montantFacture", "800..").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0003", "FAC-0001")));
        page("montantFacture", "..500").andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].numeroFacture").value(contains("FAC-0002")));
        refuseeAvecLeCode("FILTRE_INVALIDE", "montantFacture", "800..500");
        refuseeAvecLeCode("FILTRE_INVALIDE", "montantFacture", "..");
    }

    @Test
    void les_parametres_refuses_portent_un_code_stable() throws Exception {
        refuseeAvecLeCode("PAGE_INVALIDE", "page", "-1");
        refuseeAvecLeCode("TAILLE_INVALIDE", "size", "101");
        refuseeAvecLeCode("TAILLE_INVALIDE", "size", "0");
        refuseeAvecLeCode("TRI_INVALIDE", "sort", "reste,asc");
        refuseeAvecLeCode("TRI_INVALIDE", "sort", "etat,vers-le-haut");
        refuseeAvecLeCode("FILTRE_INVALIDE", "montantRegle", "beaucoup");
        refuseeAvecLeCode("FILTRE_INVALIDE", "etat", "RECU");
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "page", "x");
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "month", "13");
        refuseeAvecLeCode("PARAMETRE_INVALIDE", "caisseId", "xyz");
        mvc.perform(get("/api/v1/reglements/page").param("centerId", CENTRE.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mvc.perform(get("/api/v1/reglements/page").param("year", "2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mvc.perform(get("/api/v1/reglements/page").param("centerId", "xyz").param("year", "2026"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
        mvc.perform(get("/api/v1/reglements/page").param("centerId", CENTRE.toString()).param("year", "1999"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PARAMETRE_INVALIDE"));
    }

    @Test
    void un_role_autre_qu_admin_ou_secretaire_est_refuse_avant_toute_validation() throws Exception {
        avecLeRole("INFIRMIER");
        page("size", "1000").andExpect(status().isForbidden());
        avecLeRole("ADMIN");
        page().andExpect(status().isOk());
    }

    @Test
    void un_centre_autre_que_celui_de_la_session_est_refuse() throws Exception {
        mvc.perform(get("/api/v1/reglements/page").param("centerId", AUTRE_CENTRE.toString()).param("year", "2026"))
                .andExpect(status().isForbidden());
    }
}
