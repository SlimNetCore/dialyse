package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.reglement.CriteresDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.LigneDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PageDeSituations;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.LotsDePatients.PatientAFacturer;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des factures avec leur situation de règlement : {@code factures} jointe à {@code facture_reglements} (somme signée). La fiche
 * du patient (numéro d'assurance, nom, prénom, centre payeur courant) vient de la lecture publiée {@link LotsDePatients} de Patient et
 * couverture, BORNÉE au centre de la facture et appliquée en mémoire (LEC-PAT-GMAO (3), ADR 0181) : un patient d'un autre centre ou
 * inconnu n'est pas lu, l'instantané de la facture s'applique. La chaîne des payeurs (instantané de la facture d'abord, sinon payeur
 * courant du patient, puis agence et caisse) vient de l'annuaire publié {@link PayeursDuCentre} (PL8b, ADR 0149) : filtres et pagination
 * s'appliquent en mémoire, après le tri de la base. Tri {@code date_facturation DESC, numero_facture DESC, id} : l'identifiant de la
 * facture départage (KB-FAC-49 corrigé ; la base interdit deux factures de même numéro dans un centre, donc aucun ordre n'en est modifié).
 * La situation est calculée à chaque lecture par {@link SituationDeFacture}.
 */
public class SituationsDesFacturesJdbc implements SituationsDesFactures {

    private static final int LOT_DE_PATIENTS = 500;

    private static final String JOINTURES = """
            FROM factures f
            LEFT JOIN facture_reglements fr ON fr.facture_id = f.id AND fr.center_id = f.center_id
            """;

    private static final String REGROUPEMENT = """
            GROUP BY f.id, f.numero_facture, f.patient_id, f.numero_immatriculation_snapshot, f.patient_full_name,
                     f.centre_payeur_id_snapshot, f.agence_id_snapshot, f.date_facturation, f.total_ttc
            """;

    /**
     * PL8b : le centre payeur de l'instantané de la facture et l'agence figée sont lus en identités brutes ; la fiche du patient est lue
     * par {@link LotsDePatients}, la chaîne (centre payeur, agence, caisse) est résolue en mémoire par l'annuaire des payeurs, bornée au centre.
     */
    private static final String SELECTION = """
            SELECT f.id AS facture_id,
                   f.numero_facture,
                   f.patient_id,
                   f.numero_immatriculation_snapshot AS numero_instantane,
                   f.patient_full_name AS nom_instantane,
                   f.centre_payeur_id_snapshot AS centre_payeur_instantane,
                   f.agence_id_snapshot AS agence_figee,
                   f.date_facturation,
                   f.total_ttc AS montant_facture,
                   COALESCE(SUM(fr.montant), 0) AS montant_regle,
                   COUNT(fr.id) AS payment_count,
                   (SELECT fr2.code_reglement FROM facture_reglements fr2
                    WHERE fr2.facture_id = f.id AND fr2.center_id = f.center_id
                    ORDER BY fr2.created_at DESC LIMIT 1) AS latest_code_reglement
            """;

    private static final String TRI = " ORDER BY f.date_facturation DESC, f.numero_facture DESC, f.id";

    private final JdbcTemplate jdbc;

    private final PayeursDuCentre annuaire;

    private final LotsDePatients patients;

    public SituationsDesFacturesJdbc(JdbcTemplate jdbc, PayeursDuCentre annuaire, LotsDePatients patients) {
        this.jdbc = jdbc;
        this.annuaire = annuaire;
        this.patients = patients;
    }

    /** Une ligne lue sur les factures, avant la lecture de la fiche du patient et la résolution de sa chaîne de payeurs. */
    private record Brute(UUID facture, String numero, UUID patient, String numeroInstantane, String nomInstantane,
                         UUID centrePayeurInstantane, UUID agenceFigee, LocalDate date, BigDecimal montantFacture,
                         BigDecimal montantRegle, String dernierCode, long nombreDeReglements) {
    }

    private static Brute brute(ResultSet rs) throws SQLException {
        return new Brute(rs.getObject("facture_id", UUID.class), rs.getString("numero_facture"), rs.getObject("patient_id", UUID.class),
                rs.getString("numero_instantane"), rs.getString("nom_instantane"),
                rs.getObject("centre_payeur_instantane", UUID.class), rs.getObject("agence_figee", UUID.class),
                rs.getObject("date_facturation", LocalDate.class), rs.getBigDecimal("montant_facture"),
                rs.getBigDecimal("montant_regle"), rs.getString("latest_code_reglement"), rs.getLong("payment_count"));
    }

    /** Les fiches des patients des factures lues, DU CENTRE seulement (lecture publiée bornée, par lots), par identifiant de patient. */
    private Map<UUID, PatientAFacturer> fiches(UUID centre, List<Brute> brutes) {
        List<UUID> demandes = brutes.stream().map(Brute::patient).filter(Objects::nonNull).distinct().toList();
        Map<UUID, PatientAFacturer> fiches = new HashMap<>();
        for (int debut = 0; debut < demandes.size(); debut += LOT_DE_PATIENTS) {
            List<UUID> lot = demandes.subList(debut, Math.min(debut + LOT_DE_PATIENTS, demandes.size()));
            patients.aFacturerDuCentre(centre, lot).forEach(p -> fiches.put(p.patient(), p));
        }
        return fiches;
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
    private static LigneDeSituation ligne(Brute b, PatientAFacturer fiche, Chaine chaine) {
        // même COALESCE que la jointure d'avant : la fiche (si le patient est du centre), puis l'instantané de la facture, puis « - »
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
        BigDecimal facture = arrondi(b.montantFacture());
        BigDecimal regle = arrondi(b.montantRegle());
        SituationDeFacture situation = SituationDeFacture.evaluer(facture, regle);
        return new LigneDeSituation(b.facture(), b.numero(), numeroAssurance, patientNom, patientPrenom,
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
        Map<UUID, PatientAFacturer> fiches = fiches(centre, brutes);
        List<LigneDeSituation> lignes = new ArrayList<>();
        for (Brute brute : brutes) {
            LigneDeSituation ligne = ligne(brute, fiches.get(brute.patient()), chaine);
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
                (rs, i) -> brute(rs), centre.valeur(), facture).stream().findFirst()
                .map(b -> ligne(b, fiches(centre.valeur(), List.of(b)).get(b.patient()), chaine(centre.valeur())));
    }
}
