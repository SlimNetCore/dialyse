package com.hemodialyse.backend.contexts.facturation.application.validation;

import com.hemodialyse.backend.contexts.facturation.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.facturation.application.simulation.DemandeDeSimulation;
import com.hemodialyse.backend.contexts.facturation.application.simulation.TauxDeTvaApplicable;
import com.hemodialyse.backend.contexts.facturation.domain.CentreId;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Facture;
import com.hemodialyse.backend.contexts.facturation.domain.facture.Factures;
import com.hemodialyse.backend.contexts.facturation.domain.facture.LiaisonDesSeances;
import com.hemodialyse.backend.contexts.facturation.domain.facture.NumeroDeFactureDejaPris;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ModeleDeNumerotation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.parametres.ParametresDeFacturationRepository;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.FactureSimulee;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.NumerotationPrevisionnelle;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.PeriodeDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Regroupement;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeanceFacturable;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SeancesFacturables;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.SequenceDeFacturation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.Simulation;
import com.hemodialyse.backend.contexts.facturation.domain.simulation.TauxDeTva;
import com.hemodialyse.backend.contexts.facturation.publishedlanguage.FacturationValideeEvent;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage « valider la facturation d'une période » (RG-FAC-030 à 032) : émet les factures de la simulation de la période,
 * dans une seule transaction. Le regroupement vient de la demande, jamais des paramètres du centre. Chaque facture reçoit le
 * numéro suivant de la séquence du centre pour l'année du jour (KB-FAC-23 reproduit), est datée du jour (KB-FAC-36 reproduit) et
 * fige le taux de TVA en vigueur à la fin de la période (KB-FAC-22 corrigé). Les séances sont ensuite liées à leur facture.
 * <p>
 * La validation est refusée, sans rien émettre, si une séance éligible n'a pas de forfait (KB-FAC-32) ou si un numéro réservé est
 * déjà pris (KB-FAC-24/25). Sans séance éligible, rien n'est émis, la séquence ne bouge pas et aucun événement n'est publié
 * (KB-FAC-27/29 corrigés). Sinon l'événement {@link FacturationValideeEvent} annonce l'émission (période, nombre de factures, total).
 */
public final class ValiderLaFacturation {

    private final SeancesFacturables seances;
    private final ParametresDeFacturationRepository parametres;
    private final SequenceDeFacturation sequence;
    private final TauxDeTvaApplicable tva;
    private final Factures factures;
    private final LiaisonDesSeances liaison;
    private final PublicationDeLaValidation publication;
    private final UniteDeTravail uniteDeTravail;
    private final Clock horloge;
    private final Supplier<UUID> identifiants;

    public ValiderLaFacturation(SeancesFacturables seances, ParametresDeFacturationRepository parametres,
                                SequenceDeFacturation sequence, TauxDeTvaApplicable tva, Factures factures,
                                LiaisonDesSeances liaison, PublicationDeLaValidation publication, UniteDeTravail uniteDeTravail,
                                Clock horloge, Supplier<UUID> identifiants) {
        this.seances = seances;
        this.parametres = parametres;
        this.sequence = sequence;
        this.tva = tva;
        this.factures = factures;
        this.liaison = liaison;
        this.publication = publication;
        this.uniteDeTravail = uniteDeTravail;
        this.horloge = horloge;
        this.identifiants = identifiants;
    }

    /**
     * @param auteur l'utilisateur de la session, enregistré sur les factures émises ; {@code null} si la session ne le donne pas
     */
    public ResultatDeLaValidation valider(CentreId centre, DemandeDeSimulation demande, UUID auteur) {
        return uniteDeTravail.executer(() -> {
            PeriodeDeFacturation periode = demande.periode();
            List<SeanceFacturable> facturables = seances.dansLaPeriode(centre, periode);
            if (facturables.isEmpty()) {
                return ResultatDeLaValidation.RIEN_EMIS;
            }
            Facture.exigerUnForfaitPourChaqueSeance(facturables);
            ModeleDeNumerotation modele = parametres.duCentre(centre).orElse(ParametresDeFacturation.PAR_DEFAUT).modele();
            LocalDate aujourdhui = LocalDate.now(horloge);
            TauxDeTva taux = tva.aLaDate(centre, periode.fin());
            Simulation simulation = Simulation.etablir(centre, horloge.instant(), periode,
                    Regroupement.multiForfait(demande.regroupementMultiForfait()), facturables, taux,
                    new NumerotationPrevisionnelle(modele, aujourdhui, 0));

            List<Facture> emises = new ArrayList<>();
            for (FactureSimulee groupe : simulation.factures()) {
                String numero = modele.numero(centre, aujourdhui, sequence.reserverLaSuivante(centre, aujourdhui.getYear()));
                if (factures.numeroDejaPris(centre, numero)) {
                    throw new NumeroDeFactureDejaPris(numero);
                }
                emises.add(Facture.emettre(identifiants.get(), centre, groupe, numero, periode, aujourdhui, taux, auteur));
            }
            emises.forEach(factures::enregistrer);
            emises.forEach(facture -> liaison.lier(centre, facture.id(), facture.seances()));

            int seancesFacturees = emises.stream().mapToInt(facture -> facture.seances().size()).sum();
            publication.facturationValidee(new FacturationValideeEvent(centre.valeur(), periode.debut(), periode.fin(),
                    emises.size(), seancesFacturees, emises.stream().map(Facture::totalTtc).reduce(BigDecimal.ZERO, BigDecimal::add),
                    horloge.instant()));
            return new ResultatDeLaValidation(emises.size(), seancesFacturees);
        });
    }
}
