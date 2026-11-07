package com.hemodialyse.backend.contexts.dossiermedical.infrastructure.persistence;

import com.hemodialyse.backend.contexts.dossiermedical.domain.CategorieDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.CodeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandeExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.DemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.EcritureConcurrenteSurUneDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.LigneDeDemandeDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PageDeDemandesDExamen;
import com.hemodialyse.backend.contexts.dossiermedical.domain.PatientId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.RegleDeGestionViolee;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SaisieInvalide;
import com.hemodialyse.backend.contexts.dossiermedical.domain.StatutDeDemandeDExamen;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Demandes d'examen sur les tables réelles {@code demandes_examen} et {@code lignes_demande_examen} (aucune colonne ajoutée, et
 * surtout PAS de {@code center_id} sur les lignes : ADR 0072). Les lignes sont lues dans un ordre DÉTERMINISTE (libellé, code,
 * commentaire, identifiant : KB-MED-36 corrigé ; un rang exigerait une migration, non décidée) ; la réponse à la création rend les
 * lignes dans l'ordre saisi. Une transition ne touche QUE l'en-tête (le legacy supprimait et recréait les lignes). Une demande dont
 * le statut ou la catégorie est illisible en base (donnée incohérente) est ignorée par toutes les lectures, comptage compris
 * (KB-MED-39 corrigé : elle ne casse plus la liste). Une demande dont UNE LIGNE est illisible (nomenclature inconnue, code invalide) est
 * aussi ignorée (journal WARN avec son seul identifiant, à chaque lecture) ; LIMITE reproduite et consignée : le {@code total} compte les
 * demandes aux statut et catégorie lisibles, donc peut dépasser le nombre d'éléments listés d'autant de demandes ignorées pour une ligne
 * (le détecter en SQL exigerait de recopier les formats des nomenclatures). Verrou : {@code SELECT ... FOR UPDATE} de la demande, gardé jusqu'à la fin de la
 * transaction ; ordre global des verrous : centre, fiches (cette table), puis {@code app_user}, puis verrous consultatifs.
 */
public class DemandesDExamenJdbc implements DemandesDExamen {

    private static final String LISIBLES = " AND statut IN (" + liste(StatutDeDemandeDExamen.values()) + ") AND categorie IN ("
            + liste(CategorieDExamen.values()) + ")";
    private static final String COLONNES = "id, center_id, patient_id, prescripteur_id, date_demande, categorie, urgent, motif, statut, "
            + "conclusion, created_at, updated_at";
    private static final String ENTETE = "SELECT " + COLONNES + " FROM demandes_examen WHERE id = ? AND patient_id = ? AND center_id = ?"
            + LISIBLES;
    private static final String LIGNES = "SELECT id, demande_id, analyte_code_system, analyte_code, analyte_code_display, libelle, commentaire "
            + "FROM lignes_demande_examen WHERE demande_id IN (%s) ORDER BY libelle NULLS LAST, analyte_code NULLS FIRST, "
            + "commentaire NULLS FIRST, id";

    private static final Logger JOURNAL = LoggerFactory.getLogger(DemandesDExamenJdbc.class);

    private final JdbcTemplate jdbc;

    public DemandesDExamenJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static String liste(Enum<?>[] valeurs) {
        return Arrays.stream(valeurs).map(v -> "'" + v.name() + "'").collect(Collectors.joining(","));
    }

    @Override
    public void ajouter(DemandeExamen d) {
        jdbc.update("INSERT INTO demandes_examen (id, patient_id, center_id, prescripteur_id, date_demande, categorie, urgent, motif, statut, "
                        + "conclusion, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", d.id(), d.patient().valeur(), d.centre().valeur(),
                d.prescripteur(), d.dateDemande(), d.categorie().name(), d.urgent(), d.motif(), d.statut().name(), d.conclusion(),
                horodatage(d.creeeLe()), horodatage(d.modifieeLe()));
        for (LigneDeDemandeDExamen l : d.lignes()) {
            CodeDExamen c = l.code();
            jdbc.update("INSERT INTO lignes_demande_examen (id, demande_id, analyte_code_system, analyte_code, analyte_code_display, libelle, "
                            + "commentaire) VALUES (?,?,?,?,?,?,?)", l.id(), d.id(), c == null ? null : c.nomenclature(), c == null ? null : c.code(),
                    c == null ? null : c.libelle(), l.libelle(), l.commentaire());
        }
    }

    @Override
    public void enregistrerLaTransition(DemandeExamen d) {
        jdbc.update("UPDATE demandes_examen SET statut = ?, conclusion = ?, updated_at = ? WHERE id = ? AND center_id = ?",
                d.statut().name(), d.conclusion(), horodatage(d.modifieeLe()), d.id(), d.centre().valeur());
    }

    @Override
    public Optional<DemandeExamen> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id) {
        try {
            List<Entete> entetes = jdbc.query(ENTETE + " FOR UPDATE", (rs, i) -> entete(rs), id, patient.valeur(), centre.valeur());
            return assembler(entetes).stream().findFirst();
        } catch (PessimisticLockingFailureException | QueryTimeoutException | UncategorizedSQLException e) {
            if (e instanceof UncategorizedSQLException technique && !"55P03".equals(technique.getSQLException().getSQLState())) {
                throw e;
            }
            throw new EcritureConcurrenteSurUneDemandeDExamen();
        }
    }

    @Override
    public PageDeDemandesDExamen duPatient(CentreId centre, PatientId patient, int page, int taille) {
        Long total = jdbc.queryForObject("SELECT count(*) FROM demandes_examen WHERE center_id = ? AND patient_id = ?" + LISIBLES, Long.class,
                centre.valeur(), patient.valeur());
        List<Entete> entetes = jdbc.query("SELECT " + COLONNES + " FROM demandes_examen WHERE center_id = ? AND patient_id = ?" + LISIBLES
                        + " ORDER BY date_demande DESC, created_at DESC, id DESC LIMIT ? OFFSET ?", (rs, i) -> entete(rs),
                centre.valeur(), patient.valeur(), taille, (long) page * taille);
        return new PageDeDemandesDExamen(assembler(entetes), total == null ? 0 : total, page, taille);
    }

    private List<DemandeExamen> assembler(List<Entete> entetes) {
        if (entetes.isEmpty()) return List.of();
        String marques = String.join(",", entetes.stream().map(e -> "?").toList());
        Map<UUID, List<LigneDeDemandeDExamen>> parDemande = new LinkedHashMap<>();
        Set<UUID> incoherentes = new HashSet<>();
        jdbc.query(String.format(LIGNES, marques), rs -> {
            UUID demande = rs.getObject("demande_id", UUID.class);
            try {
                String nomenclature = rs.getString("analyte_code_system");
                CodeDExamen code = nomenclature == null || rs.getString("analyte_code") == null ? null
                        : new CodeDExamen(nomenclature, rs.getString("analyte_code"), rs.getString("analyte_code_display"));
                parDemande.computeIfAbsent(demande, k -> new ArrayList<>()).add(new LigneDeDemandeDExamen(rs.getObject("id", UUID.class), code,
                        rs.getString("libelle"), rs.getString("commentaire")));
            } catch (SaisieInvalide | RegleDeGestionViolee illisible) {
                // Ignorée À CHAQUE LECTURE, journalisée : identifiant de la demande SEULEMENT (aucune donnée de santé ni de patient).
                if (incoherentes.add(demande)) {
                    JOURNAL.warn("Demande d'examen {} ignorée : une de ses lignes est illisible en base (donnée incohérente)", demande);
                }
            }
        }, entetes.stream().map(Entete::id).toArray());
        List<DemandeExamen> resultat = new ArrayList<>();
        for (Entete e : entetes) {
            if (incoherentes.contains(e.id())) continue;
            resultat.add(DemandeExamen.reconstituer(e.id(), new PatientId(e.patient()), new CentreId(e.centre()), e.prescripteur(), e.date(),
                    CategorieDExamen.valueOf(e.categorie()), e.urgent(), e.motif(), StatutDeDemandeDExamen.valueOf(e.statut()), e.conclusion(),
                    parDemande.getOrDefault(e.id(), List.of()), e.creeeLe(), e.modifieeLe()));
        }
        return resultat;
    }

    private static Entete entete(ResultSet rs) throws SQLException {
        return new Entete(rs.getObject("id", UUID.class), rs.getObject("center_id", UUID.class), rs.getObject("patient_id", UUID.class),
                rs.getString("prescripteur_id"), rs.getObject("date_demande", LocalDate.class), rs.getString("categorie"), rs.getBoolean("urgent"),
                rs.getString("motif"), rs.getString("statut"), rs.getString("conclusion"), instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static Instant instant(ResultSet rs, String colonne) throws SQLException {
        OffsetDateTime lu = rs.getObject(colonne, OffsetDateTime.class);
        return lu == null ? null : lu.toInstant();
    }

    private static OffsetDateTime horodatage(Instant instant) {
        return instant == null ? null : OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record Entete(UUID id, UUID centre, UUID patient, String prescripteur, LocalDate date, String categorie, boolean urgent,
                          String motif, String statut, String conclusion, Instant creeeLe, Instant modifieeLe) {
    }
}
