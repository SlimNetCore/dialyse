package com.hemodialyse.backend.contexts.facturation.doubles;

import com.hemodialyse.backend.contexts.facturation.application.consultation.FactureListee;
import com.hemodialyse.backend.contexts.facturation.application.consultation.ListeDesFactures;
import com.hemodialyse.backend.contexts.facturation.application.consultation.SeancesDeLaFacture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LigneFacture;
import com.hemodialyse.backend.contexts.facturation.domain.reglement.SituationDeFacture;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Double en mémoire de la lecture des factures émises et de leurs séances : même contrat que {@code ListeDesFacturesJdbc} (somme signée des
 * règlements, somme des quantités figées, tri, bornage au centre). Le patient et la caisse sont posés tels quels (pas de chaîne de payeurs).
 */
public class ListeDesFacturesEnMemoire implements ListeDesFactures, SeancesDeLaFacture {

    private record Emise(CentreId centre, UUID id, String numero, LocalDate date, LocalDate debut, LocalDate fin, UUID patient,
                         String numeroAssurance, String nom, String prenom, UUID caisseId, String caisse, BigDecimal taux,
                         BigDecimal ht, BigDecimal tva, BigDecimal ttc) {
    }

    private record Ligne(CentreId centre, UUID facture, UUID id, LigneFacture ligne) {
    }

    private record Rattachee(CentreId centre, UUID facture, SeanceRattachee seance) {
    }

    private final Map<UUID, Emise> factures = new LinkedHashMap<>();
    private final List<Ligne> lignes = new ArrayList<>();
    private final List<Object[]> reglements = new ArrayList<>();
    private final List<Rattachee> seances = new ArrayList<>();

    /** Une facture complète ; taux, HT et TVA en texte. */
    public void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate date, UUID patient, String numeroAssurance,
                               String nom, String prenom, UUID caisseId, String caisse, String taux, String ht, String tva, String ttc) {
        factures.put(id, new Emise(centre, id, numero, date, date.withDayOfMonth(1), date, patient, numeroAssurance, nom, prenom,
                caisseId, caisse, montant(taux), montant(ht), montant(tva), montant(ttc)));
    }

    /** Une facture sans TVA d'un patient anonyme sans caisse. */
    public void poserLaFacture(CentreId centre, UUID id, String numero, LocalDate date, String ttc) {
        poserLaFacture(centre, id, numero, date, UUID.randomUUID(), "-", "-", "", null, "Non renseignee", "0", ttc, "0", ttc);
    }

    public void poserLaLigne(CentreId centre, UUID facture, UUID id, LigneFacture ligne) {
        lignes.add(new Ligne(centre, facture, id, ligne));
    }

    public void poserLeReglement(CentreId centre, UUID facture, String montant) {
        reglements.add(new Object[]{centre, facture, montant(montant)});
    }

    public void rattacher(CentreId centre, UUID facture, SeanceRattachee seance) {
        seances.add(new Rattachee(centre, facture, seance));
    }

    private static BigDecimal montant(String texte) {
        return new BigDecimal(texte).setScale(2, RoundingMode.HALF_UP);
    }

    private FactureListee listee(Emise e) {
        BigDecimal regle = reglements.stream().filter(r -> r[0].equals(e.centre()) && r[1].equals(e.id()))
                .map(r -> (BigDecimal) r[2]).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        long nombre = lignes.stream().filter(l -> l.centre().equals(e.centre()) && l.facture().equals(e.id()))
                .mapToLong(l -> l.ligne().nombreDeSeances()).sum();
        SituationDeFacture situation = SituationDeFacture.evaluer(e.ttc(), regle);
        return new FactureListee(e.id(), e.numero(), e.date(), e.debut(), e.fin(), e.patient(), e.numeroAssurance(), e.nom(), e.prenom(),
                e.caisseId(), e.caisse(), nombre, e.taux(), e.ht(), e.tva(), e.ttc(), regle,
                situation.reste().setScale(2, RoundingMode.HALF_UP), situation.tropPercu().setScale(2, RoundingMode.HALF_UP),
                situation.etat());
    }

    @Override
    public List<FactureListee> entre(CentreId centre, LocalDate du, LocalDate au) {
        return factures.values().stream()
                .filter(e -> e.centre().equals(centre) && !e.date().isBefore(du) && !e.date().isAfter(au))
                .sorted(Comparator.comparing(Emise::date).reversed().thenComparing(Comparator.comparing(Emise::numero).reversed())
                        .thenComparing(e -> e.id().toString()))
                .map(this::listee).toList();
    }

    @Override
    public Optional<FactureListee> deLaFacture(CentreId centre, UUID facture) {
        return Optional.ofNullable(factures.get(facture)).filter(e -> e.centre().equals(centre)).map(this::listee);
    }

    @Override
    public List<LigneFacture> lignes(CentreId centre, UUID facture) {
        if (deLaFacture(centre, facture).isEmpty()) return List.of();
        return lignes.stream().filter(l -> l.centre().equals(centre) && l.facture().equals(facture))
                .sorted(Comparator.comparing((Ligne l) -> l.ligne().libelle()).thenComparing(l -> l.ligne().prixUnitaireHt())
                        .thenComparing(l -> l.id().toString()))
                .map(Ligne::ligne).toList();
    }

    @Override
    public List<SeanceRattachee> de(CentreId centre, UUID facture) {
        return seances.stream().filter(s -> s.centre().equals(centre) && s.facture().equals(facture)).map(Rattachee::seance)
                .sorted(Comparator.comparing(SeanceRattachee::date).thenComparing(s -> s.seance().toString())).toList();
    }
}
