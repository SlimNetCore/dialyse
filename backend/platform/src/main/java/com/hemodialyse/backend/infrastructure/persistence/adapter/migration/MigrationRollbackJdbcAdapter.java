package com.hemodialyse.backend.infrastructure.persistence.adapter.migration;

import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.AnnulationDeLaReprise;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RepriseDesAssures;
import com.hemodialyse.backend.contexts.seance.publishedlanguage.SeancesDeLaReprisePubliees;
import com.hemodialyse.backend.infrastructure.migration.model.IdMapping;
import com.hemodialyse.backend.infrastructure.migration.model.MigrationEntity;
import com.hemodialyse.backend.infrastructure.migration.port.MigrationRollbackPort;
import com.hemodialyse.backend.infrastructure.migration.port.OpeningBalancePort;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Annulation d'un lot de reprise : seules les données créées par le lot sont supprimées, et uniquement si elles
 * n'ont pas été utilisées depuis (séance facturée ou complétée, facture de reprise encaissée, données saisies
 * dans la plateforme pour un patient repris…). Les données créées par le même lot ne bloquent jamais.
 */
@Component
public class MigrationRollbackJdbcAdapter implements MigrationRollbackPort {

    private static final Logger log = LoggerFactory.getLogger(MigrationRollbackJdbcAdapter.class);

    /**
     * Tables rattachées à un patient qui empêchent sa suppression (assure_patient est supprimée avec lui).
     */
    private static final List<String> PATIENT_DATA = List.of("prise_en_charge", "attestation_droit", SeancesDeLaReprisePubliees.NATURE,
            "factures", "dossier_medical_patient", "prescriptions_medicales", "antecedents_medicaux", "allergies_patient",
            "serologies_patient", "abords_vasculaires", "resultats_analyses", "observations_biologiques", "ordonnances",
            "demandes_examen", "administrations_anemie", "bons_sortie", "bilans_pre_greffe");

    private final JdbcTemplate jdbc;
    private final RepriseDesSoldes reprise;
    /** Séances reprises : lues et supprimées par le langage publié de la Séance (SE7e, ADR 0099), mêmes requêtes qu'avant. */
    private final SeancesDeLaReprisePubliees seances;

    /** Assurés repris : l'état d'origine d'un assuré rapproché est mémorisé et restauré par Patient et couverture (KB-S6a-2). */
    private final RepriseDesAssures assures;

    /** Patients, assurés et affectations créés par le lot : supprimés et contrôlés par le langage publié de Patient et couverture (RP1, ADR 0169). */
    private final AnnulationDeLaReprise annulation;

    public MigrationRollbackJdbcAdapter(JdbcTemplate jdbc, RepriseDesSoldes reprise, SeancesDeLaReprisePubliees seances,
                                        RepriseDesAssures assures, AnnulationDeLaReprise annulation) {
        this.annulation = annulation;
        this.assures = assures;
        this.reprise = reprise;
        this.jdbc = jdbc;
        this.seances = seances;
    }

    /**
     * Lignes créées par le lot, par nature de données (elles ne bloquent pas la suppression de leur patient).
     */
    private static Map<String, Set<String>> createdByTable(List<IdMapping> created) {
        Map<String, Set<String>> out = new HashMap<>();
        for (IdMapping m : created) {
            String table = m.entity() == MigrationEntity.SOLDES_OUVERTURE ? "factures"
                    : m.entity() == MigrationEntity.SEANCES ? SeancesDeLaReprisePubliees.NATURE
                    : HistoricalRecordJdbcAdapter.TABLES.containsKey(m.entity())
                    ? HistoricalRecordJdbcAdapter.TABLES.get(m.entity()).name() : null;
            if (table != null) out.computeIfAbsent(table, t -> new HashSet<>()).add(m.targetId().toLowerCase());
        }
        return out;
    }

    private static Set<String> targets(List<IdMapping> mappings, MigrationEntity entity) {
        return mappings.stream().filter(m -> m.entity() == entity).map(IdMapping::targetId).collect(Collectors.toSet());
    }

    @Override
    public List<String> blockers(CentreId centerId, List<IdMapping> created) {
        Map<String, Set<String>> createdByTable = createdByTable(created);
        Set<String> createdPatients = targets(created, MigrationEntity.PATIENTS);
        Set<String> createdAffectations = targets(created, MigrationEntity.AFFECTATIONS);
        List<String> blockers = new ArrayList<>();

        for (IdMapping m : created) {
            switch (m.entity()) {
                case PATIENTS -> {
                    UUID patientId = UUID.fromString(m.targetId());
                    for (String table : PATIENT_DATA) {
                        Set<String> ownRows = createdByTable.getOrDefault(table, Set.of());
                        // les factures sont celles de la Facturation, les séances celles de la Séance : lues par leur langage publié
                        // prises en charge et attestations : langage publié de Patient et couverture (RP1, ADR 0169)
                        List<String> rows = "factures".equals(table) ? reprise.facturesDuPatient(patientId)
                                : SeancesDeLaReprisePubliees.NATURE.equals(table) ? ids(() -> seances.seancesDuPatient(patientId))
                                : "prise_en_charge".equals(table) ? ids(() -> uuids(annulation.prisesEnChargeDuPatient(patientId)))
                                : "attestation_droit".equals(table) ? ids(() -> uuids(annulation.attestationsDuPatient(patientId)))
                                : ids("SELECT CAST(id AS VARCHAR(36)) FROM " + table + " WHERE patient_id = ?", patientId);
                        boolean usedElsewhere = rows.stream().anyMatch(id -> !ownRows.contains(id));
                        if (usedElsewhere) {
                            blockers.add("patient « " + m.legacyId() + " » (" + table + ")");
                            break;
                        }
                    }
                }
                case ASSURES -> {
                    if (annulation.assureRetenuHorsDuLot(centerId.valeur(), m.targetId(), createdAffectations, createdPatients))
                        blockers.add("assuré « " + m.targetId() + " » (rattaché à un patient existant)");
                }
                case SEANCES -> {
                    UUID seanceId = UUID.fromString(m.targetId());
                    boolean billed = !ids(() -> seances.factureDeLaSeance(seanceId)).isEmpty();
                    boolean completed = !ids("SELECT CAST(id AS VARCHAR(36)) FROM volet_medical WHERE seance_id = ?", seanceId).isEmpty()
                            || !ids("SELECT CAST(id AS VARCHAR(36)) FROM volet_paramedical WHERE seance_id = ?", seanceId).isEmpty();
                    if (billed || completed) {
                        blockers.add("séance « " + m.legacyId() + " » (" + (billed ? "facturée" : "complétée") + " dans la plateforme)");
                    }
                }
                case SOLDES_OUVERTURE -> {
                    long payments = reprise.reglementsHorsReprise(centerId.valeur(), UUID.fromString(m.targetId()));
                    if (payments > 0) {
                        blockers.add("facture « " + m.legacyId() + " » (encaissée dans la plateforme)");
                    }
                }
                default -> {
                    // Attestations, PEC, dossier médical… : supprimables sans condition.
                }
            }
        }
        return blockers;
    }

    @Override
    public void delete(CentreId centerId, List<IdMapping> created) {
        UUID center = centerId.valeur();
        for (IdMapping m : created) {
            switch (m.entity()) {
                case AFFECTATIONS -> annulation.supprimerLAffectation(center, UUID.fromString(m.targetId()));
                case PATIENTS -> annulation.supprimerLePatient(center, UUID.fromString(m.targetId()));
                case ASSURES -> annulation.supprimerLAssure(center, m.targetId());
                case SOLDES_OUVERTURE -> reprise.supprimer(center, UUID.fromString(m.targetId()));
                case SEANCES -> seances.retirer(center, UUID.fromString(m.targetId()));
                default -> jdbc.update("DELETE FROM " + HistoricalRecordJdbcAdapter.table(m.entity()).name()
                        + " WHERE id = ? AND center_id = ?", UUID.fromString(m.targetId()), center);
            }
        }
    }

    @Override
    public void restore(CentreId centerId, List<IdMapping> updated) {
        for (IdMapping m : updated) {
            if (m.entity() == MigrationEntity.ASSURES && m.etatOrigine() != null) {
                assures.restaurer(centerId.valeur(), m.targetId(), m.etatOrigine());
            }
        }
    }

    private static List<String> uuids(List<UUID> identifiants) {
        return identifiants.stream().map(UUID::toString).toList();
    }

    private List<String> ids(String sql, Object... args) {
        return ids(() -> jdbc.queryForList(sql, String.class, args));
    }

    /** Identifiants en minuscules ; contrôle ignoré si la table est absente (même traitement pour une lecture publiée). */
    private List<String> ids(Supplier<List<String>> lecture) {
        try {
            return lecture.get().stream().map(String::toLowerCase).toList();
        } catch (DataAccessException e) {
            // Table absente d'un schéma allégé (ex. H2 de développement sans le module concerné).
            log.debug("Contrôle ignoré : {}", e.getMessage());
            return List.of();
        }
    }
}

