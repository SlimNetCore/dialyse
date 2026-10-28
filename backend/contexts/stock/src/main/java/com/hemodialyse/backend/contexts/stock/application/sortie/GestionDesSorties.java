package com.hemodialyse.backend.contexts.stock.application.sortie;

import com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock;
import com.hemodialyse.backend.contexts.stock.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.stock.application.reception.RejeuDuPmp;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Article;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.Articles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.BonsDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.DateDeSortieDeSeanceImmuable;
import com.hemodialyse.backend.contexts.stock.domain.sortie.LigneDeSortie;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortieInvalide;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortie.SortiesDuStock.LotDuStock;
import com.hemodialyse.backend.contexts.stock.domain.sortie.StatutDesSeances;

import java.math.BigDecimal;

import com.hemodialyse.backend.contexts.stock.application.HorlogeDesCentres;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage des bons de sortie manuels d'un centre (RG-STK-030 à 033) : sortir des lots choisis, corriger un bon, consulter,
 * lister, lister les lots disponibles (FEFO). Une sortie est valorisée au PMP courant de l'article ; une correction restitue les
 * lots de l'ancien contenu puis sort les nouvelles lignes, à la date et à l'heure de création du bon. Chaque écriture s'exécute
 * dans une seule transaction, articles verrouillés par identifiant croissant, puis rejeu du PMP des articles touchés (ADR 0045).
 */
public final class GestionDesSorties {

    static final String CODE_SEANCE_FACTUREE = "SEANCE_FACTUREE_NON_MODIFIABLE";
    private static final String AUTEUR_PAR_DEFAUT = "system";

    private final BonsDeSortie bons;
    private final Articles articles;
    private final SortiesDuStock sorties;
    private final EcrituresDuStock ecritures;
    private final RejeuDuPmp rejeu;
    private final StatutDesSeances seances;
    private final UniteDeTravail uniteDeTravail;
    private final NotificationsDuStock notifications;
    private final Supplier<UUID> identifiants;
    private final HorlogeDesCentres horloge;

    public GestionDesSorties(BonsDeSortie bons, Articles articles, SortiesDuStock sorties, EcrituresDuStock ecritures,
                             RejeuDuPmp rejeu, StatutDesSeances seances, UniteDeTravail uniteDeTravail,
                             NotificationsDuStock notifications, Supplier<UUID> identifiants, HorlogeDesCentres horloge) {
        this.bons = bons;
        this.articles = articles;
        this.sorties = sorties;
        this.ecritures = ecritures;
        this.rejeu = rejeu;
        this.seances = seances;
        this.uniteDeTravail = uniteDeTravail;
        this.notifications = notifications;
        this.identifiants = identifiants;
        this.horloge = horloge;
    }

    private static SortieInvalide bonIntrouvable(UUID id) {
        return new SortieInvalide("Bon de sortie introuvable: " + id);
    }

    // ------------------------------------------------------------------ sortie

    private static Set<UUID> articlesDe(List<LigneSaisie> saisies) {
        Set<UUID> touches = new TreeSet<>();
        saisies.stream().map(LigneSaisie::articleId).filter(a -> a != null).forEach(touches::add);
        return touches;
    }

    // ------------------------------------------------------------------ correction

    private static String auteur(String auteur) {
        return auteur == null || auteur.isBlank() ? AUTEUR_PAR_DEFAUT : auteur;
    }

    /**
     * @throws SortieInvalide     {@code BAD_REQUEST} : aucune ligne, ligne incomplète, article ou lot inconnu du centre, lot d'un
     *                            autre article
     * @throws RefusDuReferentiel {@code BUSINESS_RULE_VIOLATION} (stock insuffisant sur le lot), {@code STOCK_INVENTORY_IN_PROGRESS},
     *                            {@code STOCK_PERIOD_CLOSED}
     */
    public BonDeSortie sortir(CentreId centre, UUID seanceId, UUID patientId, String poste, LocalDate dateSortie, String auteur,
                              List<LigneSaisie> saisies) {
        if (saisies == null || saisies.isEmpty()) {
            throw new SortieInvalide("Aucun article a sortir");
        }
        String par = auteur(auteur);
        UUID seance = seanceId != null ? seanceId : BonDeSortie.AUCUNE_SEANCE;
        LocalDate jour = dateSortie != null ? dateSortie : horloge.aujourdHui(centre);
        OffsetDateTime maintenant = horloge.maintenant();
        return uniteDeTravail.executer(() -> {
            Set<UUID> touches = articlesDe(saisies);
            ecritures.verrouiller(centre, touches);
            List<LigneDeSortie> lignes = new ArrayList<>();
            for (LigneSaisie saisie : saisies) {
                lignes.add(sortirLaLigne(centre, seance, jour, maintenant, par, saisie));
            }
            BonDeSortie bon = bons.creer(centre, reference -> BonDeSortie.nouveau(identifiants.get(), centre, reference, seance,
                    patientId, poste, jour, par, maintenant, lignes));
            touches.forEach(article -> rejeu.rejouer(centre, article));
            notifications.mouvementsModifies(centre.valeur(), "SORTIE", bon.reference(), touches.size());
            return bon;
        });
    }

    /**
     * Les champs absents gardent la valeur du bon ; la date d'un bon rattaché à une séance ne change pas.
     *
     * @throws RefusDuReferentiel           {@code SEANCE_FACTUREE_NON_MODIFIABLE} si la séance est facturée
     * @throws DateDeSortieDeSeanceImmuable si la date demandée diffère de celle d'un bon de séance
     */
    public BonDeSortie corriger(CentreId centre, UUID id, UUID seanceId, UUID patientId, String poste, LocalDate dateSortie,
                                String auteur, List<LigneSaisie> saisies) {
        if (saisies == null || saisies.isEmpty()) {
            throw new SortieInvalide("Aucune ligne de sortie");
        }
        String par = auteur(auteur);
        OffsetDateTime maintenant = horloge.maintenant();
        return uniteDeTravail.executer(() -> {
            BonDeSortie existant = bons.parId(centre, id).orElseThrow(() -> bonIntrouvable(id));
            UUID seance = seanceId != null ? seanceId : existant.seanceId();
            boolean liee = BonDeSortie.estLieeAUneSeance(seance);
            if (liee && seances.estFacturee(centre, seance)) {
                throw new RefusDuReferentiel(CODE_SEANCE_FACTUREE, "La seance facturee ne peut plus etre modifiee");
            }
            if (liee && dateSortie != null && existant.dateSortie() != null && !dateSortie.equals(existant.dateSortie())) {
                throw new DateDeSortieDeSeanceImmuable();
            }
            Set<UUID> touches = articlesDe(saisies);
            existant.lignes().forEach(l -> touches.add(l.articleId()));
            ecritures.verrouiller(centre, touches);

            OffsetDateTime saisiLe = existant.creeLe() != null ? existant.creeLe() : maintenant;
            restituer(centre, existant);

            UUID patient = patientId != null ? patientId : existant.patientId();
            String posteFinal = poste != null ? poste : existant.poste();
            LocalDate jour = liee ? existant.dateSortie() : (dateSortie != null ? dateSortie : existant.dateSortie());
            String createur = existant.creePar() != null ? existant.creePar() : par;
            LocalDate jourDesMouvements = jour != null ? jour : horloge.aujourdHui(centre);
            List<LigneDeSortie> lignes = new ArrayList<>();
            for (LigneSaisie saisie : saisies) {
                lignes.add(sortirLaLigne(centre, seance, jourDesMouvements, saisiLe, par, saisie));
            }
            BonDeSortie corrige = bons.modifier(centre, id, b -> b.corriger(seance, patient, posteFinal, jour, createur, lignes))
                    .orElseThrow(() -> bonIntrouvable(id));
            touches.forEach(article -> rejeu.rejouer(centre, article));
            notifications.mouvementsModifies(centre.valeur(), "CORRECTION", corrige.reference(), touches.size());
            return corrige;
        });
    }

    // ------------------------------------------------------------------ lecture

    /**
     * Restitue aux lots les quantités de l'ancien contenu et supprime les mouvements de sortie correspondants.
     */
    private void restituer(CentreId centre, BonDeSortie existant) {
        if (existant.lignes().isEmpty()) {
            return;
        }
        ecritures.exigerAucunInventaireEnCours(centre);
        for (LigneDeSortie ligne : existant.lignes()) {
            if (ligne.lotId() == null) {
                continue;
            }
            LotDuStock lot = sorties.lot(centre, ligne.lotId()).orElseThrow(
                    () -> new SortieInvalide("Lot introuvable: " + ligne.lotId()));
            sorties.restituer(centre, lot.id(), ligne.quantite());
            sorties.retirerLaSortie(centre, existant.seanceId(), ligne.articleId(), ligne.lotId(), ligne.quantite(),
                    existant.creeLe());
        }
    }

    private LigneDeSortie sortirLaLigne(CentreId centre, UUID seance, LocalDate jour, OffsetDateTime saisiLe, String auteur,
                                        LigneSaisie saisie) {
        if (saisie.articleId() == null || saisie.lotId() == null || saisie.quantite() == null || saisie.quantite().signum() <= 0) {
            throw new SortieInvalide("Ligne de sortie invalide");
        }
        Article article = articles.parId(centre, saisie.articleId())
                .orElseThrow(() -> new SortieInvalide("Article introuvable: " + saisie.articleId()));
        LotDuStock lot = sorties.lot(centre, saisie.lotId())
                .orElseThrow(() -> new SortieInvalide("Lot introuvable: " + saisie.lotId()));
        if (!saisie.articleId().equals(lot.articleId())) {
            throw new SortieInvalide("Le lot choisi n'appartient pas a l'article " + article.fiche().code());
        }
        BigDecimal disponible = lot.quantiteRestante() != null ? lot.quantiteRestante() : BigDecimal.ZERO;
        if (disponible.compareTo(saisie.quantite()) < 0) {
            throw new RefusDuReferentiel("BUSINESS_RULE_VIOLATION", "Stock insuffisant sur le lot " + lot.numero());
        }
        ecritures.exigerAucunInventaireEnCours(centre);
        ecritures.exigerUneDateOuverte(centre, jour);
        // Règle métier : une sortie est toujours valorisée au dernier PMP courant de l'article.
        BigDecimal pmp = article.pmp() != null ? article.pmp() : BigDecimal.ZERO;
        sorties.consommer(centre, lot.id(), saisie.quantite());
        sorties.enregistrerSortie(centre, saisie.articleId(), seance, lot.id(), saisie.quantite(), pmp, auteur, jour, saisiLe);
        return new LigneDeSortie(identifiants.get(), saisie.articleId(), lot.id(), saisie.quantite(), pmp);
    }

    /**
     * @throws SortieInvalide {@code BAD_REQUEST} : bon inconnu ou d'un autre centre
     */
    public BonDeSortie consulter(CentreId centre, UUID id) {
        return bons.parId(centre, id).orElseThrow(() -> bonIntrouvable(id));
    }

    // ------------------------------------------------------------------ outils

    public List<BonDeSortie> lister(CentreId centre) {
        return bons.tous(centre);
    }

    /**
     * Lots de l'article à quantité restante positive, FEFO, avec le PMP courant de l'article (0 si l'article est inconnu du
     * centre).
     */
    public List<LotDisponible> lotsDisponibles(CentreId centre, UUID articleId) {
        BigDecimal pmp = articles.parId(centre, articleId).map(Article::pmp).orElse(BigDecimal.ZERO);
        return sorties.lotsDisponibles(centre, articleId).stream()
                .map(l -> new LotDisponible(l.id(), l.numero(), l.datePeremption(), pmp, l.quantiteRestante())).toList();
    }

    /**
     * Ligne telle que saisie : article, lot choisi et quantité.
     */
    public record LigneSaisie(UUID articleId, UUID lotId, BigDecimal quantite) {
    }

    public record LotDisponible(UUID id, String numeroLot, LocalDate datePeremption, BigDecimal pmp,
                                BigDecimal quantiteRestante) {
    }
}

