package com.hemodialyse.backend.contexts.stock.application.reception;

import com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Recalcul massif du PMP (RG-STK-062) : une tâche rejoue plusieurs articles, <b>une transaction par article</b>, chacun
 * verrouillé (verrou de ligne) le temps de son rejeu — une saisie concurrente attend au lieu d'être refusée (ADR 0045). Le
 * suivi des tâches et la liste des articles en cours de recalcul sont tenus en mémoire de l'instance (consultation seulement :
 * aucun verrou n'en dépend).
 */
public final class RecalculMassifDuPmp {

    private final RejeuDuPmp rejeu;
    private final EcrituresDuStock ecritures;
    private final Articles articles;
    private final UniteDeTravail uniteDeTravail;
    private final NotificationsDuStock notifications;
    private final Executor executeur;
    private final Supplier<UUID> identifiants;
    private final Clock horloge;
    private final Map<UUID, Tache> taches = new ConcurrentHashMap<>();

    public RecalculMassifDuPmp(RejeuDuPmp rejeu, EcrituresDuStock ecritures, Articles articles, UniteDeTravail uniteDeTravail,
                               NotificationsDuStock notifications, Executor executeur, Supplier<UUID> identifiants, Clock horloge) {
        this.rejeu = rejeu;
        this.ecritures = ecritures;
        this.articles = articles;
        this.uniteDeTravail = uniteDeTravail;
        this.notifications = notifications;
        this.executeur = executeur;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    /**
     * @throws RefusDuReferentiel {@code RECALCUL_SANS_ARTICLE} aucun article demandé
     * @throws ArticleIntrouvable un article est inconnu ou d'un autre centre
     */
    public UUID demarrer(CentreId centre, Collection<UUID> demandes) {
        List<UUID> uniques = demandes == null ? List.of() : demandes.stream().filter(a -> a != null).distinct().toList();
        if (uniques.isEmpty()) {
            throw new RefusDuReferentiel("RECALCUL_SANS_ARTICLE", "Aucun article a recalculer");
        }
        var connus = articles.identifiants(centre);
        if (!connus.containsAll(uniques)) {
            throw new ArticleIntrouvable();
        }
        UUID id = identifiants.get();
        Tache tache = new Tache(id, centre.valeur(), new ArrayList<>(uniques), OffsetDateTime.now(horloge));
        taches.put(id, tache);
        notifications.verrousDeRecalculModifies(centre.valeur(), uniques, "STARTED");
        executeur.execute(() -> executer(centre, tache));
        return id;
    }

    /**
     * @throws RefusDuReferentiel {@code RECALCUL_INTROUVABLE} tâche inconnue de cette instance
     */
    public Suivi suivre(UUID tacheId) {
        Tache tache = taches.get(tacheId);
        if (tache == null) {
            throw new RefusDuReferentiel("RECALCUL_INTROUVABLE", "Tâche de recalcul introuvable");
        }
        return tache.suivi();
    }

    /**
     * Articles du centre en cours de recalcul (tâches non terminées de cette instance).
     */
    public List<UUID> enCours(CentreId centre) {
        return taches.values().stream().filter(t -> t.centre.equals(centre.valeur()) && !t.terminee)
                .flatMap(t -> t.articles.stream()).distinct().toList();
    }

    private void executer(CentreId centre, Tache tache) {
        tache.statut = Etat.RUNNING;
        tache.message = "Recalcul en cours";
        try {
            for (UUID article : tache.articles) {
                uniteDeTravail.executer(() -> {
                    ecritures.verrouiller(centre, List.of(article));
                    return rejeu.rejouer(centre, article);
                });
                tache.traites++;
                tache.message = "Article " + tache.traites + "/" + tache.articles.size();
            }
            tache.statut = Etat.COMPLETED;
            tache.message = "Recalcul termine";
        } catch (RuntimeException e) {
            tache.statut = Etat.FAILED;
            tache.message = e.getMessage() != null ? e.getMessage() : "Erreur de recalcul";
        } finally {
            tache.terminee = true;
            tache.termineLe = OffsetDateTime.now(horloge);
            notifications.verrousDeRecalculModifies(tache.centre, tache.articles, tache.statut == Etat.FAILED ? "FAILED" : "COMPLETED");
        }
    }

    public enum Etat {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public record Suivi(UUID jobId, UUID centerId, Etat status, int total, int processed, String message,
                        OffsetDateTime startedAt, OffsetDateTime finishedAt, List<UUID> articleIds) {
    }

    private static final class Tache {
        private final UUID id;
        private final UUID centre;
        private final List<UUID> articles;
        private final OffsetDateTime demarreLe;
        private volatile Etat statut = Etat.PENDING;
        private volatile int traites;
        private volatile String message = "En attente";
        private volatile OffsetDateTime termineLe;
        private volatile boolean terminee;

        private Tache(UUID id, UUID centre, List<UUID> articles, OffsetDateTime demarreLe) {
            this.id = id;
            this.centre = centre;
            this.articles = articles;
            this.demarreLe = demarreLe;
        }

        private Suivi suivi() {
            return new Suivi(id, centre, statut, articles.size(), traites, message, demarreLe, termineLe, List.copyOf(articles));
        }
    }
}

