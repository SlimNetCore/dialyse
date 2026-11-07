package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrente;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDOrdonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Medicament;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnance;
import com.hemodialyse.backend.contexts.dossiermedical.domain.Ordonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDOrdonnances;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutOrdonnance;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ordonnances sur les tables réelles {@code ordonnances} et {@code lignes_ordonnance} (aucune colonne ajoutée). Les lignes sont
 * lues dans un ordre DÉTERMINISTE (libellé, posologie, identifiant : KB-MED-16 corrigé ; le rang de saisie exigerait une colonne,
 * donc une migration, non décidée) ; la réponse à la création rend les lignes dans l'ordre saisi. Verrou : {@code SELECT ... FOR
 * UPDATE} de la fiche, gardé jusqu'à la fin de la transaction ; ordre global des verrous : centre, fiches (cette table), puis
 * compteur ({@code app_settings}).
 */
public class OrdonnancesJdbc implements Ordonnances {
    private static final org.slf4j.Logger JOURNAL = org.slf4j.LoggerFactory.getLogger(OrdonnancesJdbc.class);

    private static final String ENTETE = "SELECT id, center_id, patient_id, medecin_id, date_prescription, statut, numero, created_at, "
            + "updated_at, signed_at FROM ordonnances WHERE id = ? AND patient_id = ? AND center_id = ?";
    private static final String LIGNES = "SELECT id, ordonnance_id, medicament_code_system, medicament_code, medicament_code_display, "
            + "libelle, posologie, voie, duree_jours, quantite, instructions FROM lignes_ordonnance WHERE ordonnance_id IN (%s) "
            + "ORDER BY libelle NULLS LAST, posologie, id";

    private final JdbcTemplate jdbc;

    public OrdonnancesJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void ajouter(Ordonnance o) {
        jdbc.update("INSERT INTO ordonnances (id, center_id, patient_id, medecin_id, date_prescription, statut, numero, created_at, "
                        + "updated_at, signed_at) VALUES (?,?,?,?,?,?,?,?,?,?)", o.id(), o.centre().valeur(), o.patient().valeur(),
                o.medecin(), o.datePrescription(), o.statut().name(), o.numero(), horodatage(o.creeeLe()), horodatage(o.modifieeLe()),
                horodatage(o.signeeLe()));
        for (LigneDOrdonnance l : o.lignes()) {
            Medicament m = l.medicament();
            jdbc.update("INSERT INTO lignes_ordonnance (id, ordonnance_id, medicament_code_system, medicament_code, "
                            + "medicament_code_display, libelle, posologie, voie, duree_jours, quantite, instructions) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)", l.id(), o.id(), m == null ? null : m.nomenclature(),
                    m == null ? null : m.code(), m == null ? null : m.libelle(), l.libelle(), l.posologie(), l.voie(), l.dureeJours(),
                    l.quantite(), l.instructions());
        }
    }

    @Override
    public void enregistrerLaTransition(Ordonnance o) {
        jdbc.update("UPDATE ordonnances SET statut = ?, numero = ?, updated_at = ?, signed_at = ? WHERE id = ? AND center_id = ?",
                o.statut().name(), o.numero(), horodatage(o.modifieeLe()), horodatage(o.signeeLe()), o.id(), o.centre().valeur());
    }

    @Override
    public Optional<Ordonnance> trouver(CentreId centre, PatientId patient, UUID id) {
        return charger(ENTETE, centre, patient, id);
    }

    @Override
    public Optional<Ordonnance> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        try {
            return charger(ENTETE + " FOR UPDATE", centre, patient, id);
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrente();
        }
    }

    @Override
    public PageDOrdonnances duPatient(CentreId centre, PatientId patient, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM ordonnances WHERE center_id = ? AND patient_id = ?", Long.class,
                centre.valeur(), patient.valeur());
        List<Entete> entetes = jdbc.query("SELECT id, center_id, patient_id, medecin_id, date_prescription, statut, numero, created_at, "
                        + "updated_at, signed_at FROM ordonnances WHERE center_id = ? AND patient_id = ? "
                        + "ORDER BY date_prescription DESC, created_at DESC, id DESC LIMIT ? OFFSET ?", (rs, i) -> entete(rs),
                centre.valeur(), patient.valeur(), taille, (long) page * taille);
        return new PageDOrdonnances(assembler(entetes), total == null ? 0 : total, page, taille);
    }

    private Optional<Ordonnance> charger(String sql, CentreId centre, PatientId patient, UUID id) {
        List<Entete> entetes = jdbc.query(sql, (rs, i) -> entete(rs), id, patient.valeur(), centre.valeur());
        return assembler(entetes).stream().findFirst();
    }

    private List<Ordonnance> assembler(List<Entete> entetes) {
        if (entetes.isEmpty()) return List.of();
        String marques = String.join(",", entetes.stream().map(e -> "?").toList());
        Map<UUID, List<LigneDOrdonnance>> parOrdonnance = new LinkedHashMap<>();
        jdbc.query(String.format(LIGNES, marques), rs -> {
            String nomenclature = rs.getString("medicament_code_system");
            Medicament medicament = nomenclature == null || rs.getString("medicament_code") == null ? null
                    : new Medicament(nomenclature, rs.getString("medicament_code"), rs.getString("medicament_code_display"));
            parOrdonnance.computeIfAbsent(rs.getObject("ordonnance_id", UUID.class), k -> new ArrayList<>()).add(
                    new LigneDOrdonnance(rs.getObject("id", UUID.class), medicament, rs.getString("libelle"), rs.getString("posologie"),
                            rs.getString("voie"), rs.getObject("duree_jours", Integer.class), rs.getObject("quantite", Integer.class),
                            rs.getString("instructions")));
        }, entetes.stream().map(Entete::id).toArray());
        List<Ordonnance> resultat = new ArrayList<>();
        for (Entete e : entetes) {
            StatutOrdonnance statut;
            try {
                statut = StatutOrdonnance.valueOf(e.statut());
            } catch (IllegalArgumentException | NullPointerException illisible) {
                // KB-MED-102 (DM8) : une ordonnance au statut illisible n'échoue plus toute la lecture, son identifiant SEUL est journalisé.
                JOURNAL.warn("Ordonnance {} ignorée : statut illisible en base (donnée incohérente)", e.id());
                continue;
            }
            resultat.add(Ordonnance.reconstituer(e.id(), new PatientId(e.patient()), new CentreId(e.centre()), e.medecin(), e.date(),
                    statut, e.numero(), parOrdonnance.getOrDefault(e.id(), List.of()), e.creeeLe(),
                    e.modifieeLe(), e.signeeLe()));
        }
        return resultat;
    }

    private static Entete entete(ResultSet rs) throws SQLException {
        return new Entete(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getString("medecin_id"), rs.getObject("date_prescription", LocalDate.class), rs.getString("statut"),
                rs.getString("numero"), instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "signed_at"));
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime lu = rs.getObject(colonne, OffsetDateTime.class);
        return lu == null ? null : lu.toInstant();
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record Entete(UUID id, UUID centre, UUID patient, String medecin, LocalDate date, String statut, String numero,
                          Instant creeeLe, Instant modifieeLe, Instant signeeLe) {
    }
}
