package com.hemodialyse.backend.infrastructure.config;

import com.hemodialyse.backend.domain.gmao.model.Equipement;
import com.hemodialyse.backend.domain.gmao.model.StatutEquipement;
import com.hemodialyse.backend.domain.gmao.model.TypeEquipement;
import com.hemodialyse.backend.domain.gmao.port.EquipementRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Migration idempotente, au démarrage, de l'ancien référentiel plat {@code generateur} vers l'agrégat
 * GMAO {@code Equipement} (type {@code GENERATEUR_DIALYSE}), qui en devient la source de vérité unique
 * (module GMAO v2 — l'ancien référentiel ne comporte pas de vrai statut de maintenance).
 * <p>
 * Préserve l'{@code id} d'origine : {@code patients.generateur_id} n'a aucune contrainte FK (voir
 * {@code PatientJpaEntity}) et reste donc valide sans aucune réécriture.
 * <p>
 * Défensif comme {@link SeedPasswordInitializer} : si la table {@code generateur} n'existe pas (base
 * neuve — elle n'est plus créée par {@code db/schema.sql}), ce composant ne fait rien.
 */
@Component
public class GenerateurMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(GenerateurMigrationRunner.class);

    private final JdbcTemplate jdbc;
    private final EquipementRepositoryPort equipementRepository;

    public GenerateurMigrationRunner(JdbcTemplate jdbc, EquipementRepositoryPort equipementRepository) {
        this.jdbc = jdbc;
        this.equipementRepository = equipementRepository;
    }

    @Override
    public void run(String... args) {
        for (Map<String, Object> row : safeFindLegacyGenerateurs()) {
            UUID id = (UUID) row.get("id");
            if (equipementRepository.existsById(id)) {
                continue;
            }
            try {
                equipementRepository.save(toEquipement(row, id, createur((UUID) row.get("center_id"))));
            } catch (RuntimeException e) {
                // ne jamais empêcher le démarrage : la migration est reprise au prochain démarrage (idempotente)
                log.warn("[GMAO] Migration du générateur {} impossible pour l'instant : {}", id, e.getMessage());
            }
        }
    }

    /**
     * Utilisateur référencé comme créateur (clé étrangère vers {@code app_user} en PostgreSQL) : un utilisateur du
     * centre, à défaut n'importe quel utilisateur ; l'identifiant nul n'est qu'un repli pour une base sans utilisateur
     * (la contrainte rejette alors l'insertion, reprise au démarrage suivant).
     */
    private UUID createur(UUID centerId) {
        List<UUID> ids = jdbc.query("SELECT user_id FROM app_user_center WHERE center_id = ? ORDER BY user_id LIMIT 1",
                (rs, i) -> rs.getObject(1, UUID.class), centerId);
        if (ids.isEmpty()) {
            ids = jdbc.query("SELECT id FROM app_user ORDER BY username LIMIT 1", (rs, i) -> rs.getObject(1, UUID.class));
        }
        return ids.isEmpty() ? new UUID(0, 0) : ids.getFirst();
    }

    private Equipement toEquipement(Map<String, Object> row, UUID id, UUID creePar) {
        String numero = (String) row.get("numero");
        return Equipement.reconstruct(
                id,
                numero,
                "Générateur " + numero,
                TypeEquipement.GENERATEUR_DIALYSE,
                (String) row.get("marque"),
                (String) row.get("modele"),
                null,
                OffsetDateTime.now(ZoneOffset.UTC),
                (UUID) row.get("center_id"),
                mapStatut((String) row.get("etat")),
                null,
                "Migré automatiquement depuis l'ancien référentiel \"generateur\"",
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                creePar,
                null,
                (UUID) row.get("salle_id"),
                null
        );
    }

    private StatutEquipement mapStatut(String etat) {
        if (etat == null) return StatutEquipement.EN_SERVICE;
        return switch (etat) {
            case "EN_MAINTENANCE", "EN_REPARATION" -> StatutEquipement.EN_MAINTENANCE;
            case "EN_PANNE", "HORS_SERVICE" -> StatutEquipement.HORS_SERVICE;
            case "REFORME" -> StatutEquipement.REFORME;
            default -> StatutEquipement.EN_SERVICE;
        };
    }

    private List<Map<String, Object>> safeFindLegacyGenerateurs() {
        try {
            return jdbc.queryForList(
                    "SELECT id, salle_id, center_id, numero, marque, modele, etat FROM generateur");
        } catch (DataAccessException ignored) {
            // Base neuve : la table n'existe plus (retirée de db/schema.sql) — rien à migrer.
            return List.of();
        }
    }
}
