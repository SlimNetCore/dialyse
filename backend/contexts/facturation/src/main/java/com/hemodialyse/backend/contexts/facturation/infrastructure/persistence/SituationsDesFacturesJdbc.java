package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.reglement.CriteresDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.LigneDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PageDeSituations;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.AgencePubliee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.CaissePubliee;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.PayeursDuCentre.CentrePayeurPublie;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des factures avec leur situation de règlement : {@code factures} jointe à {@code facture_reglements} (somme signée), au
 * patient ; la chaîne des payeurs (instantané de la facture d'abord, sinon payeur courant du patient, puis agence et caisse) vient de
 * l'annuaire publié {@link PayeursDuCentre} (PL8b, ADR 0149) : filtres et pagination s'appliquent en mémoire, après le tri de la base.
 * Tri {@code date_facturation DESC, numero_facture DESC} sans départage (KB-FAC-49 reproduit). La situation est calculée à chaque
 * lecture par {@link SituationDeFacture}.
 */
public class SituationsDesFacturesJdbc implements SituationsDesFactures {

    private static final String JOINTURES = """
            FROM factures f
            LEFT JOIN patients p ON p.id = f.patient_id AND p.center_id = f.center_id
            LEFT JOIN facture_reglements fr ON fr.facture_id = f.id AND fr.center_id = f.center_id
            """;

    private static final String REGROUPEMENT = """
            GROUP BY f.id, f.numero_facture, p.numero_assurance, f.numero_immatriculation_snapshot,
                     p.nom, f.patient_full_name, p.prenom,
                     f.centre_payeur_id_snapshot, p.centre_payeur_id, f.agence_id_snapshot,
                     f.date_facturation, f.total_ttc
            """;

    /**
     * PL8b : le centre payeur (instantané de la facture d'abord, sinon payeur courant du patient) et l'agence figée sont lus en
     * identités brutes ; la chaîne (centre payeur, agence, caisse) est résolue en mémoire par l'annuaire des payeurs, bornée au centre.
     */
    private static final String SELECTION = """
            SELECT f.id AS facture_id,
                   f.numero_facture,
                   COALESCE(p.numero_assurance, f.numero_immatriculation_snapshot, '-') AS numero_assurance,
                   COALESCE(p.nom, f.patient_full_name, '-') AS patient_nom,
                   COALESCE(p.prenom, '') AS patient_prenom,
                   COALESCE(f.centre_payeur_id_snapshot, p.centre_payeur_id) AS centre_payeur_cle,
                   f.agence_id_snapshot AS agence_figee,
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

    private final PayeursDuCentre annuaire;

    public SituationsDesFacturesJdbc(JdbcTemplate jdbc, PayeursDuCentre annuaire) {
        this.jdbc = jdbc;
        this.annuaire = annuaire;
    }

    /** Une ligne lue sur les factures, avant la résolution de sa chaîne de payeurs. */
    private record Brute(UUID facture, String numero, String numeroAssurance, String patientNom, String patientPrenom,
                         UUID centrePayeurCle, UUID agenceFigee, LocalDate date, BigDecimal montantFacture, BigDecimal montantRegle,
                         String dernierCode, long nombreDeReglements) {
    }

    private static Brute brute(ResultSet rs) throws SQLException {
        return new Brute(rs.getObject("facture_id", UUID.class), rs.getString("numero_facture"), rs.getString("numero_assurance"),
                rs.getString("patient_nom"), rs.getString("patient_prenom"), rs.getObject("centre_payeur_cle", UUID.class),
                rs.getObject("agence_figee", UUID.class), rs.getObject("date_facturation", LocalDate.class),
                rs.getBigDecimal("montant_facture"), rs.getBigDecimal("montant_regle"), rs.getString("latest_code_reglement"),
                rs.getLong("payment_count"));
    }

    /** La chaîne des payeurs du centre, lue une fois par requête (trois lectures publiées, bornées au centre). */
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

    /**
     * Le centre payeur est celui de l'instantané sinon du patient ; l'agence est l'instantané figé sinon celle du centre payeur retenu ;
     * la caisse est celle de l'agence. Un étage inconnu du centre rompt la chaîne (libellé « Non renseigne(e) »).
     */
    private static LigneDeSituation ligne(Brute b, Chaine chaine) {
        CentrePayeurPublie cp = b.centrePayeurCle() == null ? null : chaine.centresPayeurs().get(b.centrePayeurCle());
        UUID agenceId = b.agenceFigee() != null ? b.agenceFigee() : cp == null ? null : cp.agence();
        AgencePubliee ag = agenceId == null ? null : chaine.agences().get(agenceId);
        CaissePubliee ca = ag == null || ag.caisse() == null ? null : chaine.caisses().get(ag.caisse());
        BigDecimal facture = arrondi(b.montantFacture());
        BigDecimal regle = arrondi(b.montantRegle());
        SituationDeFacture situation = SituationDeFacture.evaluer(facture, regle);
        return new LigneDeSituation(b.facture(), b.numero(), b.numeroAssurance(), b.patientNom(), b.patientPrenom(),
                ca == null ? null : ca.id(), ca == null || ca.nom() == null ? "Non renseignee" : ca.nom(),
                ag == null ? null : ag.id(), ag == null || ag.nom() == null ? "Non renseignee" : ag.nom(),
                cp == null ? null : cp.id(), cp == null || cp.nom() == null ? "Non renseigne" : cp.nom(),
                b.date(), facture, regle, arrondi(situation.reste()), arrondi(situation.tropPercu()), situation.etat(),
                situation.solde(), b.dernierCode(), b.nombreDeReglements());
    }

    private static BigDecimal arrondi(BigDecimal montant) {
        return montant == null ? BigDecimal.ZERO.setScale(2) : montant.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public PageDeSituations rechercher(CriteresDeSituation criteres, int page, int taille) {
        List<LigneDeSituation> filtrees = toutes(criteres);
        // LIMIT ? OFFSET ? de la base, dans l'ordre du tri conservé (PL8b : le filtre sur la chaîne se fait en mémoire, donc la page aussi)
        long debut = Math.min((long) page * taille, filtrees.size());
        long fin = Math.min(debut + Math.max(taille, 0), filtrees.size());
        return new PageDeSituations(new ArrayList<>(filtrees.subList((int) debut, (int) fin)), filtrees.size(), page, taille);
    }

    @Override
    public List<LigneDeSituation> toutes(CriteresDeSituation criteres) {
        UUID centre = criteres.centre().valeur();
        List<Brute> brutes = jdbc.query(SELECTION + JOINTURES + " WHERE f.center_id = ? AND f.date_facturation BETWEEN ? AND ? "
                + REGROUPEMENT + TRI, (rs, i) -> brute(rs), centre, Date.valueOf(criteres.debut()), Date.valueOf(criteres.fin()));
        Chaine chaine = chaine(centre);
        List<LigneDeSituation> lignes = new ArrayList<>();
        for (Brute brute : brutes) {
            LigneDeSituation ligne = ligne(brute, chaine);
            if (criteres.caisse() != null && !criteres.caisse().equals(ligne.caisseId())) continue;
            if (criteres.agence() != null && !criteres.agence().equals(ligne.agenceId())) continue;
            if (criteres.centrePayeur() != null && !criteres.centrePayeur().equals(ligne.centrePayeurId())) continue;
            lignes.add(ligne);
        }
        return lignes;
    }

    @Override
    public Optional<LigneDeSituation> deLaFacture(CentreId centre, UUID facture) {
        return jdbc.query(SELECTION + JOINTURES + " WHERE f.center_id = ? AND f.id = ? " + REGROUPEMENT,
                (rs, i) -> brute(rs), centre.valeur(), facture).stream().findFirst().map(b -> ligne(b, chaine(centre.valeur())));
    }
}
