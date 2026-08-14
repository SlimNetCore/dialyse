package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.reglement.aggregate.FactureReglementAggregate;
import com.hemodialyse.backend.domain.reglement.entity.FacturePayment;
import com.hemodialyse.backend.domain.reglement.port.ReglementUseCase;
import com.hemodialyse.backend.domain.reglement.repository.ReglementRepository;
import com.hemodialyse.backend.domain.reglement.specification.FactureReglementStatusSpecification;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class FactureReglementJdbcAdapter implements ReglementRepository {

    private static final String BASE_JOINS = """
            FROM factures f
            LEFT JOIN patients p ON p.id = f.patient_id AND p.center_id = f.center_id
            LEFT JOIN centre_payeur cp ON cp.id = COALESCE(f.centre_payeur_id_snapshot, p.centre_payeur_id) AND cp.center_id = f.center_id
            LEFT JOIN agence ag ON ag.id = COALESCE(f.agence_id_snapshot, cp.agence_id) AND ag.center_id = f.center_id
            LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id AND ca.center_id = f.center_id
            LEFT JOIN facture_reglements fr ON fr.facture_id = f.id AND fr.center_id = f.center_id
            """;

    private static final String GROUP_BY = """
            GROUP BY f.id, f.numero_facture, p.numero_assurance, f.numero_immatriculation_snapshot,
                     p.nom, f.patient_full_name, p.prenom,
                     ca.id, ca.nom, ag.id, ag.nom, cp.id, cp.nom,
                     f.date_facturation, f.total_ttc
            """;

    private final JdbcTemplate jdbc;

    public FactureReglementJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PagedResult<ReglementUseCase.ReglementFactureListItem> search(ReglementUseCase.ReglementSearchQuery query) {
        SqlFilterParts filter = buildFilter(query.centerId(), query.periodStart(), query.periodEnd(), query.caisseId(), query.agenceId(), query.centrePayeurId());
        Long total = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT f.id) " + BASE_JOINS + filter.whereClause(),
                Long.class,
                filter.args().toArray()
        );

        List<Object> args = new ArrayList<>(filter.args());
        args.add(query.size());
        args.add(query.page() * query.size());

        List<ReglementUseCase.ReglementFactureListItem> items = jdbc.query(
                selectListSql() + BASE_JOINS + filter.whereClause() + GROUP_BY + " ORDER BY f.date_facturation DESC, f.numero_facture DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapListItem(rs),
                args.toArray()
        );

        return PagedResult.of(items, total != null ? total : 0L, query.page(), query.size());
    }

    @Override
    public ReglementUseCase.ReglementDashboardResult dashboard(ReglementUseCase.ReglementDashboardQuery query) {
        SqlFilterParts filter = buildFilter(query.centerId(), query.periodStart(), query.periodEnd(), query.caisseId(), query.agenceId(), query.centrePayeurId());
        List<ReglementUseCase.ReglementFactureListItem> items = jdbc.query(
                selectListSql() + BASE_JOINS + filter.whereClause() + GROUP_BY + " ORDER BY f.date_facturation DESC, f.numero_facture DESC",
                (rs, rowNum) -> mapListItem(rs),
                filter.args().toArray()
        );

        long totalFactures = items.size();
        long nonReglees = items.stream().filter(item -> item.etat() == ReglementUseCase.FactureReglementEtat.NON_REGLEE).count();
        long partiellementReglees = items.stream().filter(item -> item.etat() == ReglementUseCase.FactureReglementEtat.PARTIELLEMENT_REGLEE).count();
        long reglees = items.stream().filter(item -> item.etat() == ReglementUseCase.FactureReglementEtat.REGLEE).count();
        long tropPercus = items.stream().filter(item -> item.soldeType() == ReglementUseCase.FactureSoldeType.TROP_PERCU).count();

        BigDecimal totalFacture = sum(items.stream().map(ReglementUseCase.ReglementFactureListItem::montantFacture).toList());
        BigDecimal totalRegle = sum(items.stream().map(ReglementUseCase.ReglementFactureListItem::montantRegle).toList());
        BigDecimal totalReste = sum(items.stream().map(ReglementUseCase.ReglementFactureListItem::reste).toList());
        BigDecimal totalTropPercu = sum(items.stream().map(ReglementUseCase.ReglementFactureListItem::tropPercu).toList());

        List<ReglementUseCase.ReglementDashboardBucket> buckets = List.of(
                new ReglementUseCase.ReglementDashboardBucket(
                        ReglementUseCase.FactureReglementEtat.NON_REGLEE.name(), "Non reglee", nonReglees,
                        sum(items.stream().filter(i -> i.etat() == ReglementUseCase.FactureReglementEtat.NON_REGLEE).map(ReglementUseCase.ReglementFactureListItem::montantFacture).toList())),
                new ReglementUseCase.ReglementDashboardBucket(
                        ReglementUseCase.FactureReglementEtat.PARTIELLEMENT_REGLEE.name(), "Partiellement reglee", partiellementReglees,
                        sum(items.stream().filter(i -> i.etat() == ReglementUseCase.FactureReglementEtat.PARTIELLEMENT_REGLEE).map(ReglementUseCase.ReglementFactureListItem::montantFacture).toList())),
                new ReglementUseCase.ReglementDashboardBucket(
                        ReglementUseCase.FactureSoldeType.REGLE.name(), "Reglee",
                        items.stream().filter(i -> i.soldeType() == ReglementUseCase.FactureSoldeType.REGLE).count(),
                        sum(items.stream().filter(i -> i.soldeType() == ReglementUseCase.FactureSoldeType.REGLE).map(ReglementUseCase.ReglementFactureListItem::montantFacture).toList())),
                new ReglementUseCase.ReglementDashboardBucket(
                        ReglementUseCase.FactureSoldeType.TROP_PERCU.name(), "Trop percu", tropPercus, totalTropPercu)
        );

        return new ReglementUseCase.ReglementDashboardResult(query.centerId().value(), query.year(), query.month(),
                totalFactures, nonReglees, partiellementReglees, reglees, tropPercus,
                totalFacture, totalRegle, totalReste, totalTropPercu, buckets);
    }

    @Override
    public FactureReglementAggregate loadAggregate(CenterId centerId, UUID factureId) {
        BigDecimal montantFacture = jdbc.query(
                        "SELECT total_ttc FROM factures WHERE id = ? AND center_id = ?",
                        (rs, rowNum) -> rs.getBigDecimal("total_ttc"), factureId, centerId.value())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Facture introuvable pour le centre courant"));

        List<FacturePayment> payments = jdbc.query(
                "SELECT id, facture_id, center_id, montant, date_reglement, saisi_par, code_reglement FROM facture_reglements WHERE facture_id = ? AND center_id = ? ORDER BY date_reglement, created_at, id",
                (rs, rowNum) -> new FacturePayment(
                        rs.getObject("id", UUID.class),
                        rs.getObject("facture_id", UUID.class),
                        rs.getObject("center_id", UUID.class),
                        rs.getBigDecimal("montant"),
                        rs.getObject("date_reglement", LocalDate.class),
                        rs.getString("saisi_par"),
                        rs.getString("code_reglement")
                ),
                factureId, centerId.value()
        );

        return new FactureReglementAggregate(factureId, centerId.value(), montantFacture, payments);
    }

    @Override
    public void savePayment(FacturePayment payment) {
        jdbc.update(
                """
                        INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, code_reglement, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                payment.id(),
                payment.factureId(),
                payment.centerId(),
                payment.montant(),
                Date.valueOf(payment.dateReglement()),
                payment.saisiPar(),
                payment.codeReglement()
        );
    }

    @Override
    public ReglementUseCase.ReglementFactureListItem getFactureItem(CenterId centerId, UUID factureId) {
        List<ReglementUseCase.ReglementFactureListItem> items = jdbc.query(
                selectListSql() + BASE_JOINS + " WHERE f.center_id = ? AND f.id = ? " + GROUP_BY,
                (rs, rowNum) -> mapListItem(rs),
                centerId.value(), factureId
        );
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Facture introuvable pour le centre courant");
        }
        return items.getFirst();
    }

    @Override
    public List<ReglementUseCase.FacturePaymentItem> getPaymentHistory(CenterId centerId, UUID factureId) {
        return jdbc.query(
                "SELECT id, facture_id, montant, date_reglement, saisi_par, code_reglement " +
                        "FROM facture_reglements WHERE facture_id = ? AND center_id = ? ORDER BY date_reglement DESC, created_at DESC",
                (rs, rowNum) -> new ReglementUseCase.FacturePaymentItem(
                        rs.getObject("id", UUID.class),
                        rs.getObject("facture_id", UUID.class),
                        rs.getBigDecimal("montant"),
                        rs.getObject("date_reglement", LocalDate.class),
                        rs.getString("saisi_par"),
                        rs.getString("code_reglement")
                ),
                factureId, centerId.value()
        );
    }

    @Override
    public List<ReglementUseCase.ReglementFactureListItem> exportList(ReglementUseCase.ReglementSearchQuery query) {
        SqlFilterParts filter = buildFilter(query.centerId(), query.periodStart(), query.periodEnd(), query.caisseId(), query.agenceId(), query.centrePayeurId());
        return jdbc.query(
                selectListSql() + BASE_JOINS + filter.whereClause() + GROUP_BY + " ORDER BY f.date_facturation DESC, f.numero_facture DESC",
                (rs, rowNum) -> mapListItem(rs),
                filter.args().toArray()
        );
    }

    // ─── Private helpers ────────────────────────────────────────────────────

    private String selectListSql() {
        return """
                SELECT f.id AS facture_id,
                       f.numero_facture,
                       COALESCE(p.numero_assurance, f.numero_immatriculation_snapshot, '-') AS numero_assurance,
                       COALESCE(p.nom, f.patient_full_name, '-') AS patient_nom,
                       COALESCE(p.prenom, '') AS patient_prenom,
                       ca.id AS caisse_id,
                       COALESCE(ca.nom, 'Non renseignee') AS caisse_nom,
                       ag.id AS agence_id,
                       COALESCE(ag.nom, 'Non renseignee') AS agence_nom,
                       cp.id AS centre_payeur_id,
                       COALESCE(cp.nom, 'Non renseigne') AS centre_payeur_nom,
                       f.date_facturation,
                       f.total_ttc AS montant_facture,
                       COALESCE(SUM(fr.montant), 0) AS montant_regle,
                       COUNT(fr.id) AS payment_count,
                       (SELECT fr2.code_reglement FROM facture_reglements fr2
                        WHERE fr2.facture_id = f.id AND fr2.center_id = f.center_id
                        ORDER BY fr2.created_at DESC LIMIT 1) AS latest_code_reglement
                """;
    }

    private ReglementUseCase.ReglementFactureListItem mapListItem(java.sql.ResultSet rs) throws java.sql.SQLException {
        UUID factureId = rs.getObject("facture_id", UUID.class);
        String numero = rs.getString("numero_facture");
        String ass = rs.getString("numero_assurance");
        String nom = rs.getString("patient_nom");
        String prenom = rs.getString("patient_prenom");
        UUID caisseId = rs.getObject("caisse_id", UUID.class);
        String caisse = rs.getString("caisse_nom");
        UUID agenceId = rs.getObject("agence_id", UUID.class);
        String agence = rs.getString("agence_nom");
        UUID cpId = rs.getObject("centre_payeur_id", UUID.class);
        String cp = rs.getString("centre_payeur_nom");
        LocalDate dateFac = rs.getObject("date_facturation", LocalDate.class);
        BigDecimal facture = scale(rs.getBigDecimal("montant_facture"));
        BigDecimal regle = scale(rs.getBigDecimal("montant_regle"));
        String latestCode = rs.getString("latest_code_reglement");
        long paymentCount = rs.getLong("payment_count");

        FactureReglementStatusSpecification.Evaluation eval =
                FactureReglementStatusSpecification.evaluate(facture, regle);

        return new ReglementUseCase.ReglementFactureListItem(
                factureId, numero, ass, nom, prenom,
                caisseId, caisse, agenceId, agence, cpId, cp,
                dateFac, facture, regle,
                scale(eval.reste()), scale(eval.tropPercu()),
                eval.etat(), eval.soldeType(),
                latestCode,
                paymentCount
        );
    }

    private SqlFilterParts buildFilter(CenterId centerId, LocalDate start, LocalDate end,
                                       UUID caisseId, UUID agenceId, UUID centrePayeurId) {
        StringBuilder where = new StringBuilder(" WHERE f.center_id = ? AND f.date_facturation BETWEEN ? AND ? ");
        List<Object> args = new ArrayList<>();
        args.add(centerId.value());
        args.add(Date.valueOf(start));
        args.add(Date.valueOf(end));
        if (caisseId != null) {
            where.append(" AND ca.id = ? ");
            args.add(caisseId);
        }
        if (agenceId != null) {
            where.append(" AND ag.id = ? ");
            args.add(agenceId);
        }
        if (centrePayeurId != null) {
            where.append(" AND cp.id = ? ");
            args.add(centrePayeurId);
        }
        return new SqlFilterParts(where.toString(), args);
    }

    private BigDecimal sum(List<BigDecimal> amounts) {
        return amounts.stream().map(this::scale).reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);
    }

    private BigDecimal scale(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private record SqlFilterParts(String whereClause, List<Object> args) {
    }
}


