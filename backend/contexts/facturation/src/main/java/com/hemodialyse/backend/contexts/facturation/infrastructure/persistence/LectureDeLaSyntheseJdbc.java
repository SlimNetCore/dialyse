package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.pilotage.CaissesDesAgences;
import com.hemodialyse.backend.contexts.facturation.application.pilotage.LectureDeLaSynthese;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SeancesDesFacturesFournies;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Caisse;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Ligne;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.SyntheseMensuelle.Repartition;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Lecture JDBC des données de la synthèse de facturation : caisses, répartitions, tableau forfait x caisse, indicateurs des factures de la
 * période, totaux enregistrés (HT, TVA, TTC). Les tables des payeurs ne sont plus jointes (RF3c-J1) : la caisse de l'agence figée sur la
 * facture vient de l'annuaire ({@link CaissesDesAgences}, bornée au centre, KB-FAC-65 reproduit). Code de caisse NORMALISÉ (espaces retirés,
 * majuscules) : un code blanc, une agence inconnue ou d'un autre centre tombent dans l'unique colonne « INCONNU » / « Inconnu » (KB-PL8-2
 * corrigé). Le nombre de séances de chaque facture vient de la Séance ({@link SeancesDesFacturesFournies}, ADR 0097) ; tri des répartitions :
 * code, puis patients et séances décroissants (tri STABLE). Tableau : part de TTC d'une ligne = HT de la ligne x TTC / HT enregistrés de la
 * facture ; facture à HT nul : TTC réparti à parts égales entre ses lignes (KB-PL8-5 corrigé) ; cellules arrondies à 2 décimales.
 */
public class LectureDeLaSyntheseJdbc implements LectureDeLaSynthese {

    private static final String INCONNU = "INCONNU";
    private static final MathContext PRECISION = new MathContext(30, RoundingMode.HALF_UP);

    private final JdbcTemplate jdbc;

    private final SeancesDesFacturesFournies seancesDesFactures;
    private final CaissesDesAgences caissesDesAgences;

    public LectureDeLaSyntheseJdbc(JdbcTemplate jdbc, SeancesDesFacturesFournies seancesDesFactures, CaissesDesAgences caissesDesAgences) {
        this.jdbc = jdbc;
        this.seancesDesFactures = seancesDesFactures;
        this.caissesDesAgences = caissesDesAgences;
    }

    @Override
    public SyntheseMensuelle de(CentreId centre, LocalDate debut, LocalDate fin) {
        UUID id = centre.valeur();
        Date du = Date.valueOf(debut);
        Date au = Date.valueOf(fin);

        List<FactureLue> factures = jdbc.query("""
                SELECT id, patient_id, agence_id_snapshot, patient_status_snapshot, total_ht, total_ttc
                FROM factures
                WHERE center_id = ?
                  AND date_facturation BETWEEN ? AND ?
                """,
                (rs, i) -> new FactureLue(rs.getObject("id", UUID.class), rs.getObject("patient_id", UUID.class),
                        rs.getObject("agence_id_snapshot", UUID.class), rs.getString("patient_status_snapshot"),
                        rs.getBigDecimal("total_ht"), rs.getBigDecimal("total_ttc")), id, du, au);

        Map<UUID, Caisse> caissesParAgence = new HashMap<>();
        Map<UUID, CaissesDesAgences.Caisse> annuaire = factures.isEmpty() ? Map.of() : caissesDesAgences.parAgence(id);
        for (FactureLue facture : factures) {
            caissesParAgence.put(facture.id(), caisseDe(annuaire.get(facture.agence())));
        }

        List<Caisse> caisses = factures.stream().map(f -> caissesParAgence.get(f.id())).distinct()
                .sorted(Comparator.comparing(Caisse::libelle)).toList();
        List<LigneDeFacture> parCaisseBrut = factures.stream()
                .map(f -> new LigneDeFacture(f.id(), f.patient(), caissesParAgence.get(f.id()).code(), caissesParAgence.get(f.id()).libelle()))
                .sorted(Comparator.comparing(LigneDeFacture::code)).toList();
        List<LigneDeFacture> parSituationBrut = factures.stream()
                .map(f -> new LigneDeFacture(f.id(), f.patient(), situation(f.situation(), INCONNU), situation(f.situation(), "Inconnu")))
                .sorted(Comparator.comparing(LigneDeFacture::code)).toList();

        // PL8, KB-PL8-5 : seules les séances au statut FACTUREE sont comptées (critère conservateur, à confirmer métiers avant le 2026-12-15)
        Map<UUID, Long> seancesParFacture = seancesDesFactures.nombreParFactureAuStatutFacturee(id,
                parCaisseBrut.stream().map(LigneDeFacture::facture).toList());

        List<Repartition> parCaisse = repartitions(parCaisseBrut, seancesParFacture);
        List<Repartition> parSituation = repartitions(parSituationBrut, seancesParFacture);

        Set<UUID> patients = new HashSet<>();
        parCaisseBrut.stream().map(LigneDeFacture::patient).filter(p -> p != null).forEach(patients::add);
        long seances = parCaisseBrut.stream().mapToLong(l -> seancesParFacture.getOrDefault(l.facture(), 0L)).sum();
        long nombreDeFactures = parCaisseBrut.stream().map(LigneDeFacture::facture).distinct().count();

        Totaux totaux = jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_ht), 0) AS ht, COALESCE(SUM(total_tva), 0) AS tva, COALESCE(SUM(total_ttc), 0) AS ttc
                FROM factures
                WHERE center_id = ?
                  AND date_facturation BETWEEN ? AND ?
                """,
                (rs, i) -> new Totaux(rs.getBigDecimal("ht"), rs.getBigDecimal("tva"), rs.getBigDecimal("ttc")), id, du, au);

        return new SyntheseMensuelle(id, debut, fin, caisses, parCaisse, parSituation, nombreDeFactures,
                patients.size(), seances, totaux.ht(), totaux.tva(), totaux.ttc(), tableau(id, du, au, factures, caissesParAgence));
    }

    /** Code normalisé (retiré des espaces, majuscules) ; vide : « INCONNU » et libellé « Inconnu ». */
    private static Caisse caisseDe(CaissesDesAgences.Caisse caisse) {
        String code = caisse == null || caisse.code() == null ? "" : caisse.code().trim().toUpperCase(Locale.ROOT);
        if (code.isEmpty() || INCONNU.equals(code)) {
            return new Caisse(INCONNU, "Inconnu");
        }
        String nom = caisse.nom() == null ? "" : caisse.nom().trim();
        return new Caisse(code, nom.isEmpty() ? "Inconnu" : nom);
    }

    private static String situation(String brut, String parDefaut) {
        return brut == null || brut.trim().isEmpty() ? parDefaut : brut.trim();
    }

    /** Tableau forfait x caisse, trié par libellé de forfait (ordre des caractères, indépendant du SGBD) puis par code de caisse. */
    private List<Ligne> tableau(UUID centre, Date du, Date au, List<FactureLue> factures, Map<UUID, Caisse> caisseDeLaFacture) {
        if (factures.isEmpty()) {
            return List.of();
        }
        Map<UUID, FactureLue> parId = new HashMap<>();
        factures.forEach(f -> parId.put(f.id(), f));
        Map<UUID, List<LigneLue>> parFacture = new LinkedHashMap<>();
        jdbc.query("""
                SELECT fl.facture_id, fl.forfait_label, fl.line_ht
                FROM facture_lignes fl
                INNER JOIN factures f ON f.id = fl.facture_id AND f.center_id = fl.center_id
                WHERE f.center_id = ?
                  AND f.date_facturation BETWEEN ? AND ?
                """, rs -> {
            UUID facture = rs.getObject("facture_id", UUID.class);
            BigDecimal ht = rs.getBigDecimal("line_ht");
            parFacture.computeIfAbsent(facture, k -> new ArrayList<>())
                    .add(new LigneLue(rs.getString("forfait_label"), ht == null ? BigDecimal.ZERO : ht));
        }, centre, du, au);

        Map<List<String>, BigDecimal[]> cellules = new TreeMap<>((a, b) -> {
            int c = a.get(0).compareTo(b.get(0));
            return c != 0 ? c : a.get(1).compareTo(b.get(1));
        });
        parFacture.forEach((idFacture, lignes) -> {
            FactureLue facture = parId.get(idFacture);
            BigDecimal totalHt = facture.ht() == null ? BigDecimal.ZERO : facture.ht();
            BigDecimal totalTtc = facture.ttc() == null ? BigDecimal.ZERO : facture.ttc();
            String caisse = caisseDeLaFacture.get(idFacture).code();
            for (LigneLue ligne : lignes) {
                BigDecimal part = totalHt.signum() == 0
                        ? totalTtc.divide(BigDecimal.valueOf(lignes.size()), PRECISION)
                        : ligne.ht().multiply(totalTtc).divide(totalHt, PRECISION);
                BigDecimal[] cellule = cellules.computeIfAbsent(
                        List.of(ligne.forfait() == null ? "" : ligne.forfait(), caisse), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                cellule[0] = cellule[0].add(ligne.ht());
                cellule[1] = cellule[1].add(part);
            }
        });
        List<Ligne> resultat = new ArrayList<>();
        cellules.forEach((cle, somme) -> resultat.add(new Ligne(cle.get(0), cle.get(1),
                // arrondi à la paire (HALF_EVEN) : celui de l'affichage historique du PDF (0,545 donne 0,54)
                somme[0].setScale(2, RoundingMode.HALF_EVEN), somme[1].setScale(2, RoundingMode.HALF_EVEN))));
        return resultat;
    }

    /** Regroupe les factures par (code, libellé) dans l'ordre du code, puis tri stable patients puis séances décroissants. */
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

    private record FactureLue(UUID id, UUID patient, UUID agence, String situation, BigDecimal ht, BigDecimal ttc) {
    }

    private record LigneLue(String forfait, BigDecimal ht) {
    }

    private record LigneDeFacture(UUID facture, UUID patient, String code, String libelle) {
    }

    private record Totaux(BigDecimal ht, BigDecimal tva, BigDecimal ttc) {
    }
}
