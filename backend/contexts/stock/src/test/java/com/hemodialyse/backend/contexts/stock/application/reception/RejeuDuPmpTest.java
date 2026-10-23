package com.hemodialyse.backend.contexts.stock.application.reception;

import com.hemodialyse.backend.contexts.stock.application.reception.RecalculMassifDuPmp.Etat;
import com.hemodialyse.backend.contexts.stock.application.reception.RecalculMassifDuPmp.Suivi;
import com.hemodialyse.backend.contexts.stock.domain.CentreId;
import com.hemodialyse.backend.contexts.stock.domain.lectures.LecturesDuStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock;
import com.hemodialyse.backend.contexts.stock.domain.lectures.MouvementDeStock.Type;
import com.hemodialyse.backend.contexts.stock.domain.reception.EcrituresDuStock;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.ArticleIntrouvable;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.RefusDuReferentiel;
import com.hemodialyse.backend.contexts.stock.doubles.ArticlesEnMemoire;
import com.hemodialyse.backend.contexts.stock.application.referentiel.ReferentielDesArticles;
import com.hemodialyse.backend.contexts.stock.domain.referentiel.FicheArticle;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Rejeu du PMP et recalcul massif (RG-STK-060, 062) : valorisation des sorties, verrou avant rejeu, une transaction par article.
 */
class RejeuDuPmpTest {

    private static final CentreId CENTRE = new CentreId(UUID.randomUUID());
    private static final Clock HORLOGE = Clock.fixed(Instant.parse("2026-10-23T08:00:00Z"), ZoneOffset.UTC);
    private static final OffsetDateTime T0 = OffsetDateTime.parse("2026-10-01T00:00:00Z");

    private final List<MouvementDeStock> historique = new ArrayList<>();
    private final List<String> journal = new ArrayList<>();
    private List<EcrituresDuStock.MiseAJourDeMouvement> ecrites = List.of();
    private BigDecimal pmpEcrit;
    private BigDecimal quantiteEcrite;

    private final LecturesDuStock lectures = (LecturesDuStock) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{LecturesDuStock.class}, (proxy, methode, arguments) -> {
                if (methode.getName().equals("mouvementsDeLArticle")) {
                    List<MouvementDeStock> ordre = new ArrayList<>(historique);
                    ordre.sort(MouvementDeStock.CHRONOLOGIE);
                    return ordre;
                }
                throw new UnsupportedOperationException(methode.getName());
            });

    private final EcrituresDuStock ecritures = (EcrituresDuStock) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{EcrituresDuStock.class}, (proxy, methode, arguments) -> {
                switch (methode.getName()) {
                    case "verrouiller" -> journal.add("verrou:" + ((Collection<?>) arguments[1]).size());
                    case "appliquerLeRejeu" -> {
                        journal.add("rejeu");
                        ecrites = (List<EcrituresDuStock.MiseAJourDeMouvement>) arguments[2];
                        pmpEcrit = (BigDecimal) arguments[3];
                        quantiteEcrite = (BigDecimal) arguments[4];
                    }
                    default -> throw new UnsupportedOperationException(methode.getName());
                }
                return null;
            });

    private final RejeuDuPmp rejeu = new RejeuDuPmp(lectures, ecritures);

    private MouvementDeStock mouvement(Type type, String quantite, String prix, OffsetDateTime quand) {
        MouvementDeStock m = new MouvementDeStock(UUID.randomUUID(), UUID.randomUUID(), null, null, type, new BigDecimal(quantite),
                prix == null ? null : new BigDecimal(prix), quand, quand);
        historique.add(m);
        return m;
    }

    @Test
    void chaque_sortie_est_revalorisee_au_pmp_qui_la_precede_et_l_article_recoit_le_pmp_final() {
        mouvement(Type.ENTREE, "100", "8", T0);
        MouvementDeStock sortie = mouvement(Type.SORTIE, "30", "99", T0.plusDays(1));
        mouvement(Type.ENTREE, "50", "11", T0.plusDays(2));
        rejeu.rejouer(CENTRE, UUID.randomUUID());
        assertEquals(new BigDecimal("9.2500"), pmpEcrit.setScale(4));
        assertEquals(new BigDecimal("120"), quantiteEcrite.stripTrailingZeros().setScale(0));
        var miseSortie = ecrites.stream().filter(m -> m.mouvementId().equals(sortie.id())).findFirst().orElseThrow();
        assertEquals(new BigDecimal("8.0000"), miseSortie.valorisation());
        assertNull(ecrites.get(0).valorisation());
    }

    @Test
    void une_sortie_sans_pmp_dans_l_historique_garde_son_prix_enregistre() {
        MouvementDeStock sortie = mouvement(Type.SORTIE, "3", "5", T0);
        rejeu.rejouer(CENTRE, UUID.randomUUID());
        assertNull(ecrites.stream().filter(m -> m.mouvementId().equals(sortie.id())).findFirst().orElseThrow().valorisation());
        assertEquals(BigDecimal.ZERO, quantiteEcrite);
    }

    @Test
    void le_recalcul_massif_verrouille_puis_rejoue_chaque_article_dans_sa_transaction() {
        ArticlesEnMemoire articles = new ArticlesEnMemoire();
        ReferentielDesArticles referentiel = new ReferentielDesArticles(articles, UUID::randomUUID, HORLOGE);
        UUID a1 = referentiel.referencer(CENTRE, fiche("A1")).id();
        UUID a2 = referentiel.referencer(CENTRE, fiche("A2")).id();
        List<String> evenements = new ArrayList<>();
        RecalculMassifDuPmp recalcul = new RecalculMassifDuPmp(rejeu, ecritures, articles,
                new com.hemodialyse.backend.contexts.stock.application.UniteDeTravail() {
                    @Override
                    public <T> T executer(java.util.function.Supplier<T> travail) {
                        evenements.add("tx");
                        return travail.get();
                    }
                }, new com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock() {
            @Override
            public void mouvementsModifies(UUID centre, String mouvement, String reference, int nombreArticles) {
            }

            @Override
            public void verrousDeRecalculModifies(UUID centre, List<UUID> articlesTouches, String etat) {
                evenements.add(etat);
            }
        }, Runnable::run, UUID::randomUUID, HORLOGE);
        UUID tache = recalcul.demarrer(CENTRE, List.of(a1, a2, a1));
        Suivi suivi = recalcul.suivre(tache);
        assertEquals(Etat.COMPLETED, suivi.status());
        assertEquals(2, suivi.total());
        assertEquals(2, suivi.processed());
        assertEquals(List.of("STARTED", "tx", "tx", "COMPLETED"), evenements);
        assertEquals(List.of("verrou:1", "rejeu", "verrou:1", "rejeu"), journal);
        assertEquals(List.of(), recalcul.enCours(CENTRE));
    }

    @Test
    void le_recalcul_massif_refuse_une_demande_vide_un_article_inconnu_ou_une_tache_inconnue() {
        ArticlesEnMemoire articles = new ArticlesEnMemoire();
        RecalculMassifDuPmp recalcul = new RecalculMassifDuPmp(rejeu, ecritures, articles,
                new com.hemodialyse.backend.contexts.stock.application.UniteDeTravail() {
                    @Override
                    public <T> T executer(java.util.function.Supplier<T> travail) {
                        return travail.get();
                    }
                }, new com.hemodialyse.backend.contexts.stock.application.NotificationsDuStock() {
            @Override
            public void mouvementsModifies(UUID centre, String mouvement, String reference, int nombreArticles) {
            }

            @Override
            public void verrousDeRecalculModifies(UUID centre, List<UUID> articlesTouches, String etat) {
            }
        }, Runnable::run, UUID::randomUUID, HORLOGE);
        assertEquals("RECALCUL_SANS_ARTICLE", assertThrows(RefusDuReferentiel.class, () -> recalcul.demarrer(CENTRE, List.of())).code());
        assertThrows(ArticleIntrouvable.class, () -> recalcul.demarrer(CENTRE, List.of(UUID.randomUUID())));
        assertEquals("RECALCUL_INTROUVABLE", assertThrows(RefusDuReferentiel.class, () -> recalcul.suivre(UUID.randomUUID())).code());
    }

    private static FicheArticle fiche(String code) {
        return FicheArticle.saisie(code, "L " + code, null, null, null, null, "piece", null, null, null, null, null, null, null, null,
                null, false, false, null, false, false, null, null, null);
    }

    @SuppressWarnings("unused")
    private static Optional<LocalDate> inutilise() {
        return Optional.empty();
    }
}

