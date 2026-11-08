package com.hemodialyse.backend.contexts.seance.application;

import com.hemodialyse.backend.contexts.seance.domain.FormatDExport;
import com.hemodialyse.backend.contexts.seance.domain.MoisDuTableauDeBord;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.UUID;

/**
 * Export du tableau de bord mensuel (GET /api/v1/seances/dashboard/export?format=csv|pdf|xlsx, {@code csv} par défaut). Ordre des refus :
 * centre (403), format (400, contrôlé AVANT le mois, comme le legacy), mois (400). Le fichier est produit par le port sortant.
 */
public class ExporterLeTableauDeBord {

    private final SessionCourante session;
    private final ConsulterLeTableauDeBord tableau;
    private final ExportsDuTableauDeBord exports;

    public ExporterLeTableauDeBord(SessionCourante session, ConsulterLeTableauDeBord tableau, ExportsDuTableauDeBord exports) {
        this.session = session;
        this.tableau = tableau;
        this.exports = exports;
    }

    public Export executer(UUID centreDemande, int annee, int mois, String formatDemande) {
        CentreId centre = session.centre(centreDemande);
        FormatDExport format = FormatDExport.lire(formatDemande);
        MoisDuTableauDeBord periode = new MoisDuTableauDeBord(annee, mois);
        var donnees = tableau.pour(centre, periode);
        String nom = "seances-dashboard-" + annee + "-" + String.format("%02d", mois) + "." + format.extension();
        return new Export(exports.produire(format, donnees), format, nom);
    }

    /** Le fichier produit, son format (type de média) et son nom. */
    public record Export(byte[] contenu, FormatDExport format, String nomDuFichier) {
    }
}
