package com.hemodialyse.backend.infrastructure.web.rest;

import com.hemodialyse.backend.application.notification.NotificationService;
import com.hemodialyse.backend.contexts.absencespatients.publishedlanguage.AbsencesDuPilotage;
import com.hemodialyse.backend.domain.seance.port.SeanceUseCase;
import com.hemodialyse.backend.infrastructure.security.CenterAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * KB-ABS-35 (slice A4) : dans le mois en cours, les absences datées après aujourd'hui (fuseau du centre) ne sont pas comptées, ni
 * dans le tableau de bord, ni dans son détail, ni dans les exports. Le port {@link AbsencesDuPilotage} est simulé (aujourd'hui
 * fixé au 15 mars 2026) ; il applique les bornes qu'on lui passe, comme l'adaptateur JDBC.
 */
class BorneDesAbsencesDuTableauDeBordTest {

    private static final UUID CENTRE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PATIENT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate AUJOURDHUI = LocalDate.of(2026, 3, 15);

    /** Port simulé : une absence le 10 mars, une le 15 (aujourd'hui), deux plus tard dans le mois (données héritées). */
    private static final class PortSimule implements AbsencesDuPilotage {
        private final List<AbsenceDuCentre> absences = List.of(
                new AbsenceDuCentre(PATIENT, LocalDate.of(2026, 3, 10), "A_QUALIFIER"),
                new AbsenceDuCentre(PATIENT, LocalDate.of(2026, 3, 15), "JUSTIFIEE"),
                new AbsenceDuCentre(PATIENT, LocalDate.of(2026, 3, 16), "A_QUALIFIER"),
                new AbsenceDuCentre(PATIENT, LocalDate.of(2026, 3, 28), "NON_JUSTIFIEE"));

        @Override
        public LocalDate aujourdhui() {
            return AUJOURDHUI;
        }

        @Override
        public long nombreHorsAnnulees(UUID centre, LocalDate du, LocalDate au) {
            return horsAnnulees(centre, du, au).size();
        }

        @Override
        public List<AbsenceDuCentre> horsAnnulees(UUID centre, LocalDate du, LocalDate au) {
            return absences.stream().filter(a -> !a.dateDeSeance().isBefore(du) && !a.dateDeSeance().isAfter(au)).toList();
        }

        @Override
        public List<CumulDuCentre> cumulParCentre(Collection<UUID> centres, LocalDate du, LocalDate au) {
            return List.of();
        }

        @Override
        public List<AbsencesDuMois> mensuel(Collection<UUID> centres, LocalDate du, LocalDate au) {
            return List.of(new AbsencesDuMois(2026, 3, 0, BigDecimal.ZERO));
        }
    }

    private SeanceRestController controleur() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(eq("SELECT id, nom, prenom FROM patients WHERE center_id = ?"), eq(CENTRE)))
                .thenReturn(List.of(Map.of("id", PATIENT, "nom", "Amrani", "prenom", "Lina")));
        return new SeanceRestController(mock(SeanceUseCase.class), mock(NotificationService.class), jdbc,
                mock(CenterAccessGuard.class), new PortSimule());
    }

    @Test
    void le_tableau_de_bord_du_mois_en_cours_ne_compte_que_les_absences_jusqu_a_aujourd_hui() {
        ResponseEntity<?> reponse = controleur().dashboard(CENTRE, 2026, 3);

        @SuppressWarnings("unchecked")
        Map<String, Object> corps = (Map<String, Object>) reponse.getBody();
        assertNotNull(corps);
        assertEquals(2L, corps.get("absenceCount"));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) corps.get("absenceDetails");
        assertEquals(AUJOURDHUI, details.get("periodEnd"));
    }

    @Test
    void le_detail_des_absences_du_mois_en_cours_s_arrete_a_aujourd_hui() {
        ResponseEntity<?> reponse = controleur().dashboardDetails(CENTRE, 2026, 3, "absence");

        @SuppressWarnings("unchecked")
        Map<String, Object> corps = (Map<String, Object>) reponse.getBody();
        assertNotNull(corps);
        assertEquals(2, corps.get("total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) corps.get("items");
        assertEquals(List.of(LocalDate.of(2026, 3, 10), AUJOURDHUI), items.stream().map(i -> i.get("dateSeance")).toList());
    }

    @Test
    void les_exports_du_mois_en_cours_ne_comptent_pas_les_absences_futures() {
        SeanceRestController controleur = controleur();

        ResponseEntity<?> csv = controleur.exportDashboard(CENTRE, 2026, 3, "csv");
        assertTrue(((String) csv.getBody()).startsWith("year,month,expectedSeances,presenceCount,absenceCount,totalSeances\n2026,3,0,0,2,0"),
                "CSV : 2 absences");

        for (String format : List.of("pdf", "xlsx")) {
            ResponseEntity<?> binaire = controleur.exportDashboard(CENTRE, 2026, 3, format);
            assertEquals(200, binaire.getStatusCode().value());
            assertTrue(((byte[]) binaire.getBody()).length > 0, format);
        }
    }
}
