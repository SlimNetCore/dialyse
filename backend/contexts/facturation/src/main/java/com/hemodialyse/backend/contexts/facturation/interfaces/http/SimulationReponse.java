package com.hemodialyse.backend.contexts.facturation.interfaces.http;

import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.LigneSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Réponse des routes de simulation : mêmes champs et mêmes noms que le legacy ({@code FacturationPreviewResult}).
 */
public record SimulationReponse(UUID centerId, OffsetDateTime generatedAt, LocalDate periodStart, LocalDate periodEnd,
                                int totalFactures, BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc,
                                List<Facture> invoices) {

    static SimulationReponse depuis(Simulation simulation) {
        return new SimulationReponse(simulation.centre().valeur(),
                OffsetDateTime.ofInstant(simulation.etablieLe(), ZoneOffset.UTC), simulation.periode().debut(),
                simulation.periode().fin(), simulation.factures().size(), simulation.totalHt(), simulation.totalTva(),
                simulation.totalTtc(), simulation.factures().stream().map(Facture::depuis).toList());
    }

    public record Facture(String previewKey, String numeroFacture, UUID patientId, String patientCode,
                          String patientFullName,
                          String patientStatusSnapshot, BigDecimal totalHt, BigDecimal totalTva, BigDecimal totalTtc,
                          List<Ligne> lines, List<UUID> seanceIds, List<Seance> seances) {

        static Facture depuis(FactureSimulee facture) {
            return new Facture(facture.cle(), facture.numeroPrevisionnel(), facture.patient().id(),
                    facture.patient().codeAffiche(), facture.patient().nomComplet(), facture.patient().etatAffiche(),
                    facture.totalHt(), facture.totalTva(), facture.totalTtc(),
                    facture.lignes().stream().map(Ligne::depuis).toList(),
                    facture.seances().stream().map(SeanceFacturable::id).toList(),
                    facture.seances().stream().map(Seance::depuis).toList());
        }
    }

    public record Ligne(UUID forfaitId, String forfaitLabel, BigDecimal unitPriceHt, int seanceCount,
                        BigDecimal lineHt) {

        static Ligne depuis(LigneSimulee ligne) {
            return new Ligne(ligne.forfait().id(), ligne.forfait().libelle(), ligne.prixUnitaireHt(),
                    ligne.nombreDeSeances(), ligne.montantHt());
        }
    }

    public record Seance(UUID seanceId, LocalDate seanceDate, String seanceStatus, UUID forfaitId, String forfaitLabel,
                         BigDecimal forfaitPrixHt) {

        static Seance depuis(SeanceFacturable seance) {
            String libelle = seance.forfait().libelle();
            return new Seance(seance.id(), seance.date(), seance.statut().valeur(), seance.forfait().id(),
                    libelle == null || libelle.isBlank() ? "-" : libelle,
                    seance.forfait().prixHt().setScale(2, RoundingMode.HALF_UP));
        }
    }
}
