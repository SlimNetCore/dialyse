package com.hemodialyse.backend.application.direction;

import com.hemodialyse.backend.contexts.gmao.publishedlanguage.GenerateursInstalles;
import com.hemodialyse.backend.contexts.planification.publishedlanguage.BaseDeLaCapaciteTheorique;
import com.hemodialyse.backend.application.direction.DirectionDashboardQueryService.CentreInfo;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator.Niveau;
import com.hemodialyse.backend.domain.planning.service.CapaciteTheoriqueCalculator.Resultat;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capacité théorique des centres d'une société et taux d'occupation de leur file active sur la période choisie : par
 * centre et consolidés. Méthode : voir {@link CapaciteTheoriqueCalculator} (postes actifs = générateurs − secours,
 * capacité = postes × séries par jour × patients par poste et par série, paramétrable par centre).
 * <ul>
 *   <li>Générateurs : les seuls générateurs EN SERVICE installés au plus tard à la fin de la période (GMAO, KB-PLN-76).</li>
 *   <li>Séries, patients par poste et série, file active : lectures publiées de la planification ({@link BaseDeLaCapaciteTheorique}).
 *   La file active est celle de la présence unique des patients (RG-PLN-045) : hors sommeil, hors patients déjà sortis.</li>
 * </ul>
 * Agrégats uniquement ; une file active comprise entre 1 et 4 patients est masquée ({@link AnonymityPolicy}) et son taux
 * avec elle, et ce centre est exclu du total consolidé (KB-PLN-75, champ {@code centresMasques}).
 */
@Service
public class DirectionCapaciteQueryService {

    private static final int MAX_YEARS = 5;

    private final DirectionDashboardQueryService dashboard;
    /** Les générateurs en service de chaque centre viennent de la lecture publiée par le module GMAO. */
    private final GenerateursInstalles installes;
    /** Séries, patients par poste et série, file active : lectures publiées par le module Planification (P6). */
    private final BaseDeLaCapaciteTheorique base;
    /** Fuseau du jour par défaut de la période : celui de la planification (une société consolidée n'a pas un seul centre). */
    private final ZoneId fuseau;

    public DirectionCapaciteQueryService(DirectionDashboardQueryService dashboard, GenerateursInstalles installes,
                                         BaseDeLaCapaciteTheorique base,
                                         @Value("${app.planification.fuseau-par-defaut:Africa/Algiers}") String fuseauParDefaut) {
        this.installes = installes;
        this.dashboard = dashboard;
        this.base = base;
        this.fuseau = ZoneId.of(fuseauParDefaut);
    }

    private static Ligne ligne(Resultat r) {
        Long fileMasquee = AnonymityPolicy.mask(r.fileActive());
        boolean masquee = fileMasquee == null;
        // Revue P6 : pour une file masquée, la capacité, le niveau et « atteinte » la bornent (capacité 2 + ATTEINTE = file de 2 à 4) :
        // ils sont masqués avec elle. Les équipements (générateurs, postes, séries) ne sont pas des données patient.
        return new Ligne(r.generateurs(), r.generateursSecours(), r.postesActifs(), r.series(),
                r.patientsParPosteEtSerie(), masquee ? null : r.capacite(), fileMasquee,
                masquee ? null : r.tauxOccupation(), masquee ? null : r.niveau(), masquee ? null : r.atteinte());
    }

    public CapaciteOverview capacite(UUID societeId, LocalDate from, LocalDate to) {
        LocalDate end = to != null ? to : LocalDate.now(fuseau);
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
                    ligne(CapaciteTheoriqueCalculator.agreger(List.of())), List.of(), 0);
        }
        List<UUID> ids = centres.stream().map(CentreInfo::id).toList();
        // seuls les générateurs EN SERVICE comptent (KB-PLN-76, décision du 27/10 : comme le planning de la semaine)
        Map<UUID, Long> generateurs = installes.enServiceParCentre(ids, end.plusDays(1).atStartOfDay(fuseau).toInstant());

        // KB-PLN-75 (décision du 27/10 : « masquer par centre », comme les Absences) : un centre dont la file active est de 1 à 4
        // est masqué ET exclu du total consolidé ; sinon la différence entre le total et les centres publiés le redonnerait.
        List<Resultat> publies = new ArrayList<>();
        List<CentreCapacite> lignes = new ArrayList<>();
        int centresMasques = 0;
        for (CentreInfo c : centres) {
            BaseDeLaCapaciteTheorique.Base b = base.duCentre(c.id(), start, end);
            Resultat r = CapaciteTheoriqueCalculator.calculer(generateurs.getOrDefault(c.id(), 0L).intValue(), b.creneaux(),
                    b.patientsParPosteEtSerie(), b.fileActive());
            lignes.add(new CentreCapacite(c.id(), c.nom(), c.actif(), ligne(r)));
            if (AnonymityPolicy.mask(r.fileActive()) == null) {
                centresMasques++;
            } else {
                publies.add(r);
            }
        }
        return new CapaciteOverview(societeId, start, end, maintenant, regle,
                ligne(CapaciteTheoriqueCalculator.agreger(publies)), lignes, centresMasques);
    }

    /**
     * Paramètres de la méthode de calcul communs à tous les centres, fournis pour l'expliquer à l'écran.
     */
    public record Regle(int generateursParSecours, int seuilProchePourcent) {
    }

    /**
     * Capacité d'un centre (ou totaux) : {@code series} et {@code patientsParPosteEtSerie} valent 0 pour les totaux ;
     * {@code fileActive}, {@code tauxOccupation}, {@code capacite}, {@code niveau} et {@code atteinte} sont {@code null} si la file
     * active est trop faible pour être publiée (ils la borneraient).
     */
    public record Ligne(int generateurs, int generateursSecours, int postesActifs, int series,
                        int patientsParPosteEtSerie, Integer capacite, Long fileActive, BigDecimal tauxOccupation,
                        Niveau niveau, Boolean atteinte) {
    }

    public record CentreCapacite(UUID centerId, String nom, boolean actif, Ligne capacite) {
    }

    public record CapaciteOverview(UUID societeId, LocalDate from, LocalDate to, OffsetDateTime generatedAt,
                                   Regle regle, Ligne total, List<CentreCapacite> centres, int centresMasques) {
    }
}
