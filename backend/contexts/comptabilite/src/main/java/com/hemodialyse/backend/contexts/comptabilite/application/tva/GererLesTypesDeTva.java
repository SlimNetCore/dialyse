package com.hemodialyse.backend.contexts.comptabilite.application.tva;

import com.hemodialyse.backend.contexts.comptabilite.application.PageDe;
import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTva;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaId;
import com.hemodialyse.backend.contexts.comptabilite.domain.tva.RegleTvaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Cas d'usage de l'écran « Types de TVA » ({@code /tva-types}, slice F1, ADR 0030) : lister, créer, modifier et désactiver
 * les règles de TVA du centre, avec la <b>même règle de saisie</b> que l'écran comptable (taux de 0 à 100 %, fin égale ou
 * postérieure au début, pas de chevauchement d'une règle active du même type ; KB-FAC-04 et 05 corrigés).
 */
public final class GererLesTypesDeTva {

    private final RegleTvaRepository regles;
    private final LectureDesTypesDeTva lecture;
    private final UniteDeTravail uniteDeTravail;

    public GererLesTypesDeTva(RegleTvaRepository regles, LectureDesTypesDeTva lecture, UniteDeTravail uniteDeTravail) {
        this.regles = regles;
        this.lecture = lecture;
        this.uniteDeTravail = uniteDeTravail;
    }

    public PageDe<TypeDeTvaVue> lister(CentreId centre, int page, int taille) {
        return uniteDeTravail.executer(() -> lecture.page(centre, page, taille));
    }

    /**
     * Crée un type actif au nom de son auteur et le rend tel qu'enregistré.
     */
    public TypeDeTvaVue creer(CentreId centre, Saisie saisie, String auteur) {
        RegleTvaId id = new RegleTvaId(UUID.randomUUID());
        RegleTva nouvelle = RegleTva.definir(id, centre, saisie.libelle(), saisie.typePrestation(), saisie.taux(),
                saisie.exonere(), saisie.dateDebutValidite(), saisie.dateFinValidite(), saisie.texteReference());
        return uniteDeTravail.executer(() -> {
            nouvelle.exigerSansChevauchement(regles.duCentre(centre));
            regles.ajouter(nouvelle, auteur);
            return lecture.type(centre, id).orElseThrow();
        });
    }

    /**
     * Remplace les valeurs saisies d'un type du centre ; son état et son auteur ne changent pas (KB-FAC-03 corrigé). Vide si
     * le type n'existe pas ou appartient à un autre centre : rien n'est écrit (KB-FAC-02 corrigé).
     */
    public Optional<TypeDeTvaVue> modifier(CentreId centre, RegleTvaId id, Saisie saisie) {
        return uniteDeTravail.executer(() -> regles.duCentre(centre, id).map(existante -> {
            RegleTva modifiee = existante.modifier(saisie.libelle(), saisie.typePrestation(), saisie.taux(), saisie.exonere(),
                    saisie.dateDebutValidite(), saisie.dateFinValidite(), saisie.texteReference());
            modifiee.exigerSansChevauchement(regles.duCentre(centre));
            regles.remplacer(modifiee);
            return lecture.type(centre, id).orElseThrow();
        }));
    }

    /**
     * Désactive un type du centre ; sans effet (et sans erreur) s'il est inconnu, d'un autre centre ou déjà désactivé
     * (KB-FAC-06, reproduit).
     */
    public void desactiver(CentreId centre, RegleTvaId id) {
        uniteDeTravail.executer(() -> {
            regles.desactiver(centre, id);
            return null;
        });
    }

    public record Saisie(String libelle, String typePrestation, BigDecimal taux, boolean exonere,
                         LocalDate dateDebutValidite,
                         LocalDate dateFinValidite, String texteReference) {
    }
}



