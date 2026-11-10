package com.hemodialyse.backend.contexts.facturation.infrastructure.persistence;

import com.hemodialyse.backend.contexts.facturation.application.payeur.AgenceDuPayeur;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Soldes d'ouverture (JDBC) : facture de reprise hors séquence de numérotation, sans TVA (TTC = HT), une ligne
 * « Solde d'ouverture » et, le cas échéant, le montant déjà réglé saisi comme règlement de reprise. Le rejeu remplace sur
 * place ; le comportement est celui de l'ancien adaptateur de la Migration (slice F6).
 */
public class RepriseDesSoldesJdbc implements RepriseDesSoldes {

    private final JdbcTemplate jdbc;

    private final AgenceDuPayeur payeurs;

    public RepriseDesSoldesJdbc(JdbcTemplate jdbc, AgenceDuPayeur payeurs) {
        this.jdbc = jdbc;
        this.payeurs = payeurs;
    }

    @Override
    public Optional<UUID> factureDuNumero(UUID centre, String numero) {
        return jdbc.queryForList("SELECT id FROM factures WHERE center_id = ? AND numero_facture = ?", UUID.class,
                centre, numero).stream().findFirst();
    }

    @Override
    public long reglementsHorsReprise(UUID centre, UUID facture) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM facture_reglements WHERE facture_id = ? AND center_id = ? "
                + "AND (saisi_par IS NULL OR saisi_par <> ?)", Long.class, facture, centre, AUTEUR_DE_LA_REPRISE);
        return n == null ? 0 : n;
    }

    @Override
    public UUID ouvrir(UUID centre, SoldeDOuverture solde) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO factures (id, center_id, patient_id, numero_facture, patient_code, patient_full_name, "
                        + "patient_status_snapshot, numero_immatriculation_snapshot, centre_payeur_id_snapshot, agence_id_snapshot, "
                        + "period_start, period_end, date_facturation, tva_rate, total_ht, total_tva, total_ttc, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 0, ?, ?)",
                id, centre, solde.patient(), solde.numero(), solde.codePatient(), solde.nomComplet(), solde.statutDuPatient(),
                solde.numeroImmatriculation(), solde.centrePayeur(), agenceDuPayeur(centre, solde.centrePayeur()),
                solde.debutDePeriode(), solde.finDePeriode(), solde.dateDeFacture(), solde.montantTtc(), solde.montantTtc(),
                OffsetDateTime.now());
        poserLeDetail(centre, id, solde);
        return id;
    }

    @Override
    public void remplacer(UUID centre, UUID facture, SoldeDOuverture solde) {
        jdbc.update("UPDATE factures SET period_start = ?, period_end = ?, date_facturation = ?, total_ht = ?, total_ttc = ? "
                        + "WHERE id = ? AND center_id = ?",
                solde.debutDePeriode(), solde.finDePeriode(), solde.dateDeFacture(), solde.montantTtc(), solde.montantTtc(),
                facture, centre);
        jdbc.update("DELETE FROM facture_lignes WHERE facture_id = ? AND center_id = ?", facture, centre);
        jdbc.update("DELETE FROM facture_reglements WHERE facture_id = ? AND center_id = ? AND saisi_par = ?",
                facture, centre, AUTEUR_DE_LA_REPRISE);
        poserLeDetail(centre, facture, solde);
    }

    @Override
    public void supprimer(UUID centre, UUID facture) {
        jdbc.update("DELETE FROM facture_reglements WHERE facture_id = ? AND center_id = ?", facture, centre);
        jdbc.update("DELETE FROM facture_lignes WHERE facture_id = ? AND center_id = ?", facture, centre);
        jdbc.update("DELETE FROM factures WHERE id = ? AND center_id = ?", facture, centre);
    }

    @Override
    public List<String> facturesDuPatient(UUID patient) {
        return jdbc.queryForList("SELECT CAST(id AS VARCHAR(36)) FROM factures WHERE patient_id = ?", String.class, patient)
                .stream().map(String::toLowerCase).toList();
    }

    private void poserLeDetail(UUID centre, UUID facture, SoldeDOuverture solde) {
        jdbc.update("INSERT INTO facture_lignes (id, facture_id, center_id, forfait_id, forfait_label, unit_price_ht, "
                        + "seance_count, line_ht) VALUES (?, ?, ?, NULL, ?, ?, 1, ?)",
                UUID.randomUUID(), facture, centre, solde.libelle(), solde.montantTtc(), solde.montantTtc());
        if (solde.montantRegle() != null && solde.montantRegle().compareTo(BigDecimal.ZERO) > 0) {
            UUID reglement = UUID.randomUUID();
            jdbc.update("INSERT INTO facture_reglements (id, facture_id, center_id, montant, date_reglement, saisi_par, "
                            + "code_reglement, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    reglement, facture, centre, solde.montantRegle(), solde.dateDuReglement(), AUTEUR_DE_LA_REPRISE,
                    "REP-" + reglement.toString().substring(0, 8).toUpperCase(), OffsetDateTime.now());
        }
    }

    private UUID agenceDuPayeur(UUID centre, UUID centrePayeur) {
        if (centrePayeur == null) return null;
        return payeurs.agenceDuCentrePayeur(centre, centrePayeur).orElse(null);
    }
}
