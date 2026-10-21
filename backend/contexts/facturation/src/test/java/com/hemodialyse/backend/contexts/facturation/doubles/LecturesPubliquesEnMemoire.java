package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.FacturesEmises;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ReglementsEncaisses;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationDuPatient;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.RepriseDesSoldes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Double en mémoire des lectures publiées de la Facturation : mêmes règles que les adaptateurs JDBC (vérifié par le contrat
 * partagé), dont l'exclusion des factures de reprise de ce qui est fourni à la Comptabilité.
 */
public class LecturesPubliquesEnMemoire implements FacturationDuPatient, FacturesEmises, ReglementsEncaisses {

    private record Facture(UUID id, UUID centre, UUID patient, String numero, LocalDate debut, LocalDate fin,
                           LocalDate dateFacturation, BigDecimal ht, BigDecimal tva, BigDecimal ttc, UUID payeur) {
        boolean deReprise() {
            return numero.startsWith(RepriseDesSoldes.PREFIXE_DES_NUMEROS);
        }
    }

    private record Reglement(UUID id, UUID centre, UUID facture, BigDecimal montant, LocalDate date, String mode, String auteur) {
    }

    private final List<Facture> factures = new ArrayList<>();
    private final List<Reglement> reglements = new ArrayList<>();

    public void poserLaFacture(UUID id, UUID centre, UUID patient, String numero, String debut, String fin,
                               String dateFacturation, String ht, String tva, String ttc, UUID payeur) {
        factures.add(new Facture(id, centre, patient, numero, LocalDate.parse(debut), LocalDate.parse(fin),
                LocalDate.parse(dateFacturation), new BigDecimal(ht), new BigDecimal(tva), new BigDecimal(ttc), payeur));
    }

    public void poserLeReglement(UUID id, UUID centre, UUID facture, String montant, String date, String mode, String auteur) {
        reglements.add(new Reglement(id, centre, facture, new BigDecimal(montant), LocalDate.parse(date), mode, auteur));
    }

    @Override
    public boolean periodeFacturee(UUID centre, UUID patient, LocalDate jour) {
        return factures.stream().anyMatch(f -> f.centre().equals(centre) && f.patient().equals(patient)
                && !f.debut().isAfter(jour) && !f.fin().isBefore(jour));
    }

    private static FactureEmise emise(Facture f) {
        return new FactureEmise(f.id(), f.numero(), f.payeur(), f.ht(), f.tva(), f.ttc(), f.dateFacturation(), f.fin(),
                "Facture");
    }

    private List<FactureEmise> emises(UUID centre, java.util.function.Predicate<Facture> filtre) {
        return factures.stream().filter(f -> f.centre().equals(centre) && !f.deReprise()).filter(filtre)
                .sorted(Comparator.comparing(Facture::dateFacturation).thenComparing(Facture::numero))
                .map(LecturesPubliquesEnMemoire::emise).toList();
    }

    @Override
    public List<FactureEmise> dontLaPeriodeEstIncluseDans(UUID centre, LocalDate debut, LocalDate fin) {
        return emises(centre, f -> !f.debut().isBefore(debut) && !f.fin().isAfter(fin));
    }

    @Override
    public List<FactureEmise> emisesEntre(UUID centre, LocalDate du, LocalDate au) {
        return emises(centre, f -> !f.dateFacturation().isBefore(du) && !f.dateFacturation().isAfter(au));
    }

    @Override
    public List<ReglementEncaisse> encaissesEntre(UUID centre, LocalDate du, LocalDate au) {
        return reglements.stream().filter(r -> r.centre().equals(centre) && !r.date().isBefore(du) && !r.date().isAfter(au))
                .filter(r -> !RepriseDesSoldes.AUTEUR_DE_LA_REPRISE.equals(r.auteur())
                        || factures.stream().noneMatch(f -> f.id().equals(r.facture()) && f.deReprise()))
                .sorted(Comparator.comparing(Reglement::date).thenComparing(Reglement::id))
                .map(r -> new ReglementEncaisse(r.id(), r.facture(), r.montant(), r.date(), r.mode())).toList();
    }
}
