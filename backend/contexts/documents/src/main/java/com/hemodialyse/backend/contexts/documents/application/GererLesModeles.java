package com.hemodialyse.backend.contexts.documents.application;

import com.hemodialyse.backend.contexts.documents.domain.DefinitionDeModele;
import com.hemodialyse.backend.contexts.documents.domain.ModeleDuCatalogue;
import com.hemodialyse.backend.contexts.documents.domain.RechercheDeModeles;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage de la gestion des modèles de document d'un centre (DOC4a, ex {@code DocumentRestController}) : lister, rechercher, créer, modifier,
 * supprimer. Toute opération est bornée au centre de la session (centre transverse pour le SUPERADMIN). Supprimer un modèle retire aussi
 * l'historique de ses versions de gabarit, dans la même unité de travail.
 *
 * <p>Reproduits, à confirmer métiers/DPO avant le 2026-12-15 (KB-DOC4A-6) : modifier ou supprimer un modèle inexistant, ou d'un autre centre,
 * ne fait rien et répond comme un succès.
 */
public class GererLesModeles {

    private static final Logger journal = LoggerFactory.getLogger(GererLesModeles.class);

    private final SessionCourante session;
    private final ModelesDeDocument modeles;
    private final GererLesGabarits gabarits;
    private final UniteDeTravail unite;
    private final Supplier<UUID> identifiants;

    public GererLesModeles(SessionCourante session, ModelesDeDocument modeles, GererLesGabarits gabarits, UniteDeTravail unite,
                           Supplier<UUID> identifiants) {
        this.session = session;
        this.modeles = modeles;
        this.gabarits = gabarits;
        this.unite = unite;
        this.identifiants = identifiants;
    }

    /** Le centre à utiliser (celui de la session, ou le centre demandé pour un accès transverse) ; 403 sinon. */
    public CentreId exigerLeCentre(UUID centreDemande) {
        return session.exigerLeCentre(centreDemande);
    }

    public List<ModeleDuCatalogue> lister(UUID centre, String type) {
        return modeles.lister(centre, type);
    }

    public List<ModeleDuCatalogue> rechercher(RechercheDeModeles recherche) {
        return modeles.rechercher(recherche);
    }

    /** Crée le modèle sous l'identifiant demandé, à défaut sous un identifiant nouveau ; rend l'identifiant. */
    public UUID creer(UUID identifiantDemande, DefinitionDeModele modele) {
        UUID id = identifiantDemande != null ? identifiantDemande : identifiants.get();
        modeles.creer(id, modele);
        journal.info("Modèle de document créé : centre={}, modèle={}, code={}", modele.centre(), id, modele.code());
        return id;
    }

    public void modifier(UUID id, DefinitionDeModele modele) {
        modeles.modifier(id, modele);
        journal.info("Modèle de document modifié : centre={}, modèle={}", modele.centre(), id);
    }

    public void supprimer(UUID centre, UUID id) {
        unite.executer(() -> {
            gabarits.retirerLHistorique(centre, id);
            modeles.supprimer(centre, id);
            return null;
        });
        journal.info("Modèle de document supprimé : centre={}, modèle={}", centre, id);
    }
}
