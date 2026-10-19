package com.hemodialyse.backend.contexts.comptabilite.application.vente;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.MappingDuCentreVu;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.RegleMetierViolee;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.SequenceDesNumerosDePieces;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeOuverte;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FactureAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.FacturesAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.RedactionDesEcritures;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.domain.vente.ReglementsAComptabiliser;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ComptabilisationDesVentesEtReglements;
import com.hemodialyse.backend.contexts.comptabilite.publishedlanguage.ComptesClientsDesPayeurs;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Cas d'usage C5 : comptabilisation des ventes (politique P1) et des règlements (P2), reconstitution et liste des factures
 * sans écriture.
 * <p>
 * <b>Isolation (décision KB-CPT-37 du 2026-10-19)</b> : chaque facture ou règlement est comptabilisé dans sa propre
 * transaction ({@code uniteIsolee}, propagation « nouvelle transaction »). Un refus (mois clôturé, facture déséquilibrée...)
 * n'annule jamais la facture ou le règlement de l'appelant et n'empêche pas de comptabiliser les sources suivantes : il est
 * rapporté dans le {@link Bilan} et la source reste à régénérer ({@link #reconstruire}).
 * <p>
 * Les politiques P1 et P2 sont aussi offertes aux autres contextes par le langage publié
 * {@link ComptabilisationDesVentesEtReglements} (types simples, refus rapportés).
 */
public final class ComptabiliserLesVentesEtReglements implements ComptabilisationDesVentesEtReglements {

    private final EcritureComptableRepository ecritures;
    private final PeriodeComptableRepository periodes;
    private final SequenceDesNumerosDePieces numerotation;
    private final MappingDuCentreVu mappings;
    private final ComptesClientsDesPayeurs comptesDesPayeurs;
    private final FacturesAComptabiliser factures;
    private final ReglementsAComptabiliser reglements;
    private final UniteDeTravail uniteIsolee;

    public ComptabiliserLesVentesEtReglements(EcritureComptableRepository ecritures, PeriodeComptableRepository periodes,
                                              SequenceDesNumerosDePieces numerotation, MappingDuCentreVu mappings,
                                              ComptesClientsDesPayeurs comptesDesPayeurs, FacturesAComptabiliser factures,
                                              ReglementsAComptabiliser reglements, UniteDeTravail uniteIsolee) {
        this.ecritures = ecritures;
        this.periodes = periodes;
        this.numerotation = numerotation;
        this.mappings = mappings;
        this.comptesDesPayeurs = comptesDesPayeurs;
        this.factures = factures;
        this.reglements = reglements;
        this.uniteIsolee = uniteIsolee;
    }

    // ------------------------------------------------------------------ une source

    private static List<SourceNonComptabilisee> publies(Bilan bilan) {
        return bilan.echecs().stream()
                .map(e -> new SourceNonComptabilisee(e.nature(), e.source(), e.reference(), e.code(), e.message()))
                .toList();
    }

    /**
     * Écriture de ventes de la facture, datée de {@code dateEcriture} ; l'écriture existante si la facture est déjà
     * comptabilisée (R6, contrôlé avant la période). Une transaction à elle seule.
     */
    public EcritureComptable comptabiliserUneVente(CentreId centre, FactureAComptabiliser facture, LocalDate dateEcriture) {
        return uniteIsolee.executer(() -> {
            numerotation.verrouillerSource(centre, facture.id());
            Optional<EcritureComptable> existante = ecritures.parSource(centre, facture.id());
            if (existante.isPresent()) return existante.get();
            PeriodeOuverte periode = periodeOuverte(centre, dateEcriture);
            MappingComptable mapping = mappings.effectif(centre);
            String compteClient = RedactionDesEcritures.compteClient(mapping, facture.payeur(), compteDuPayeur(centre, facture.payeur()));
            NumeroDePiece numero = prochainNumero(centre, mapping.journalDe(OperationComptable.VENTE), dateEcriture.getYear());
            EcritureComptable ecriture = RedactionDesEcritures.ventes(centre, mapping, compteClient, facture, dateEcriture,
                    numero, periode, UUID::randomUUID);
            ecritures.enregistrer(ecriture);
            return ecriture;
        });
    }

    // ------------------------------------------------------------------ langage publié (P1, P2)

    @Override
    public List<SourceNonComptabilisee> facturationValidee(UUID centre, LocalDate debut, LocalDate fin) {
        return publies(comptabiliserLesVentesValidees(new CentreId(centre), debut, fin));
    }

    @Override
    public List<SourceNonComptabilisee> reglementEnregistre(UUID centre, UUID facture, BigDecimal montant, LocalDate date,
                                                            String auteur) {
        return publies(comptabiliserLeReglementEnregistre(new CentreId(centre), facture, montant, date, auteur));
    }

    /**
     * Écriture du règlement ; elle crédite le compte que l'écriture de la facture a débité, sinon le compte client résolu
     * (R18, R19). L'écriture existante si le règlement est déjà comptabilisé. Une transaction à elle seule.
     */
    public EcritureComptable comptabiliserUnReglement(CentreId centre, ReglementAComptabiliser reglement) {
        return uniteIsolee.executer(() -> {
            numerotation.verrouillerSource(centre, reglement.id());
            Optional<EcritureComptable> existante = ecritures.parSource(centre, reglement.id());
            if (existante.isPresent()) return existante.get();
            PeriodeOuverte periode = periodeOuverte(centre, reglement.date());
            MappingComptable mapping = mappings.effectif(centre);
            CodeJournal journal = RedactionDesEcritures.journalDuReglement(mapping, reglement);
            NumeroDePiece numero = prochainNumero(centre, journal, reglement.date().getYear());
            String compteClient = (reglement.facture() == null ? Optional.<EcritureComptable>empty()
                    : ecritures.parSource(centre, reglement.facture()))
                    .flatMap(RedactionDesEcritures::compteDebite)
                    .orElseGet(() -> RedactionDesEcritures.compteClient(mapping, reglement.payeur(),
                            compteDuPayeur(centre, reglement.payeur())));
            EcritureComptable ecriture = RedactionDesEcritures.reglement(centre, mapping, compteClient, reglement, numero,
                    periode, UUID::randomUUID);
            ecritures.enregistrer(ecriture);
            return ecriture;
        });
    }

    // ------------------------------------------------------------------ politiques et reconstitution

    /**
     * P1 : une écriture de ventes par facture dont la période est incluse dans la période validée, datée de la fin de
     * cette période. Chaque facture est indépendante ; les refus sont rapportés.
     */
    public Bilan comptabiliserLesVentesValidees(CentreId centre, LocalDate debut, LocalDate fin) {
        List<FactureAComptabiliser> aTraiter = uniteIsolee.executer(() -> factures.dontLaPeriodeEstIncluseDans(centre, debut, fin));
        Bilan bilan = new Bilan();
        aTraiter.forEach(f -> bilan.vente(f, () -> comptabiliserUneVente(centre, f, fin)));
        return bilan;
    }

    /**
     * P2 : retrouve l'identifiant du règlement enregistré (B13) et le comptabilise en banque ; le règlement solde le compte
     * que la facture a débité.
     */
    public Bilan comptabiliserLeReglementEnregistre(CentreId centre, UUID facture, BigDecimal montant, LocalDate date,
                                                    String auteur) {
        UUID id = uniteIsolee.executer(() -> reglements.identifiantDuReglement(centre, facture, montant, date, auteur));
        ReglementAComptabiliser reglement = new ReglementAComptabiliser(id, facture, montant, date, "BANQUE",
                "Règlement facture", null);
        Bilan bilan = new Bilan();
        bilan.reglement(reglement, () -> comptabiliserUnReglement(centre, reglement));
        return bilan;
    }

    /**
     * Régénère les écritures manquantes des factures émises et des règlements encaissés entre {@code du} et {@code au}
     * (rejouable, sans doublon). Une vente est datée de la fin de sa période facturée, comme par P1 ; une source refusée
     * n'arrête pas les autres et figure dans le bilan.
     */
    public Bilan reconstruire(CentreId centre, LocalDate du, LocalDate au) {
        List<FactureAComptabiliser> facturees = uniteIsolee.executer(() -> factures.factureesEntre(centre, du, au));
        List<ReglementAComptabiliser> encaisses = uniteIsolee.executer(() -> reglements.encaissesEntre(centre, du, au));
        Bilan bilan = new Bilan();
        facturees.forEach(f -> bilan.vente(f, () -> comptabiliserUneVente(centre, f,
                f.finDePeriode() != null ? f.finDePeriode() : f.dateFacturation())));
        encaisses.forEach(r -> bilan.reglement(r, () -> comptabiliserUnReglement(centre, r)));
        return bilan;
    }

    /**
     * Factures émises entre {@code du} et {@code au} sans écriture de ventes : celles à régénérer.
     */
    public List<FactureAComptabiliser> facturesSansEcriture(CentreId centre, LocalDate du, LocalDate au) {
        return uniteIsolee.executer(() -> factures.sansEcritureEntre(centre, du, au));
    }

    // ------------------------------------------------------------------ règles communes

    /**
     * La preuve que le mois de la date est ouvert (R7, refus {@code PERIODE_CLOTUREE}).
     */
    private PeriodeOuverte periodeOuverte(CentreId centre, LocalDate date) {
        return periodes.duMoisOuOuverte(centre, YearMonth.from(date)).exigerOuverte();
    }

    private NumeroDePiece prochainNumero(CentreId centre, CodeJournal journal, int annee) {
        return NumeroDePiece.suivant(journal, annee, numerotation.verrouillerEtLireLesNumeros(centre, journal, annee));
    }

    private Optional<String> compteDuPayeur(CentreId centre, UUID payeur) {
        return payeur == null ? Optional.empty() : comptesDesPayeurs.compteDuPayeur(centre.valeur(), payeur);
    }

    // ------------------------------------------------------------------ bilan

    /**
     * Une source non comptabilisée et la raison du refus.
     */
    public record Echec(String nature, UUID source, String reference, String code, String message) {
    }

    /**
     * Ce qu'a donné un traitement : sources comptabilisées (créées ou déjà présentes) et sources refusées.
     */
    public static final class Bilan {
        private final List<Echec> echecs = new ArrayList<>();
        private int ventes;
        private int reglements;

        void vente(FactureAComptabiliser facture, Supplier<EcritureComptable> action) {
            if (tenter("FACTURE", facture.id(), facture.numero(), action)) ventes++;
        }

        void reglement(ReglementAComptabiliser reglement, Supplier<EcritureComptable> action) {
            if (tenter("REGLEMENT", reglement.id(), String.valueOf(reglement.facture()), action)) reglements++;
        }

        private boolean tenter(String nature, UUID source, String reference, Supplier<EcritureComptable> action) {
            try {
                action.get();
                return true;
            } catch (RegleMetierViolee refus) {
                echecs.add(new Echec(nature, source, reference, refus.code(), refus.getMessage()));
            } catch (RuntimeException erreur) {
                echecs.add(new Echec(nature, source, reference, "ERREUR_TECHNIQUE", erreur.getMessage()));
            }
            return false;
        }

        public int ventes() {
            return ventes;
        }

        public int reglements() {
            return reglements;
        }

        public List<Echec> echecs() {
            return List.copyOf(echecs);
        }
    }
}













