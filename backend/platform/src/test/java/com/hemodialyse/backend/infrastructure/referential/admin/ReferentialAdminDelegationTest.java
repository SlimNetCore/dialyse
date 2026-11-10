package com.hemodialyse.backend.infrastructure.referential.admin;

import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportReport;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ImportTable;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate;
import com.hemodialyse.backend.infrastructure.referential.admin.service.ReferentialAdminDomainService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF1 : les transporteurs ne sont plus gérés par l'administration générique mais par patient-couverture ; le service générique
 * garde la lecture du fichier (en-têtes, bornes, compte rendu) et délègue la liste, la saisie, la suppression et les lignes importées.
 */
class ReferentialAdminDelegationTest {

    private static final CenterId CENTER = CenterId.of(UUID.fromString("00000000-0000-0000-0000-00000000000a"));

    private final List<String> appels = new ArrayList<>();
    private final List<ReferentialAdminDelegate.Row> lignesRecues = new ArrayList<>();
    private List<ValidationIssue> anomaliesRendues = List.of();
    private InMemoryReferentialAdminRepository repo;
    private ReferentialAdminDomainService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryReferentialAdminRepository();
        ReferentialAdminDelegate delegate = new ReferentialAdminDelegate() {
            @Override
            public boolean prend(ReferentialKind kind) {
                return kind == ReferentialKind.TRANSPORTEUR;
            }

            @Override
            public PagedResult<ReferentialEntry> list(CenterId centerId, ReferentialKind kind, String search, int page, int size) {
                appels.add("list:" + search + ":" + page + ":" + size);
                return PagedResult.of(List.of(), 0, page, size);
            }

            @Override
            public ReferentialEntry create(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
                appels.add("create:" + values.get("nom"));
                return new ReferentialEntry(UUID.randomUUID(), values, Map.of());
            }

            @Override
            public ReferentialEntry update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
                appels.add("update:" + id);
                return new ReferentialEntry(id, values, Map.of());
            }

            @Override
            public void delete(CenterId centerId, ReferentialKind kind, UUID id) {
                appels.add("delete:" + id);
            }

            @Override
            public Outcome importRows(CenterId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
                appels.add("import:" + rows.size() + ":" + dryRun);
                lignesRecues.addAll(rows);
                return new Outcome(1, 2, anomaliesRendues, anomaliesRendues.isEmpty() && !dryRun);
            }
        };
        service = new ReferentialAdminDomainService(repo, delegate);
    }

    @Test
    void la_liste_est_normalisee_puis_deleguee_sans_toucher_au_depot_generique() {
        service.list(CENTER, ReferentialKind.TRANSPORTEUR, "  taxi ", -3, 1000);
        service.list(CENTER, ReferentialKind.TRANSPORTEUR, " ", 2, 0);

        assertThat(appels).containsExactly("list:taxi:0:100", "list:null:2:20");
        assertThat(repo.writes).isZero();
    }

    @Test
    void la_saisie_et_la_suppression_des_transporteurs_sont_deleguees() {
        UUID id = UUID.randomUUID();
        service.create(CENTER, ReferentialKind.TRANSPORTEUR, Map.of("nom", "Taxi"));
        service.update(CENTER, ReferentialKind.TRANSPORTEUR, id, Map.of("nom", "Taxi 2"));
        service.delete(CENTER, ReferentialKind.TRANSPORTEUR, id);

        assertThat(appels).containsExactly("create:Taxi", "update:" + id, "delete:" + id);
        assertThat(repo.writes).isZero();
    }

    @Test
    void les_autres_referentiels_restent_geres_par_le_service_generique() {
        service.create(CENTER, ReferentialKind.SALLE, Map.of("code", "S1", "nom", "Salle 1"));

        assertThat(appels).isEmpty();
        assertThat(repo.of(CENTER, ReferentialKind.SALLE)).hasSize(1);
    }

    private static ImportTable table(List<String> headers, List<List<String>> rows) {
        List<ImportTable.Row> out = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) out.add(new ImportTable.Row(i + 2, rows.get(i)));
        return new ImportTable(headers, out);
    }

    @Test
    void l_import_lit_les_en_tetes_puis_delegue_les_lignes_non_vides_avec_leur_numero() {
        ImportReport report = service.importEntries(CENTER, ReferentialKind.TRANSPORTEUR,
                table(List.of("TRANSPORTEUR", "TÉL", "Autre"),
                        List.of(List.of("Taxi", "0795006136", "x"), List.of("", "", ""), List.of("Ambulance", "", "y"))), false);

        assertThat(appels).containsExactly("import:2:false");
        assertThat(lignesRecues).extracting(ReferentialAdminDelegate.Row::lineNumber).containsExactly(2, 4);
        assertThat(lignesRecues.get(0).values()).containsEntry("nom", "Taxi").containsEntry("telephone", "0795006136");
        assertThat(report.ignoredColumns()).containsExactly("Autre");
        assertThat(report.created()).isEqualTo(1);
        assertThat(report.updated()).isEqualTo(2);
        assertThat(report.totalRows()).isEqualTo(2);
        assertThat(report.applied()).isTrue();
    }

    @Test
    void les_anomalies_deleguees_rendent_un_fichier_refuse_et_les_bornes_sont_controlees_avant() {
        anomaliesRendues = List.of(new ValidationIssue(3, "nom", "REQUIRED", "Obligatoire.", Map.of()));
        ImportReport refuse = service.importEntries(CENTER, ReferentialKind.TRANSPORTEUR,
                table(List.of("nom"), List.of(List.of("Taxi"))), false);
        assertThat(refuse.applied()).isFalse();
        assertThat(refuse.errors()).extracting(ValidationIssue::code).containsExactly("REQUIRED");

        appels.clear();
        ImportReport sansColonne = service.importEntries(CENTER, ReferentialKind.TRANSPORTEUR,
                table(List.of("telephone"), List.of(List.of("0795006136"))), false);
        ImportReport vide = service.importEntries(CENTER, ReferentialKind.TRANSPORTEUR, table(List.of("nom"), List.of()), false);
        assertThat(sansColonne.missingColumns()).containsExactly("nom");
        assertThat(vide.errors()).extracting(ValidationIssue::code).containsExactly("NO_DATA");
        assertThat(appels).isEmpty();
    }
}
