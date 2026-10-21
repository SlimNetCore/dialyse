package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.application.reglement.CriteresDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.LigneDeSituation;
import com.hemodialyse.backend.contexts.facturation.application.reglement.PageDeSituations;
import com.hemodialyse.backend.contexts.facturation.application.reglement.SituationsDesFactures;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.FactureARegler;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglement;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.Reglements;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.SituationDeFacture;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire des règlements et de leur lecture : mêmes contrats que {@code ReglementsJdbc} et
 * {@code SituationsDesFacturesJdbc}. Les factures sont posées par les tests ({@link #poserLaFacture}).
 */
public class ReglementsEnMemoire implements Reglements, SituationsDesFactures {

    private final List<FactureEmise> factures = new ArrayList<>();
    private final List<Reglement> reglements = new ArrayList<>();

    public void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate date, BigDecimal total) {
        factures.add(new FactureEmise(centre, id, numero, date, total));
    }

    public List<Reglement> enregistres() {
        return List.copyOf(reglements);
    }

    private Optional<FactureEmise> facture(CentreId centre, UUID id) {
        return factures.stream().filter(f -> f.centre().equals(centre) && f.id().equals(id)).findFirst();
    }

    private List<Reglement> reglementsDe(CentreId centre, UUID id) {
        return reglements.stream().filter(r -> r.centre().equals(centre) && r.facture().equals(id)).toList();
    }

    @Override
    public Optional<FactureARegler> factureARegler(CentreId centre, UUID facture) {
        return facture(centre, facture).map(f -> new FactureARegler(f.id(), f.total(),
                reglementsDe(centre, f.id()).stream().map(Reglement::montant).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    @Override
    public Optional<Reglement> parCleDIdempotence(CentreId centre, UUID facture, String cle) {
        return reglementsDe(centre, facture).stream().filter(r -> cle.equals(r.cleDIdempotence())).findFirst();
    }

    @Override
    public void enregistrer(Reglement reglement) {
        reglements.add(reglement);
    }

    @Override
    public List<Reglement> historique(CentreId centre, UUID facture) {
        List<Reglement> liste = new ArrayList<>(reglementsDe(centre, facture));
        java.util.Collections.reverse(liste);
        liste.sort(Comparator.comparing(Reglement::date).reversed());
        return liste;
    }

    @Override
    public PageDeSituations rechercher(CriteresDeSituation criteres, int page, int taille) {
        List<LigneDeSituation> toutes = toutes(criteres);
        int debut = (int) Math.min((long) page * taille, toutes.size());
        int fin = (int) Math.min((long) debut + taille, toutes.size());
        return new PageDeSituations(toutes.subList(debut, fin), toutes.size(), page, taille);
    }

    @Override
    public List<LigneDeSituation> toutes(CriteresDeSituation criteres) {
        return factures.stream()
                .filter(f -> f.centre().equals(criteres.centre()) && !f.date().isBefore(criteres.debut())
                        && !f.date().isAfter(criteres.fin()))
                .sorted(Comparator.comparing(FactureEmise::date).thenComparing(FactureEmise::numero).reversed())
                .map(f -> ligne(f)).toList();
    }

    @Override
    public Optional<LigneDeSituation> deLaFacture(CentreId centre, UUID facture) {
        return facture(centre, facture).map(this::ligne);
    }

    private LigneDeSituation ligne(FactureEmise f) {
        List<Reglement> siens = reglementsDe(f.centre(), f.id());
        BigDecimal regle = siens.stream().map(Reglement::montant).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2);
        SituationDeFacture situation = SituationDeFacture.evaluer(f.total(), regle);
        return new LigneDeSituation(f.id(), f.numero(), "-", "-", "", null, "Non renseignee", null, "Non renseignee", null,
                "Non renseigne", f.date(), f.total().setScale(2), regle, situation.reste().setScale(2),
                situation.tropPercu().setScale(2), situation.etat(), situation.solde(),
                siens.isEmpty() ? null : siens.getLast().code(), siens.size());
    }

    private record FactureEmise(CentreId centre, UUID id, String numero, LocalDate date, BigDecimal total) {
    }
}
