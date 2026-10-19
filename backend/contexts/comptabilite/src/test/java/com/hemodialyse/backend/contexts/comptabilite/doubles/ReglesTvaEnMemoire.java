package com.hemodialyse.backend.contexts.comptabilite.doubles;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.LectureDesTypesDeTva;
import com.hemodialyse.backend.contexts.comptabilite.application.tva.TypeDeTvaVue;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire des règles de TVA et de la lecture de l'écran « Types de TVA » : même contrat que les adaptateurs JDBC de
 * {@code tva_types} (voir les tests de contrat), dont l'arrondi du taux à deux décimales par la colonne.
 */
public class ReglesTvaEnMemoire implements RegleTvaRepository, LectureDesTypesDeTva {

    private static final OffsetDateTime CREE_LE = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    private final List<Ligne> lignes = new ArrayList<>();

    /**
     * Une règle en mémoire de test, avec un identifiant tiré au hasard.
     */
    public static RegleTva regle(CentreId centre, String type, String taux, boolean exonere, LocalDate debut, LocalDate fin,
                                 String texte, boolean active) {
        return RegleTva.reconstituee(new RegleTvaId(UUID.randomUUID()), centre, null, type, new BigDecimal(taux), exonere,
                debut, fin, texte, active);
    }

    private static RegleTva arrondie(RegleTva regle) {
        return RegleTva.reconstituee(regle.id(), regle.centre(), regle.libelle(), regle.typePrestation(),
                regle.taux().valeur().setScale(2, RoundingMode.HALF_UP), regle.exonere(), regle.debutValidite(),
                regle.finValidite(), regle.texteReference(), regle.active());
    }

    private static TypeDeTvaVue vue(Ligne l) {
        RegleTva r = l.regle;
        return new TypeDeTvaVue(r.id().valeur(), r.centre().valeur(), r.libelle(), r.taux().valeur(), r.typePrestation(),
                r.exonere(), r.debutValidite(), r.finValidite(), r.texteReference(), r.active(), CREE_LE, l.auteur);
    }

    @Override
    public List<RegleTva> duCentre(CentreId centre) {
        return lignes.stream().map(l -> l.regle).filter(r -> r.centre().equals(centre))
                .sorted(Comparator.comparing(RegleTva::debutValidite).reversed()).toList();
    }

    @Override
    public Optional<RegleTva> duCentre(CentreId centre, RegleTvaId id) {
        return ligne(centre, id).map(l -> l.regle);
    }

    @Override
    public void ajouter(RegleTva regle, String auteur) {
        lignes.add(new Ligne(arrondie(regle), auteur));
    }

    @Override
    public void remplacer(RegleTva regle) {
        ligne(regle.centre(), regle.id()).ifPresent(l -> l.regle = RegleTva.reconstituee(regle.id(), regle.centre(),
                regle.libelle(), regle.typePrestation(), regle.taux().valeur().setScale(2, RoundingMode.HALF_UP),
                regle.exonere(), regle.debutValidite(), regle.finValidite(), regle.texteReference(), l.regle.active()));
    }

    @Override
    public void desactiver(CentreId centre, RegleTvaId id) {
        ligne(centre, id).ifPresent(l -> l.regle = l.regle.desactiver());
    }

    @Override
    public PageDe<TypeDeTvaVue> page(CentreId centre, int page, int taille) {
        List<TypeDeTvaVue> tous = lignes.stream().filter(l -> l.regle.centre().equals(centre))
                .sorted(Comparator.comparing((Ligne l) -> l.regle.debutValidite()).reversed()
                        .thenComparing(l -> l.regle.libelle()))
                .map(ReglesTvaEnMemoire::vue).toList();
        return PageDe.decoupee(tous, page, taille);
    }

    @Override
    public Optional<TypeDeTvaVue> type(CentreId centre, RegleTvaId id) {
        return ligne(centre, id).map(ReglesTvaEnMemoire::vue);
    }

    private Optional<Ligne> ligne(CentreId centre, RegleTvaId id) {
        return lignes.stream().filter(l -> l.regle.centre().equals(centre) && l.regle.id().equals(id)).findFirst();
    }

    /**
     * Une règle déjà en base, éventuellement désactivée, saisie par la Comptabilité.
     */
    public ReglesTvaEnMemoire enBase(RegleTva regle) {
        lignes.add(new Ligne(regle, AUTEUR_COMPTABILITE));
        return this;
    }

    public String auteur(RegleTvaId id) {
        return lignes.stream().filter(l -> l.regle.id().equals(id)).findFirst().orElseThrow().auteur;
    }

    public int nombre() {
        return lignes.size();
    }

    private static final class Ligne {
        private final String auteur;
        private RegleTva regle;

        private Ligne(RegleTva regle, String auteur) {
            this.regle = regle;
            this.auteur = auteur;
        }
    }
}
