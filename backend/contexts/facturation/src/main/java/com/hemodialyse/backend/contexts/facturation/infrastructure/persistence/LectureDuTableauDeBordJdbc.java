package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDuTableauDeBord;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois.RepartitionParCaisse;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.TableauDeBordDuMois.RepartitionParSituation;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture JDBC du tableau de bord de facturation (mêmes tables et mêmes requêtes que le legacy, slice F5) : {@code factures}
 * (totaux enregistrés), {@code agence} et {@code caisse_assurance}. Les jointures du référentiel ne filtrent
 * pas par centre, comme avant (KB-FAC-65). Les séances viennent de la Séance (ADR 0097), sous les DEUX définitions
 * de « facturée » conservées (KB-SEA-75, {@link SeancesDesFacturesFournies}) : l'en-tête compte le statut {@code FACTUREE} par date de séance, les répartitions comptent les séances
 * rattachées aux factures du mois. Le tri d'avant est conservé : lignes triées par code selon la base, puis tri STABLE (patients ou séances
 * décroissants).
 */
public class LectureDuTableauDeBordJdbc implements LectureDuTableauDeBord {

    private final JdbcTemplate jdbc;

    private final SeancesDesFacturesFournies seancesDesFactures;

    public LectureDuTableauDeBordJdbc(JdbcTemplate jdbc, SeancesDesFacturesFournies seancesDesFactures) {
        this.jdbc = jdbc;
        this.seancesDesFactures = seancesDesFactures;
    }

    @Override
    public TableauDeBordDuMois du(CentreId centre, YearMonth mois) {
        Date debut = Date.valueOf(mois.atDay(1));
        Date fin = Date.valueOf(mois.atEndOfMonth());
        Object[] criteres = {centre.valeur(), debut, fin};

        BigDecimal ttc = valeur("SELECT COALESCE(SUM(total_ttc), 0) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                BigDecimal.class, BigDecimal.ZERO, criteres);
        BigDecimal ht = valeur("SELECT COALESCE(SUM(total_ht), 0) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                BigDecimal.class, BigDecimal.ZERO, criteres);
        Long seances = seancesDesFactures.nombreDeSeancesFactureesEntre(centre.valeur(), mois.atDay(1), mois.atEndOfMonth());
        Long patients = valeur("SELECT COUNT(DISTINCT patient_id) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                Long.class, 0L, criteres);
        Long factures = valeur("SELECT COUNT(1) FROM factures WHERE center_id = ? AND date_facturation BETWEEN ? AND ?",
                Long.class, 0L, criteres);

        List<LigneDeFacture> parCaisseBrut = jdbc.query("""
                SELECT f.id AS facture_id, f.patient_id,
                       COALESCE(ca.code, 'INCONNU') AS insurance_code,
                       COALESCE(ca.nom, 'Inconnu') AS insurance_label
                FROM factures f
                LEFT JOIN agence a ON a.id = f.agence_id_snapshot
                LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                ORDER BY insurance_code ASC
                """,
                (rs, i) -> new LigneDeFacture(rs.getObject("facture_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getString("insurance_code"), rs.getString("insurance_label")),
                criteres);

        List<LigneDeFacture> parSituationBrut = jdbc.query("""
                SELECT f.id AS facture_id, f.patient_id,
                       COALESCE(f.patient_status_snapshot, 'INCONNU') AS status_code,
                       COALESCE(f.patient_status_snapshot, 'Inconnu') AS status_label
                FROM factures f
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                ORDER BY status_code ASC
                """,
                (rs, i) -> new LigneDeFacture(rs.getObject("facture_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getString("status_code"), rs.getString("status_label")),
                criteres);

        Map<UUID, Long> seancesParFacture = seancesDesFactures.nombreParFacture(centre.valeur(),
                parCaisseBrut.stream().map(LigneDeFacture::facture).toList());

        return new TableauDeBordDuMois(centre.valeur(), mois, ttc, ht, seances, patients, factures,
                repartitionParCaisse(parCaisseBrut, seancesParFacture), repartitionParSituation(parSituationBrut, seancesParFacture));
    }

    /** Ordre d'avant : patients décroissants, puis code croissant (code déjà trié par la base, tri stable). */
    private static List<RepartitionParCaisse> repartitionParCaisse(List<LigneDeFacture> lignes, Map<UUID, Long> seancesParFacture) {
        Map<List<String>, Groupe> groupes = grouper(lignes, seancesParFacture);
        List<RepartitionParCaisse> resultat = new ArrayList<>();
        groupes.forEach((cle, g) -> resultat.add(new RepartitionParCaisse(cle.get(0), cle.get(1), g.seances, g.patients.size())));
        resultat.sort(Comparator.comparingLong(RepartitionParCaisse::patients).reversed());
        return resultat;
    }

    /** Ordre d'avant : séances décroissantes, puis code croissant (code déjà trié par la base, tri stable). */
    private static List<RepartitionParSituation> repartitionParSituation(List<LigneDeFacture> lignes, Map<UUID, Long> seancesParFacture) {
        Map<List<String>, Groupe> groupes = grouper(lignes, seancesParFacture);
        List<RepartitionParSituation> resultat = new ArrayList<>();
        groupes.forEach((cle, g) -> resultat.add(new RepartitionParSituation(cle.get(0), cle.get(1), g.seances)));
        resultat.sort(Comparator.comparingLong(RepartitionParSituation::seances).reversed());
        return resultat;
    }

    private static Map<List<String>, Groupe> grouper(List<LigneDeFacture> lignes, Map<UUID, Long> seancesParFacture) {
        Map<List<String>, Groupe> groupes = new LinkedHashMap<>();
        for (LigneDeFacture ligne : lignes) {
            Groupe groupe = groupes.computeIfAbsent(List.of(ligne.code(), ligne.libelle()), cle -> new Groupe());
            if (ligne.patient() != null) {
                groupe.patients.add(ligne.patient());
            }
            groupe.seances += seancesParFacture.getOrDefault(ligne.facture(), 0L);
        }
        return groupes;
    }

    private static final class Groupe {
        private final Set<UUID> patients = new HashSet<>();
        private long seances;
    }

    private record LigneDeFacture(UUID facture, UUID patient, String code, String libelle) {
    }

    private <T> T valeur(String sql, Class<T> type, T parDefaut, Object[] criteres) {
        return Optional.ofNullable(jdbc.queryForObject(sql, type, criteres)).orElse(parDefaut);
    }
}
