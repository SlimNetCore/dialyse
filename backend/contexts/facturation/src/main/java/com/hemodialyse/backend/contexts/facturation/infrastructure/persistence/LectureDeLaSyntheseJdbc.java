package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Caisse;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Repartition;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lecture JDBC des données de la synthèse de facturation : caisses, répartitions et indicateurs des factures de la période,
 * totaux enregistrés (HT, TVA, TTC). Contrairement au tableau de bord, les jointures du référentiel filtrent par centre
 * (KB-FAC-65 reproduit). Valeurs vides ou blanches de caisse et de situation : « INCONNU ». Le nombre de séances de chaque facture vient de
 * la Séance ({@link SeancesDesFacturesFournies}, ADR 0097) : la requête ne touche plus la table des séances ; une séance n'ayant qu'une facture, le
 * nombre de séances d'un groupe de factures est la somme des nombres de ses factures, et le tri (patients, séances, code) est celui d'avant :
 * les lignes arrivent triées par code selon la base, puis un tri STABLE applique patients et séances décroissants.
 */
public class LectureDeLaSyntheseJdbc implements LectureDeLaSynthese {

    private final JdbcTemplate jdbc;

    private final SeancesDesFacturesFournies seancesDesFactures;

    public LectureDeLaSyntheseJdbc(JdbcTemplate jdbc, SeancesDesFacturesFournies seancesDesFactures) {
        this.jdbc = jdbc;
        this.seancesDesFactures = seancesDesFactures;
    }

    @Override
    public SyntheseMensuelle de(CentreId centre, LocalDate debut, LocalDate fin) {
        UUID id = centre.valeur();
        Date du = Date.valueOf(debut);
        Date au = Date.valueOf(fin);

        List<Caisse> caisses = jdbc.query("""
                SELECT DISTINCT
                       COALESCE(NULLIF(TRIM(ca.code), ''), 'INCONNU') AS caisse_code,
                       COALESCE(NULLIF(TRIM(ca.nom), ''), 'Inconnu') AS caisse_label
                FROM factures f
                LEFT JOIN agence a ON a.id = f.agence_id_snapshot AND a.center_id = f.center_id
                LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id AND ca.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                ORDER BY caisse_label ASC
                """,
                (rs, i) -> new Caisse(rs.getString("caisse_code"), rs.getString("caisse_label")), id, du, au);

        List<LigneDeFacture> parCaisseBrut = jdbc.query("""
                SELECT f.id AS facture_id, f.patient_id,
                       COALESCE(NULLIF(TRIM(ca.code), ''), 'INCONNU') AS bucket_code,
                       COALESCE(NULLIF(TRIM(ca.nom), ''), 'Inconnu') AS bucket_label
                FROM factures f
                LEFT JOIN agence a ON a.id = f.agence_id_snapshot AND a.center_id = f.center_id
                LEFT JOIN caisse_assurance ca ON ca.id = a.caisse_id AND ca.center_id = f.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                ORDER BY bucket_code ASC
                """,
                (rs, i) -> new LigneDeFacture(rs.getObject("facture_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getString("bucket_code"), rs.getString("bucket_label")), id, du, au);

        List<LigneDeFacture> parSituationBrut = jdbc.query("""
                SELECT f.id AS facture_id, f.patient_id,
                       COALESCE(NULLIF(TRIM(f.patient_status_snapshot), ''), 'INCONNU') AS bucket_code,
                       COALESCE(NULLIF(TRIM(f.patient_status_snapshot), ''), 'Inconnu') AS bucket_label
                FROM factures f
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                ORDER BY bucket_code ASC
                """,
                (rs, i) -> new LigneDeFacture(rs.getObject("facture_id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getString("bucket_code"), rs.getString("bucket_label")), id, du, au);

        Map<UUID, Long> seancesParFacture = seancesDesFactures.nombreParFacture(id,
                parCaisseBrut.stream().map(LigneDeFacture::facture).toList());

        List<Repartition> parCaisse = repartitions(parCaisseBrut, seancesParFacture);
        List<Repartition> parSituation = repartitions(parSituationBrut, seancesParFacture);

        Set<UUID> patients = new HashSet<>();
        parCaisseBrut.stream().map(LigneDeFacture::patient).filter(p -> p != null).forEach(patients::add);
        long seances = parCaisseBrut.stream().mapToLong(l -> seancesParFacture.getOrDefault(l.facture(), 0L)).sum();
        Indicateurs indicateurs = new Indicateurs(parCaisseBrut.stream().map(LigneDeFacture::facture).distinct().count(),
                patients.size(), seances);

        Totaux totaux = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_ht), 0) AS ht, COALESCE(SUM(total_tva), 0) AS tva, COALESCE(SUM(total_ttc), 0) AS ttc
                FROM factures
                WHERE center_id = ?
                  AND date_facturation BETWEEN ? AND ?
                """,
                (rs, i) -> new Totaux(rs.getBigDecimal("ht"), rs.getBigDecimal("tva"), rs.getBigDecimal("ttc")), id, du, au);

        return new SyntheseMensuelle(id, debut, fin, caisses, parCaisse, parSituation, indicateurs.factures(),
                indicateurs.patients(), indicateurs.seances(), totaux.ht(), totaux.tva(), totaux.ttc());
    }

    /** Regroupe les factures par (code, libellé) dans l'ordre du code reçu de la base, puis tri stable patients puis séances décroissants. */
    private static List<Repartition> repartitions(List<LigneDeFacture> lignes, Map<UUID, Long> seancesParFacture) {
        Map<List<String>, Groupe> groupes = new LinkedHashMap<>();
        for (LigneDeFacture ligne : lignes) {
            Groupe groupe = groupes.computeIfAbsent(List.of(ligne.code(), ligne.libelle()), cle -> new Groupe());
            if (ligne.patient() != null) {
                groupe.patients.add(ligne.patient());
            }
            groupe.seances += seancesParFacture.getOrDefault(ligne.facture(), 0L);
        }
        List<Repartition> resultat = new ArrayList<>();
        groupes.forEach((cle, groupe) -> resultat.add(new Repartition(cle.get(0), cle.get(1), groupe.patients.size(), groupe.seances)));
        resultat.sort(Comparator.comparingLong(Repartition::patients).reversed().thenComparing(Comparator.comparingLong(Repartition::seances).reversed()));
        return resultat;
    }

    private static final class Groupe {
        private final Set<UUID> patients = new HashSet<>();
        private long seances;
    }

    private record LigneDeFacture(UUID facture, UUID patient, String code, String libelle) {
    }

    private record Indicateurs(long factures, long patients, long seances) {
    }

    private record Totaux(BigDecimal ht, BigDecimal tva, BigDecimal ttc) {
    }
}
