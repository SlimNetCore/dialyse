package com.hemodialyse.backend.contexts.comptabilite.application.stock;

import com.hemodialyse.backend.contexts.comptabilite.application.UniteDeTravail;
import com.hemodialyse.backend.contexts.comptabilite.application.mapping.MappingDuCentreVu;
import com.hemodialyse.backend.contexts.comptabilite.domain.CentreId;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.EcritureComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.NumeroDePiece;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.SequenceDesNumerosDePieces;
import com.hemodialyse.backend.contexts.comptabilite.domain.ecriture.StatutEcriture;
import com.hemodialyse.backend.contexts.comptabilite.domain.journal.CodeJournal;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.MappingComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.mapping.OperationComptable;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeCloturee;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeComptableRepository;
import com.hemodialyse.backend.contexts.comptabilite.domain.periode.PeriodeOuverte;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock.Inventaire;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock.Montant;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock.Reception;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.OperationsDeStock.SortiesDuJour;
import com.hemodialyse.backend.contexts.comptabilite.domain.stock.SoldesDuStock;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cas d'usage C6 : met la comptabilité d'un centre en accord avec son stock sur une période (inventaire permanent),
 * rejouable sans doublon ; une seule transaction par centre.
 * <ul>
 *   <li>une écriture par bon de réception validé et par inventaire clôturé, créée une seule fois ;</li>
 *   <li>une écriture par jour de sorties, mise à jour en place tant qu'elle n'est ni exportée ni dans un mois clos (B9),
 *   sinon complétée par une écriture de complément (datée du jour d'origine, ou d'aujourd'hui si ce mois est clos : B8).</li>
 * </ul>
 * <b>Décision KB-CPT-42 et 44 (2026-10-19)</b> : les pièces non comptabilisées parce que leur mois est clos, les articles
 * reçus ou sortis sans valeur et les journées au-delà du plafond de compléments sont rapportés dans
 * {@link Synchronisation#echecs()} (les compteurs restent ceux du legacy).
 */
public final class ComptabiliserLeStock {

    /**
     * Garde-fou : au-delà, les compléments d'une même journée sont refusés (situation anormale à examiner).
     */
    static final int COMPLEMENTS_MAX = 50;
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EcritureComptableRepository ecritures;
    private final PeriodeComptableRepository periodes;
    private final SequenceDesNumerosDePieces numerotation;
    private final MappingDuCentreVu mappings;
    private final OperationsDeStock stock;
    private final UniteDeTravail uniteDeTravail;

    public ComptabiliserLeStock(EcritureComptableRepository ecritures, PeriodeComptableRepository periodes,
                                SequenceDesNumerosDePieces numerotation, MappingDuCentreVu mappings,
                                OperationsDeStock stock, UniteDeTravail uniteDeTravail) {
        this.ecritures = ecritures;
        this.periodes = periodes;
        this.numerotation = numerotation;
        this.mappings = mappings;
        this.stock = stock;
        this.uniteDeTravail = uniteDeTravail;
    }

    /**
     * Source stable d'une écriture de sorties : centre, jour, rang (0 = écriture du jour, puis compléments).
     */
    static UUID sourceDuJour(CentreId centre, LocalDate jour, int rang) {
        return UUID.nameUUIDFromBytes(("STOCK_SORTIE|" + centre.valeur() + "|" + jour + "|" + rang)
                .getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Centres à traiter par la tâche de nuit.
     */
    public List<CentreId> centres() {
        return uniteDeTravail.executer(stock::centres);
    }

    /**
     * @param aujourdhui date du jour, portée par un complément de sorties d'un mois clos
     */
    public Synchronisation synchroniser(CentreId centre, LocalDate du, LocalDate au, LocalDate aujourdhui) {
        if (du == null || au == null || au.isBefore(du)) {
            throw new IllegalArgumentException("La période à comptabiliser est invalide");
        }
        return uniteDeTravail.executer(() -> {
            Bilan bilan = new Bilan(centre);
            MappingComptable mapping = mappings.effectif(centre);
            for (Reception r : stock.receptions(centre, du, au)) {
                if (!r.articlesSansValeur().isEmpty()) {
                    bilan.echec("RECEPTION", r.bonId(), r.reference(), "SANS_VALEUR",
                            "Article(s) reçu(s) sans prix : " + String.join(", ", r.articlesSansValeur()));
                }
                bilan.issue(pieceUnique(centre, mapping, OperationComptable.STOCK_RECEPTION, r.bonId(), r.date(),
                        "Réception " + r.reference(), SoldesDuStock.reception(r.lignes(), mapping.stock()),
                        Issue.RECEPTION), "RECEPTION", r.bonId(), r.reference(), r.date());
            }
            for (Inventaire i : stock.inventaires(centre, du, au)) {
                bilan.issue(pieceUnique(centre, mapping, OperationComptable.STOCK_INVENTAIRE, i.inventaireId(), i.date(),
                        "Écarts d'inventaire " + i.reference(), SoldesDuStock.inventaire(i.ecarts(), mapping.stock()),
                        Issue.INVENTAIRE), "INVENTAIRE", i.inventaireId(), i.reference(), i.date());
            }
            Map<LocalDate, SortiesDuJour> sorties = new HashMap<>();
            stock.sorties(centre, du, au).forEach(s -> sorties.put(s.jour(), s));
            // chaque jour de la période, y compris ceux dont les sorties ont disparu depuis la dernière comptabilisation
            for (LocalDate jour = du; !jour.isAfter(au); jour = jour.plusDays(1)) {
                SortiesDuJour duJour = sorties.get(jour);
                if (duJour != null && !duJour.articlesSansValeur().isEmpty()) {
                    bilan.echec("SORTIES", sourceDuJour(centre, jour, 0), jour.toString(), "SANS_VALEUR",
                            "Article(s) sorti(s) sans prix ni PMP : " + String.join(", ", duJour.articlesSansValeur()));
                }
                List<Montant> lignes = duJour == null ? List.of() : duJour.lignes();
                Resultat r = journee(centre, mapping, jour, lignes, aujourdhui);
                bilan.issue(r.issue(), "SORTIES", sourceDuJour(centre, jour, 0), jour.toString(), r.moisRefuse());
            }
            return bilan.synchronisation();
        });
    }

    private Issue pieceUnique(CentreId centre, MappingComptable mapping, OperationComptable operation, UUID source,
                              LocalDate date, String libelle, Map<String, BigDecimal> soldes, Issue siCreee) {
        if (soldes.isEmpty()) {
            return Issue.RIEN;
        }
        numerotation.verrouillerSource(centre, source);
        if (ecritures.parSource(centre, source).isPresent()) {
            return Issue.RIEN;
        }
        if (estClos(centre, date)) {
            return Issue.IGNOREE;
        }
        creer(centre, mapping.journalDe(operation), date, date, libelle, soldes, source);
        return siCreee;
    }

    private Resultat journee(CentreId centre, MappingComptable mapping, LocalDate jour, List<Montant> lignes,
                             LocalDate aujourdhui) {
        List<EcritureComptable> famille = famille(centre, jour);
        Map<String, BigDecimal> cible = SoldesDuStock.sorties(lignes, mapping.stock());
        Map<String, BigDecimal> manque = SoldesDuStock.difference(cible, SoldesDuStock.deja(famille));
        if (manque.isEmpty()) {
            return new Resultat(Issue.RIEN, null);
        }
        if (!famille.isEmpty()) {
            EcritureComptable derniere = famille.get(famille.size() - 1);
            Map<String, BigDecimal> reste = SoldesDuStock.difference(cible,
                    SoldesDuStock.deja(famille.subList(0, famille.size() - 1)));
            // une écriture ne peut pas être vide : si plus rien ne doit rester, on contre-passe par un complément
            if (modifiable(centre, derniere) && !reste.isEmpty()) {
                ecritures.enregistrer(derniere.reviserLesSorties(
                        SoldesDuStock.lignes(reste, derniere.libelle(), centre, UUID::randomUUID),
                        periodeOuverte(centre, derniere.dateEcriture())));
                return new Resultat(Issue.SORTIES, null);
            }
        }
        if (famille.size() >= COMPLEMENTS_MAX) {
            return new Resultat(Issue.PLAFOND, null);
        }
        LocalDate dateEcriture = estClos(centre, jour) ? aujourdhui : jour;
        if (estClos(centre, dateEcriture)) {
            return new Resultat(Issue.IGNOREE, dateEcriture);
        }
        boolean complement = !famille.isEmpty();
        String libelle = (complement ? "Complément sorties de stock du " : "Sorties de stock du ") + jour.format(JOUR);
        creer(centre, mapping.journalDe(OperationComptable.STOCK_SORTIE), dateEcriture, jour, libelle, manque,
                sourceDuJour(centre, jour, famille.size()));
        return new Resultat(complement ? Issue.COMPLEMENT : Issue.SORTIES, null);
    }

    private void creer(CentreId centre, CodeJournal journal, LocalDate dateEcriture, LocalDate datePiece, String libelle,
                       Map<String, BigDecimal> soldes, UUID source) {
        NumeroDePiece numero = NumeroDePiece.suivant(journal, dateEcriture.getYear(),
                numerotation.verrouillerEtLireLesNumeros(centre, journal, dateEcriture.getYear()));
        ecritures.enregistrer(EcritureComptable.generer(UUID.randomUUID(), centre, journal, dateEcriture, datePiece,
                numero, libelle, SoldesDuStock.lignes(soldes, libelle, centre, UUID::randomUUID), source,
                periodeOuverte(centre, dateEcriture)));
    }

    /**
     * La preuve que le mois de la date est ouvert ; les appelants ont déjà écarté les mois clos (pièce ignorée et
     * rapportée), l'écriture le vérifie à nouveau à sa naissance (R7).
     */
    private PeriodeOuverte periodeOuverte(CentreId centre, LocalDate date) {
        return periodes.duMoisOuOuverte(centre, YearMonth.from(date)).exigerOuverte();
    }

    /**
     * Écritures de sorties d'une journée : la première, puis ses compléments, dans l'ordre de création.
     */
    private List<EcritureComptable> famille(CentreId centre, LocalDate jour) {
        List<EcritureComptable> famille = new ArrayList<>();
        for (int rang = 0; rang <= COMPLEMENTS_MAX; rang++) {
            UUID source = sourceDuJour(centre, jour, rang);
            numerotation.verrouillerSource(centre, source);
            var trouvee = ecritures.parSource(centre, source);
            if (trouvee.isEmpty()) break;
            famille.add(trouvee.get());
        }
        return famille;
    }

    private boolean modifiable(CentreId centre, EcritureComptable ecriture) {
        return ecriture.statut() != StatutEcriture.EXPORTEE && !estClos(centre, ecriture.dateEcriture());
    }

    private boolean estClos(CentreId centre, LocalDate date) {
        return periodes.duMois(centre, YearMonth.from(date)).filter(p -> p.estCloturee()).isPresent();
    }

    // ------------------------------------------------------------------ résultat

    private enum Issue {
        RIEN, RECEPTION, SORTIES, INVENTAIRE, COMPLEMENT, IGNOREE, PLAFOND
    }

    /**
     * Ce qu'il est advenu d'une journée, et le mois clos qui l'a refusée le cas échéant.
     */
    private record Resultat(Issue issue, LocalDate moisRefuse) {
    }

    /**
     * Une pièce non (entièrement) comptabilisée et la raison.
     */
    public record Echec(String nature, UUID source, String reference, String code, String message) {
    }

    /**
     * @param receptions   écritures de réception créées
     * @param joursSorties écritures journalières de sorties créées ou mises à jour
     * @param inventaires  écritures d'écarts d'inventaire créées
     * @param complements  écritures de complément créées (journée exportée ou mois clos)
     * @param ignorees     pièces non comptabilisées parce que leur mois est clos
     * @param echecs       détail des pièces ignorées, des articles sans valeur et des journées au plafond de compléments
     */
    public record Synchronisation(int receptions, int joursSorties, int inventaires, int complements, int ignorees,
                                  List<Echec> echecs) {
        public Synchronisation {
            echecs = echecs == null ? List.of() : List.copyOf(echecs);
        }
    }

    private static final class Bilan {
        private final CentreId centre;
        private final Map<Issue, Integer> issues = new HashMap<>();
        private final List<Echec> echecs = new ArrayList<>();

        Bilan(CentreId centre) {
            this.centre = centre;
        }

        void echec(String nature, UUID source, String reference, String code, String message) {
            echecs.add(new Echec(nature, source, reference, code, message));
        }

        void issue(Issue issue, String nature, UUID source, String reference, LocalDate date) {
            if (issue == Issue.PLAFOND) {
                echec(nature, source, reference, "COMPLEMENTS_MAX", "Plus de " + COMPLEMENTS_MAX
                        + " compléments pour cette journée : situation à examiner, rien n'a été écrit");
                issues.merge(Issue.IGNOREE, 1, Integer::sum);
                return;
            }
            if (issue == Issue.IGNOREE) {
                PeriodeCloturee refus = new PeriodeCloturee(centre, YearMonth.from(date));
                echec(nature, source, reference, refus.code(), refus.getMessage());
            }
            issues.merge(issue, 1, Integer::sum);
        }

        Synchronisation synchronisation() {
            return new Synchronisation(issues.getOrDefault(Issue.RECEPTION, 0), issues.getOrDefault(Issue.SORTIES, 0),
                    issues.getOrDefault(Issue.INVENTAIRE, 0), issues.getOrDefault(Issue.COMPLEMENT, 0),
                    issues.getOrDefault(Issue.IGNOREE, 0), echecs);
        }
    }
}






