package com.hemodialyse.backend.contexts.pilotagedirection.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Entité {@code ExportSynthese} — export de synthèse du pilotage (slice PD4).
 * <p>
 * Le contenu est assemblé à partir des read models du contexte (tableau de bord, alertes,
 * instantané) ; le rendu physique (PDF/HTML via {@code ModeleDocumentPrinter}, §11.1) reste
 * assuré par la plateforme reporting jusqu'au retrait complet du legacy (dette documentée
 * dans la slice PD4).
 * <p>
 * L'entité ne génère jamais d'identifiant ni d'horloge elle-même : l'identifiant et l'instant
 * sont fournis par l'appelant (couche application), conformément à la frontière hexagonale
 * (règle ArchUnit {@code le_domaine_ne_tire_ni_identifiant_ni_heure}).
 */
public final class ExportSynthese {

    private final ExportSyntheseId id;
    private final CentreId centreId;
    private final String periode;
    private final FormatExport format;
    private final StatutExport statut;
    private final String contenu;
    private final OffsetDateTime dateGeneration;

    private ExportSynthese(ExportSyntheseId id, CentreId centreId, String periode,
                           FormatExport format, StatutExport statut, String contenu,
                           OffsetDateTime dateGeneration) {
        this.id = id;
        this.centreId = centreId;
        this.periode = periode;
        this.format = format;
        this.statut = statut;
        this.contenu = contenu;
        this.dateGeneration = dateGeneration;
    }

    /**
     * Génère un nouvel export de synthèse.
     */
    public static ExportSynthese generer(ExportSyntheseId id, CentreId centreId, String periode,
                                         FormatExport format, String contenu, OffsetDateTime maintenant) {
        if (id == null) throw new IllegalArgumentException("ExportSyntheseId requis");
        if (centreId == null) throw new IllegalArgumentException("centreId requis");
        if (periode == null || periode.isBlank()) throw new IllegalArgumentException("periode requise");
        if (format == null) throw new IllegalArgumentException("format requis");
        if (contenu == null || contenu.isBlank()) throw new IllegalArgumentException("contenu requis");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        return new ExportSynthese(id, centreId, periode, format, StatutExport.GENERE, contenu, maintenant);
    }

    /**
     * Reconstitue un export depuis la persistance.
     */
    public static ExportSynthese reconstituer(ExportSyntheseId id, CentreId centreId, String periode,
                                              FormatExport format, StatutExport statut, String contenu,
                                              OffsetDateTime dateGeneration) {
        return new ExportSynthese(id, centreId, periode, format, statut, contenu, dateGeneration);
    }

    public ExportSyntheseId id() {
        return id;
    }

    public CentreId centreId() {
        return centreId;
    }

    public String periode() {
        return periode;
    }

    public FormatExport format() {
        return format;
    }

    public StatutExport statut() {
        return statut;
    }

    public String contenu() {
        return contenu;
    }

    public OffsetDateTime dateGeneration() {
        return dateGeneration;
    }

    /**
     * Liste des identifiants de centres concernés par cette entité (ici un seul : centreId).
     * Utilisé pour le contrôle d'isolation multi-centre.
     */
    public List<CentreId> centresConcernees() {
        return Collections.singletonList(centreId);
    }
}
