package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.reglement.CriteresDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.LigneDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PageDeSituations;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.SituationDeFacture;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des factures avec leur situation de règlement : {@code factures} jointe à {@code facture_reglements} (somme signée), au
 * patient et à son payeur (instantané de la facture d'abord, sinon payeur courant du patient), à l'agence et à la caisse.
 * Tri {@code date_facturation DESC, numero_facture DESC} sans départage (KB-FAC-49 reproduit). La situation est calculée à chaque
 * lecture par {@link SituationDeFacture}.
 */
public class SituationsDesFacturesJdbc implements SituationsDesFactures {

    private static final String JOINTURES = """
            FROM factures f
            LEFT JOIN patients p ON p.id = f.patient_id AND p.center_id = f.center_id
            LEFT JOIN centre_payeur cp ON cp.id = COALESCE(f.centre_payeur_id_snapshot, p.centre_payeur_id) AND cp.center_id = f.center_id
            LEFT JOIN agence ag ON ag.id = COALESCE(f.agence_id_snapshot, cp.agence_id) AND ag.center_id = f.center_id
            LEFT JOIN caisse_assurance ca ON ca.id = ag.caisse_id AND ca.center_id = f.center_id
            LEFT JOIN facture_reglements fr ON fr.facture_id = f.id AND fr.center_id = f.center_id
            """;

    private static final String REGROUPEMENT = """
            GROUP BY f.id, f.numero_facture, p.numero_assurance, f.numero_immatriculation_snapshot,
                     p.nom, f.patient_full_name, p.prenom,
                     ca.id, ca.nom, ag.id, ag.nom, cp.id, cp.nom,
                     f.date_facturation, f.total_ttc
            """;

    private static final String SELECTION = """
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

    private static final String TRI = " ORDER BY f.date_facturation DESC, f.numero_facture DESC";

    private final JdbcTemplate jdbc;

    public SituationsDesFacturesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static LigneDeSituation ligne(ResultSet rs) throws SQLException {
        BigDecimal facture = arrondi(rs.getBigDecimal("montant_facture"));
        BigDecimal regle = arrondi(rs.getBigDecimal("montant_regle"));
        SituationDeFacture situation = SituationDeFacture.evaluer(facture, regle);
        return new LigneDeSituation(rs.getObject("facture_id", UUID.class), rs.getString("numero_facture"),
                rs.getString("numero_assurance"), rs.getString("patient_nom"), rs.getString("patient_prenom"),
                rs.getObject("caisse_id", UUID.class), rs.getString("caisse_nom"), rs.getObject("agence_id", UUID.class),
                rs.getString("agence_nom"), rs.getObject("centre_payeur_id", UUID.class), rs.getString("centre_payeur_nom"),
                rs.getObject("date_facturation", LocalDate.class), facture, regle, arrondi(situation.reste()),
                arrondi(situation.tropPercu()), situation.etat(), situation.solde(), rs.getString("latest_code_reglement"),
                rs.getLong("payment_count"));
    }

    private static BigDecimal arrondi(BigDecimal montant) {
        return montant == null ? BigDecimal.ZERO.setScale(2) : montant.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public PageDeSituations rechercher(CriteresDeSituation criteres, int page, int taille) {
        Filtre filtre = filtre(criteres);
        Long total = jdbc.queryForObject("SELECT COUNT(DISTINCT f.id) " + JOINTURES + filtre.clause(), Long.class,
                filtre.arguments().toArray());
        List<Object> arguments = new ArrayList<>(filtre.arguments());
        arguments.add(taille);
        arguments.add(page * taille);
        List<LigneDeSituation> lignes = jdbc.query(SELECTION + JOINTURES + filtre.clause() + REGROUPEMENT + TRI
                + " LIMIT ? OFFSET ?", (rs, i) -> ligne(rs), arguments.toArray());
        return new PageDeSituations(lignes, total != null ? total : 0L, page, taille);
    }

    @Override
    public List<LigneDeSituation> toutes(CriteresDeSituation criteres) {
        Filtre filtre = filtre(criteres);
        return jdbc.query(SELECTION + JOINTURES + filtre.clause() + REGROUPEMENT + TRI, (rs, i) -> ligne(rs),
                filtre.arguments().toArray());
    }

    @Override
    public Optional<LigneDeSituation> deLaFacture(CentreId centre, UUID facture) {
        return jdbc.query(SELECTION + JOINTURES + " WHERE f.center_id = ? AND f.id = ? " + REGROUPEMENT,
                (rs, i) -> ligne(rs), centre.valeur(), facture).stream().findFirst();
    }

    private Filtre filtre(CriteresDeSituation criteres) {
        StringBuilder clause = new StringBuilder(" WHERE f.center_id = ? AND f.date_facturation BETWEEN ? AND ? ");
        List<Object> arguments = new ArrayList<>();
        arguments.add(criteres.centre().valeur());
        arguments.add(Date.valueOf(criteres.debut()));
        arguments.add(Date.valueOf(criteres.fin()));
        if (criteres.caisse() != null) {
            clause.append(" AND ca.id = ? ");
            arguments.add(criteres.caisse());
        }
        if (criteres.agence() != null) {
            clause.append(" AND ag.id = ? ");
            arguments.add(criteres.agence());
        }
        if (criteres.centrePayeur() != null) {
            clause.append(" AND cp.id = ? ");
            arguments.add(criteres.centrePayeur());
        }
        return new Filtre(clause.toString(), arguments);
    }

    private record Filtre(String clause, List<Object> arguments) {
    }
}
