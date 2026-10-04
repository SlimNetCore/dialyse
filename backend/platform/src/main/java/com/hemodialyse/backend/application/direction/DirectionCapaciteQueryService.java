package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.domain.planning.model.PlanningParametres;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator.Niveau;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator.Resultat;
import com.hemodialyse.backend.application.query.EffectifSql;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Capacité théorique des centres d'une société et taux d'occupation de leur file active sur la période choisie : par
 * centre et consolidés. Méthode : voir {@link CapaciteTheoriqueCalculator} (postes actifs = générateurs − secours,
 * capacité = postes × séries par jour × patients par poste et par série, paramétrable par centre).
 * <ul>
 *   <li>Générateurs : ceux installés au plus tard à la fin de la période (hors réformés, désactivés, supprimés).</li>
 *   <li>Séries : créneaux configurés du centre.</li>
 *   <li>File active : patients distincts ayant au moins une séance réalisée (validée, signée ou facturée) pendant la
 *   période.</li>
 * </ul>
 * Agrégats uniquement ; une file active comprise entre 1 et 4 patients est masquée ({@link AnonymityPolicy}) et son taux
 * avec elle.
 */
@Service
public class DirectionCapaciteQueryService {

    private static final int MAX_YEARS = 5;
    private static final String GENERATEURS = "SELECT centre_id AS cid, COUNT(*) AS n FROM gmao_equipements "
            + "WHERE type = 'GENERATEUR_DIALYSE' AND deleted_at IS NULL AND statut NOT IN ('REFORME', 'DESACTIF') "
            + "AND date_installation < ? AND centre_id IN (%s) GROUP BY centre_id";
    private static final String SERIES = "SELECT center_id AS cid, COUNT(*) AS n FROM position_creneau "
            + "WHERE center_id IN (%s) GROUP BY center_id";
    /**
     * File active : effectif de la période (règle unique RG-PAT-032 — un patient sorti avant le début n'est pas compté,
     * un patient sorti pendant ou après la période l'est), hors patients « en sommeil ».
     */
    private static final String FILE_ACTIVE = "SELECT p.center_id AS cid, COUNT(*) AS n FROM patients p WHERE "
            + EffectifSql.presentSur("p", LocalDate.EPOCH, LocalDate.EPOCH).sql()
            + " AND COALESCE(p.en_sommeil, FALSE) = FALSE AND p.center_id IN (%s) GROUP BY p.center_id";
    private static final String PARAMETRES = "SELECT center_id AS cid, patients_par_poste_serie AS n "
            + "FROM planning_parametres WHERE center_id IN (%s)";

    private final JdbcTemplate jdbc;
    private final DirectionDashboardQueryService dashboard;

    public DirectionCapaciteQueryService(JdbcTemplate jdbc, DirectionDashboardQueryService dashboard) {
        this.jdbc = jdbc;
        this.dashboard = dashboard;
    }

    private static Ligne ligne(Resultat r) {
        Long fileMasquee = AnonymityPolicy.mask(r.fileActive());
        return new Ligne(r.generateurs(), r.generateursSecours(), r.postesActifs(), r.series(),
                r.patientsParPosteEtSerie(), r.capacite(), fileMasquee,
                fileMasquee == null ? null : r.tauxOccupation(), r.niveau(), r.atteinte());
    }

    public CapaciteOverview capacite(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : LocalDate.of(end.getYear(), 1, 1);
        if (start.isAfter(end)) {
            throw new BusinessException("PERIODE_INVALIDE", "La date de début doit précéder la date de fin");
        }
        if (start.plusYears(MAX_YEARS).isBefore(end)) {
            throw new BusinessException("PERIODE_TROP_LONGUE", "La période ne peut pas dépasser " + MAX_YEARS + " ans");
        }
        Regle regle = new Regle(CapaciteTheoriqueCalculator.GENERATEURS_PAR_SECOURS,
                CapaciteTheoriqueCalculator.SEUIL_PROCHE_POURCENT);
        OffsetDateTime maintenant = OffsetDateTime.now(ZoneOffset.UTC);
        List<CentreInfo> centres = dashboard.societe(societeId).centres();
        if (centres.isEmpty()) {
            return new CapaciteOverview(societeId, start, end, maintenant, regle,
                    ligne(CapaciteTheoriqueCalculator.agreger(List.of())), List.of());
        }
        List<UUID> ids = centres.stream().map(CentreInfo::id).toList();
        Map<UUID, Long> generateurs = compter(GENERATEURS, ids, end.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC));
        Map<UUID, Long> series = compter(SERIES, ids);
        Map<UUID, Long> files = compter(FILE_ACTIVE, ids, EffectifSql.presentSur("p", start, end).args().toArray());
        Map<UUID, Long> parametres = compter(PARAMETRES, ids);

        List<Resultat> resultats = new ArrayList<>();
        List<CentreCapacite> lignes = new ArrayList<>();
        for (CentreInfo c : centres) {
            long k = parametres.getOrDefault(c.id(), 0L);
            Resultat r = CapaciteTheoriqueCalculator.calculer(generateurs.getOrDefault(c.id(), 0L).intValue(),
                    series.getOrDefault(c.id(), 0L).intValue(),
                    k > 0 ? (int) k : PlanningParametres.PATIENTS_PAR_POSTE_PAR_DEFAUT,
                    files.getOrDefault(c.id(), 0L));
            resultats.add(r);
            lignes.add(new CentreCapacite(c.id(), c.nom(), c.actif(), ligne(r)));
        }
        return new CapaciteOverview(societeId, start, end, maintenant, regle,
                ligne(CapaciteTheoriqueCalculator.agreger(resultats)), lignes);
    }

    /**
     * @param extras paramètres de la requête placés avant les identifiants de centre (dates de période, etc.)
     */
    private Map<UUID, Long> compter(String sql, List<UUID> ids, Object... extras) {
        String in = ids.stream().map(i -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>(List.of(extras));
        args.addAll(ids);
        Map<UUID, Long> parCentre = new HashMap<>();
        jdbc.query(String.format(sql, in),
                rs -> {
                    parCentre.put(rs.getObject("cid", UUID.class), rs.getLong("n"));
                }, args.toArray());
        return parCentre;
    }

    /**
     * Paramètres de la méthode de calcul communs à tous les centres, fournis pour l'expliquer à l'écran.
     */
    public record Regle(int generateursParSecours, int seuilProchePourcent) {
    }

    /**
     * Capacité d'un centre (ou totaux) : {@code series} et {@code patientsParPosteEtSerie} valent 0 pour les totaux ;
     * {@code fileActive} et {@code tauxOccupation} sont {@code null} si la file active est trop faible pour être publiée.
     */
    public record Ligne(int generateurs, int generateursSecours, int postesActifs, int series,
                        int patientsParPosteEtSerie, int capacite, Long fileActive, BigDecimal tauxOccupation,
                        Niveau niveau, boolean atteinte) {
    }

    public record CentreCapacite(UUID centerId, String nom, boolean actif, Ligne capacite) {
    }

    public record CapaciteOverview(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt,
                                   Regle regle, Ligne total, List<CentreCapacite> centres) {
    }
}
