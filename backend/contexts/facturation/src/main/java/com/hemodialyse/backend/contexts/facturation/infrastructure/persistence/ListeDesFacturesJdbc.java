package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.consultation.FactureListee;
import com.hemodialyse.backend.contexts.facturation.application.consultation.ListeDesFactures;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.SituationDeFacture;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients.PatientAFacturer;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.AgencePubliee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.CaissePubliee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.CentrePayeurPublie;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des factures émises pour leur consultation (RG-FAC-046, 047) : {@code factures} avec, en sous-requêtes bornées au centre, la somme
 * signée des {@code facture_reglements} et la somme des quantités figées des {@code facture_lignes}. La fiche du patient vient de
 * {@link LotsDePatients} (bornée au centre, par lots de 500 ; sinon instantané de la facture) et la caisse de la chaîne des payeurs de
 * {@link PayeursDuCentre} (instantané figé, sinon payeur courant du patient), avec les mêmes repli que {@code SituationsDesFacturesJdbc}.
 * Aucun SQL sur les tables d'un autre contexte (séances, patients, payeurs). Lecture seule.
 */
public class ListeDesFacturesJdbc implements ListeDesFactures {

    private static final int LOT_DE_PATIENTS = 500;

    private static final String SELECTION = """
            SELECT f.id, f.numero_facture, f.patient_id, f.numero_immatriculation_snapshot, f.patient_full_name,
                   f.centre_payeur_id_snapshot, f.agence_id_snapshot, f.period_start, f.period_end, f.date_facturation,
                   f.tva_rate, f.total_ht, f.total_tva, f.total_ttc,
                   (SELECT COALESCE(SUM(fr.montant), 0) FROM facture_reglements fr
                     WHERE fr.facture_id = f.id AND fr.center_id = f.center_id) AS montant_regle,
                   (SELECT COALESCE(SUM(fl.seance_count), 0) FROM facture_lignes fl
                     WHERE fl.facture_id = f.id AND fl.center_id = f.center_id) AS nombre_de_seances
            FROM factures f
            """;

    private static final String TRI = " ORDER BY f.date_facturation DESC, f.numero_facture DESC, f.id";

    private final JdbcTemplate jdbc;
    private final PayeursDuCentre annuaire;
    private final LotsDePatients patients;

    public ListeDesFacturesJdbc(JdbcTemplate jdbc, PayeursDuCentre annuaire, LotsDePatients patients) {
        this.jdbc = jdbc;
        this.annuaire = annuaire;
        this.patients = patients;
    }

    private record Brute(UUID facture, String numero, UUID patient, String numeroInstantane, String nomInstantane,
                         UUID centrePayeurInstantane, UUID agenceFigee, LocalDate periodeDebut, LocalDate periodeFin, LocalDate date,
                         BigDecimal tauxTva, BigDecimal ht, BigDecimal tva, BigDecimal ttc, BigDecimal regle, long nombreDeSeances) {
    }

    private static Brute brute(ResultSet rs) throws SQLException {
        return new Brute(rs.getObject("id", UUID.class), rs.getString("numero_facture"), rs.getObject("patient_id", UUID.class),
                rs.getString("numero_immatriculation_snapshot"), rs.getString("patient_full_name"),
                rs.getObject("centre_payeur_id_snapshot", UUID.class), rs.getObject("agence_id_snapshot", UUID.class),
                rs.getObject("period_start", LocalDate.class), rs.getObject("period_end", LocalDate.class),
                rs.getObject("date_facturation", LocalDate.class), rs.getBigDecimal("tva_rate"), rs.getBigDecimal("total_ht"),
                rs.getBigDecimal("total_tva"), rs.getBigDecimal("total_ttc"), rs.getBigDecimal("montant_regle"),
                rs.getLong("nombre_de_seances"));
    }

    @Override
    public List<FactureListee> entre(CentreId centre, LocalDate du, LocalDate au) {
        List<Brute> brutes = jdbc.query(SELECTION + " WHERE f.center_id = ? AND f.date_facturation BETWEEN ? AND ?" + TRI,
                (rs, i) -> brute(rs), centre.valeur(), Date.valueOf(du), Date.valueOf(au));
        return lister(centre.valeur(), brutes);
    }

    @Override
    public Optional<FactureListee> deLaFacture(CentreId centre, UUID facture) {
        List<Brute> brutes = jdbc.query(SELECTION + " WHERE f.center_id = ? AND f.id = ?", (rs, i) -> brute(rs), centre.valeur(),
                facture);
        return lister(centre.valeur(), brutes).stream().findFirst();
    }

    @Override
    public List<LigneFacture> lignes(CentreId centre, UUID facture) {
        return jdbc.query("""
                        SELECT fl.forfait_id, fl.forfait_label, fl.unit_price_ht, fl.seance_count
                        FROM facture_lignes fl
                        JOIN factures f ON f.id = fl.facture_id AND f.center_id = fl.center_id
                        WHERE fl.center_id = ? AND fl.facture_id = ?
                        ORDER BY fl.forfait_label ASC, fl.unit_price_ht ASC, fl.id
                        """,
                (rs, i) -> new LigneFacture(rs.getObject("forfait_id", UUID.class), rs.getString("forfait_label"),
                        rs.getBigDecimal("unit_price_ht"), rs.getInt("seance_count")),
                centre.valeur(), facture);
    }

    private List<FactureListee> lister(UUID centre, List<Brute> brutes) {
        if (brutes.isEmpty()) return List.of();
        Map<UUID, PatientAFacturer> fiches = fiches(centre, brutes);
        Chaine chaine = chaine(centre);
        return brutes.stream().map(b -> facture(b, fiches.get(b.patient()), chaine)).toList();
    }

    private Map<UUID, PatientAFacturer> fiches(UUID centre, List<Brute> brutes) {
        List<UUID> demandes = brutes.stream().map(Brute::patient).filter(Objects::nonNull).distinct().toList();
        Map<UUID, PatientAFacturer> fiches = new HashMap<>();
        for (int debut = 0; debut < demandes.size(); debut += LOT_DE_PATIENTS) {
            List<UUID> lot = demandes.subList(debut, Math.min(debut + LOT_DE_PATIENTS, demandes.size()));
            patients.aFacturerDuCentre(centre, lot).forEach(p -> fiches.put(p.patient(), p));
        }
        return fiches;
    }

    private record Chaine(Map<UUID, CentrePayeurPublie> centresPayeurs, Map<UUID, AgencePubliee> agences,
                          Map<UUID, CaissePubliee> caisses) {
    }

    private Chaine chaine(UUID centre) {
        Map<UUID, CentrePayeurPublie> centresPayeurs = new HashMap<>();
        annuaire.centresPayeurs(centre).forEach(c -> centresPayeurs.put(c.id(), c));
        Map<UUID, AgencePubliee> agences = new HashMap<>();
        annuaire.agences(centre).forEach(a -> agences.put(a.id(), a));
        Map<UUID, CaissePubliee> caisses = new HashMap<>();
        annuaire.caisses(centre).forEach(c -> caisses.put(c.id(), c));
        return new Chaine(centresPayeurs, agences, caisses);
    }

    private static FactureListee facture(Brute b, PatientAFacturer fiche, Chaine chaine) {
        String numeroAssurance = fiche != null && fiche.numeroAssurance() != null ? fiche.numeroAssurance()
                : b.numeroInstantane() != null ? b.numeroInstantane() : "-";
        String patientNom = fiche != null && fiche.nom() != null ? fiche.nom() : b.nomInstantane() != null ? b.nomInstantane() : "-";
        String patientPrenom = fiche != null && fiche.prenom() != null ? fiche.prenom() : "";
        UUID centrePayeurCle = b.centrePayeurInstantane() != null ? b.centrePayeurInstantane()
                : fiche == null ? null : fiche.centrePayeur();
        CentrePayeurPublie cp = centrePayeurCle == null ? null : chaine.centresPayeurs().get(centrePayeurCle);
        UUID agenceId = b.agenceFigee() != null ? b.agenceFigee() : cp == null ? null : cp.agence();
        AgencePubliee ag = agenceId == null ? null : chaine.agences().get(agenceId);
        CaissePubliee ca = ag == null || ag.caisse() == null ? null : chaine.caisses().get(ag.caisse());
        BigDecimal ttc = arrondi(b.ttc());
        BigDecimal regle = arrondi(b.regle());
        SituationDeFacture situation = SituationDeFacture.evaluer(ttc, regle);
        return new FactureListee(b.facture(), b.numero(), b.date(), b.periodeDebut(), b.periodeFin(), b.patient(), numeroAssurance,
                patientNom, patientPrenom, ca == null ? null : ca.id(), ca == null || ca.nom() == null ? "Non renseignee" : ca.nom(),
                b.nombreDeSeances(), arrondi(b.tauxTva()), arrondi(b.ht()), arrondi(b.tva()), ttc, regle, arrondi(situation.reste()),
                arrondi(situation.tropPercu()), situation.etat());
    }

    private static BigDecimal arrondi(BigDecimal montant) {
        return montant == null ? BigDecimal.ZERO.setScale(2) : montant.setScale(2, RoundingMode.HALF_UP);
    }
}
