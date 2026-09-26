package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.Overview;
import com.hemodialyse.backend.application.direction.DirectionIndicatorsQueryService.Indicators;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Instantanés mensuels des tableaux de bord de la direction : à la fin de chaque mois, les agrégats anonymes de la
 * société (finances + indicateurs) sont figés. Un instantané n'est jamais recalculé : il garde une valeur historique
 * stable, même si les données sources sont corrigées ensuite. Il est produit automatiquement le 1er du mois
 * ({@code DirectionSnapshotScheduler}) ou, à défaut, à la première demande de la direction pour un mois écoulé.
 */
@Service
public class DirectionSnapshotService {

    private static final Logger log = LoggerFactory.getLogger(DirectionSnapshotService.class);
    private static final Pattern MONTH = Pattern.compile("\\d{4}-\\d{2}");
    private static final int MAX_YEARS_BACK = 5;
    private final JdbcTemplate jdbc;
    private final DirectionDashboardQueryService dashboard;
    private final DirectionIndicatorsQueryService indicators;
    private final ObjectMapper mapper;
    public DirectionSnapshotService(JdbcTemplate jdbc, DirectionDashboardQueryService dashboard,
                                    DirectionIndicatorsQueryService indicators, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.dashboard = dashboard;
        this.indicators = indicators;
        this.mapper = mapper;
    }

    private static YearMonth parse(String mois) {
        if (mois == null || !MONTH.matcher(mois).matches()) {
            throw new BusinessException("MOIS_INVALIDE", "Mois invalide (attendu : AAAA-MM)");
        }
        try {
            return YearMonth.parse(mois);
        } catch (DateTimeParseException e) {
            throw new BusinessException("MOIS_INVALIDE", "Mois invalide (attendu : AAAA-MM)");
        }
    }

    public List<Info> list(UUID societeId) {
        return jdbc.query("SELECT mois, generated_at FROM direction_snapshot WHERE societe_id = ? ORDER BY mois DESC",
                (rs, i) -> new Info(rs.getString("mois"), rs.getObject("generated_at", OffsetDateTime.class)),
                societeId);
    }

    public Snapshot get(UUID societeId, String mois) {
        YearMonth month = parse(mois);
        var rows = jdbc.query("SELECT generated_at, payload FROM direction_snapshot WHERE societe_id = ? AND mois = ?",
                (rs, i) -> read(month.toString(), rs.getObject("generated_at", OffsetDateTime.class),
                        rs.getString("payload")),
                societeId, month.toString());
        if (rows.isEmpty()) {
            throw new BusinessException("SNAPSHOT_INTROUVABLE", "Aucun instantané pour ce mois");
        }
        return rows.get(0);
    }

    /**
     * Renvoie l'instantané du mois, en le figeant maintenant s'il n'existe pas encore (mois écoulé uniquement).
     */
    public Snapshot ensure(UUID societeId, String mois) {
        YearMonth month = parse(mois);
        if (!month.isBefore(YearMonth.now())) {
            throw new BusinessException("MOIS_NON_TERMINE", "Seul un mois écoulé peut être figé");
        }
        if (month.isBefore(YearMonth.now().minusYears(MAX_YEARS_BACK))) {
            throw new BusinessException("MOIS_TROP_ANCIEN", "Mois trop ancien");
        }
        if (list(societeId).stream().noneMatch(i -> i.mois().equals(month.toString()))) {
            create(societeId, month);
        }
        return get(societeId, month.toString());
    }

    /**
     * Fige le mois écoulé de chaque société active (tâche planifiée) ; renvoie le nombre d'instantanés créés.
     */
    public int generateForPreviousMonth() {
        YearMonth previous = YearMonth.now().minusMonths(1);
        int created = 0;
        for (UUID societeId : jdbc.queryForList("SELECT id FROM societes WHERE actif = TRUE", UUID.class)) {
            try {
                boolean exists = list(societeId).stream().anyMatch(i -> i.mois().equals(previous.toString()));
                if (!exists && create(societeId, previous)) {
                    created++;
                }
            } catch (RuntimeException e) {
                log.error("Instantané {} de la société {} non généré", previous, societeId, e);
            }
        }
        log.info("Instantanés mensuels {} : {} créé(s)", previous, created);
        return created;
    }

    private boolean create(UUID societeId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Overview overview = dashboard.overview(societeId, from, to);
        Indicators ind = indicators.indicators(societeId, from, to);
        var root = mapper.createObjectNode();
        root.set("overview", mapper.valueToTree(overview));
        root.set("indicators", mapper.valueToTree(ind));
        try {
            jdbc.update("INSERT INTO direction_snapshot (id, societe_id, mois, payload, generated_at) "
                            + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    UUID.randomUUID(), societeId, month.toString(), mapper.writeValueAsString(root));
            return true;
        } catch (DuplicateKeyException e) {
            return false; // créé entre-temps par une autre demande : l'instantané reste immuable
        }
    }

    private Snapshot read(String mois, OffsetDateTime generatedAt, String payload) {
        JsonNode root = mapper.readTree(payload);
        return new Snapshot(mois, generatedAt, root.path("overview"), root.path("indicators"));
    }

    public record Info(String mois, OffsetDateTime generatedAt) {
    }

    public record Snapshot(String mois, OffsetDateTime generatedAt, JsonNode overview, JsonNode indicators) {
    }
}
