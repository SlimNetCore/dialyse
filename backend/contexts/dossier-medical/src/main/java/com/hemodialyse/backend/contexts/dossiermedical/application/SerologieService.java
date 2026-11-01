package com.hemodialyse.backend.contexts.dossiermedical.application;

import com.hemodialyse.backend.contexts.dossiermedical.domain.Serologie;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieId;
import com.hemodialyse.backend.contexts.dossiermedical.domain.SerologieRepository;
import com.hemodialyse.backend.contexts.dossiermedical.publishedlanguage.SerologiePositiveDetectee;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Implémentation des use cases {@code SerologieUseCases}.
 * Orchestre l'agrégat et le port de sortie ; génère identifiants et instants.
 * Détecte les résultats sérologiques positifs et publie l'événement métier associé.
 */
public class SerologieService implements SerologieUseCases {

    private final SerologieRepository serologies;
    private final java.util.function.Supplier<UUID> idGenerator;
    private final java.util.function.Consumer<SerologiePositiveDetectee> evenementPositif;

    public SerologieService(SerologieRepository serologies,
                            java.util.function.Supplier<UUID> idGenerator,
                            java.util.function.Consumer<SerologiePositiveDetectee> evenementPositif) {
        this.serologies = serologies;
        this.idGenerator = idGenerator;
        this.evenementPositif = evenementPositif;
    }

    @Override
    public SerologieId enregistrer(EnregistrerSerologieCommande commande, Instant maintenant) {
        if (commande == null) throw new IllegalArgumentException("commande requise");
        if (maintenant == null) throw new IllegalArgumentException("maintenant requis");
        SerologieId id = new SerologieId(idGenerator.get());
        OffsetDateTime maintenantOd = maintenant.atOffset(ZoneOffset.UTC);
        LocalDate aujourdhui = LocalDate.now(ZoneOffset.UTC);
        Serologie serologie = Serologie.enregistrer(id, commande.patientId(), commande.centreId(),
                commande.prescripteurId(), commande.datePrelevement(), commande.typeSerologie(),
                commande.resultat(), commande.valeur(), commande.unite(), commande.dateResultat(),
                commande.laboratoire(), commande.commentaire(), maintenantOd);
        serologies.save(serologie);
        if (serologie.estPositif()) {
            SerologiePositiveDetectee evenement = new SerologiePositiveDetectee(
                    UUID.randomUUID(), serologie.id().value(), serologie.patientId(),
                    serologie.centreId().valeur(), serologie.typeSerologie().name(),
                    commande.dateResultat() != null ? commande.dateResultat() : maintenant);
            evenementPositif.accept(evenement);
        }
        return id;
    }

    @Override
    public Serologie consulter(SerologieId serologieId, CentreId centreId) {
        return serologies.trouver(serologieId, centreId)
                .orElseThrow(() -> new IllegalArgumentException("Serologie introuvable"));
    }

    @Override
    public PagedResult<Serologie> listerParPatient(CentreId centreId, UUID patientId, int page, int size) {
        return serologies.listerParPatient(centreId, patientId, page, size);
    }
}
