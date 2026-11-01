package com.hemodialyse.backend.contexts.pilotagedirection.application;

import com.hemodialyse.backend.contexts.pilotagedirection.domain.AlerteDirectionRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSynthese;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseId;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.ExportSyntheseRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.InstantaneMensuelRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.domain.TableauDeBordRepository;
import com.hemodialyse.backend.contexts.pilotagedirection.publishedlanguage.ExportSyntheseGenere;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Implémentation des use cases {@code ExportSyntheseUseCases}.
 * Assemble le contenu de l'export à partir des read models du contexte
 * (tableau de bord, alertes, instantanés) — lecture seule intra-contexte (R4).
 */
public class ExportSyntheseService implements ExportSyntheseUseCases {

    private final ExportSyntheseRepository exports;
    private final TableauDeBordRepository tableaux;
    private final AlerteDirectionRepository alertes;
    private final InstantaneMensuelRepository instantanes;
    private final java.util.function.Supplier<UUID> idGenerator;
    private final java.util.function.Consumer<ExportSyntheseGenere> evenementGeneration;

    public ExportSyntheseService(ExportSyntheseRepository exports,
                                 TableauDeBordRepository tableaux,
                                 AlerteDirectionRepository alertes,
                                 InstantaneMensuelRepository instantanes,
                                 java.util.function.Supplier<UUID> idGenerator,
                                 java.util.function.Consumer<ExportSyntheseGenere> evenementGeneration) {
        this.exports = exports;
        this.tableaux = tableaux;
        this.alertes = alertes;
        this.instantanes = instantanes;
        this.idGenerator = idGenerator;
        this.evenementGeneration = evenementGeneration;
    }

    @Override
    public ExportSyntheseId generer(GenererExportCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        String contenu = assemblerContenu(commande.centreId(), commande.periode());
        ExportSyntheseId id = new ExportSyntheseId(idGenerator.get());
        ExportSynthese export = ExportSynthese.generer(id, commande.centreId(), commande.periode(),
                commande.format(), contenu, maintenantOd);
        exports.save(export);
        evenementGeneration.accept(new ExportSyntheseGenere(
                UUID.randomUUID(), export.id().value(), export.centreId().valeur(),
                export.periode(), export.format().name(), maintenant));
        return id;
    }

    @Override
    public ExportSynthese telecharger(ExportSyntheseId exportId, CentreId centreId) {
        return exports.trouver(exportId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Export de synthèse introuvable"));
    }

    /**
     * Assemble le contenu de synthèse à partir des read models du contexte.
     * Le rendu physique (PDF/HTML via {@code ModeleDocumentPrinter}, §11.1) reste assuré
     * par la plateforme reporting jusqu'au retrait complet du legacy.
     */
    private String assemblerContenu(CentreId centreId, String periode) {
        String tableau = tableaux.trouver(centreId).map(t -> t.indicateurs()).orElse("{}");
        long alertesNonLues = alertes.listerParCentre(centreId, 0, Integer.MAX_VALUE).items().stream()
                .filter(a -> !a.lue()).count();
        long nbInstantanes = instantanes.listerParCentre(centreId, 0, 1).total();
        return "{\"centre\":\"" + centreId.valeur() + "\",\"periode\":\"" + periode
                + "\",\"tableauDeBord\":" + tableau
                + ",\"alertesNonLues\":" + alertesNonLues
                + ",\"instantanes\":" + nbInstantanes + "}";
    }
}
