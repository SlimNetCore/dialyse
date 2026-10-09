package com.hemodialyse.backend.domain.comptabilite.service;

import com.hemodialyse.backend.domain.comptabilite.aggregate.EcritureComptable;
import com.hemodialyse.backend.domain.comptabilite.port.ComptabiliteStockUseCase;
import com.hemodialyse.backend.domain.comptabilite.port.EcritureComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.port.MappingComptablePort;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Inventaire;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Montant;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.Reception;
import com.hemodialyse.backend.domain.comptabilite.port.OperationsStockPort.SortiesDuJour;
import com.hemodialyse.backend.domain.comptabilite.port.PeriodeComptableRepositoryPort;
import com.hemodialyse.backend.domain.comptabilite.valueobject.JournalCode;
import com.hemodialyse.backend.domain.comptabilite.valueobject.MappingComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.OperationComptable;
import com.hemodialyse.backend.domain.comptabilite.valueobject.StatutEcriture;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service de domaine pur — met la comptabilité en accord avec le stock (inventaire permanent).
 * <ul>
 *   <li>une écriture par <b>bon de réception</b> validé et par <b>inventaire</b> clôturé (créée une seule fois) ;</li>
 *   <li>une écriture par <b>jour</b> pour les sorties. Tant qu'elle n'est ni exportée ni dans une période clôturée, elle
 *   est <b>mise à jour</b> quand les sorties du jour changent (mouvement saisi après coup, PMP recalculé). Sinon la
 *   différence fait l'objet d'une <b>écriture de complément</b> : une écriture exportée ne se modifie jamais.</li>
 * </ul>
 * Rejouable à volonté : seule la différence entre ce qui devrait être comptabilisé et ce qui l'est déjà est écrite.
 */
public class ComptabiliteStockService implements ComptabiliteStockUseCase {

    /**
     * Garde-fou : au-delà, les compléments d'une même journée sont ignorés (situation anormale à examiner).
     */
    static final int COMPLEMENTS_MAX = 50;
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EcritureComptableRepositoryPort ecritures;
    private final MappingComptablePort mappings;
    private final PeriodeComptableRepositoryPort periodes;
    private final OperationsStockPort stock;

    public ComptabiliteStockService(EcritureComptableRepositoryPort ecritures, MappingComptablePort mappings,
                                    PeriodeComptableRepositoryPort periodes, OperationsStockPort stock) {
        this.ecritures = ecritures;
        this.mappings = mappings;
        this.periodes = periodes;
        this.stock = stock;
    }

    /**
     * Identifiant source stable d'une écriture de sorties : centre, jour, rang (0 = écriture du jour, puis compléments).
     */
    static UUID sourceDuJour(UUID centerId, LocalDate jour, int rang) {
        return UUID.nameUUIDFromBytes(("STOCK_SORTIE|" + centerId + "|" + jour + "|" + rang)
                .getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Synchronisation synchroniser(UUID centerId, LocalDate du, LocalDate au, LocalDate aujourdhui) {
        if (du == null || au == null || au.isBefore(du)) {
            throw new IllegalArgumentException("La période à comptabiliser est invalide");
        }
        MappingComptable mapping = mappings.findByCenterId(centerId);
        Map<Issue, Integer> issues = new EnumMap<>(Issue.class);

        for (Reception r : stock.receptions(centerId, du, au)) {
            issues.merge(pieceUnique(centerId, mapping, OperationComptable.STOCK_RECEPTION, r.bonId(), r.date(),
                    "Réception " + r.reference(),
                    GenerateurEcritureStock.soldesReception(r.lignes(), mapping.stock()), Issue.RECEPTION), 1, Integer::sum);
        }
        for (Inventaire i : stock.inventaires(centerId, du, au)) {
            issues.merge(pieceUnique(centerId, mapping, OperationComptable.STOCK_INVENTAIRE, i.inventaireId(), i.date(),
                    "Écarts d'inventaire " + i.reference(),
                    GenerateurEcritureStock.soldesInventaire(i.ecarts(), mapping.stock()), Issue.INVENTAIRE), 1, Integer::sum);
        }
        Map<LocalDate, List<Montant>> sorties = new HashMap<>();
        for (SortiesDuJour s : stock.sorties(centerId, du, au)) {
            sorties.put(s.jour(), s.lignes());
        }
        // chaque jour de la période, y compris ceux dont les sorties ont disparu depuis la dernière comptabilisation
        for (LocalDate jour = du; !jour.isAfter(au); jour = jour.plusDays(1)) {
            issues.merge(journee(centerId, mapping, jour, sorties.getOrDefault(jour, List.of()), aujourdhui), 1,
                    Integer::sum);
        }
        return new Synchronisation(issues.getOrDefault(Issue.RECEPTION, 0), issues.getOrDefault(Issue.SORTIES, 0),
                issues.getOrDefault(Issue.INVENTAIRE, 0), issues.getOrDefault(Issue.COMPLEMENT, 0),
                issues.getOrDefault(Issue.IGNOREE, 0));
    }

    /**
     * Pièce comptabilisée une seule fois (réception, inventaire) : sans effet si elle l'est déjà.
     */
    private Issue pieceUnique(UUID centerId, MappingComptable mapping, OperationComptable operation, UUID sourceId,
                              LocalDate date, String libelle, Map<String, BigDecimal> soldes, Issue siCreee) {
        if (soldes.isEmpty() || ecritures.findBySourceId(sourceId, centerId).isPresent()) {
            return Issue.RIEN;
        }
        if (periodes.isClotured(centerId, YearMonth.from(date))) {
            return Issue.IGNOREE;
        }
        creer(centerId, mapping.journalDe(operation), date, date, libelle, soldes, sourceId);
        return siCreee;
    }

    private Issue journee(UUID centerId, MappingComptable mapping, LocalDate jour, List<Montant> lignes,
                          LocalDate aujourdhui) {
        List<EcritureComptable> famille = famille(centerId, jour);
        Map<String, BigDecimal> cible = GenerateurEcritureStock.soldesSorties(lignes, mapping.stock());
        Map<String, BigDecimal> manque = GenerateurEcritureStock.difference(cible, GenerateurEcritureStock.soldesDe(famille));
        if (manque.isEmpty()) {
            return Issue.RIEN;
        }
        if (!famille.isEmpty()) {
            EcritureComptable derniere = famille.get(famille.size() - 1);
            Map<String, BigDecimal> reste = GenerateurEcritureStock.difference(cible,
                    GenerateurEcritureStock.soldesDe(famille.subList(0, famille.size() - 1)));
            // une écriture ne peut pas être vide : si plus rien ne doit rester, on extourne par un complément
            if (modifiable(centerId, derniere) && !reste.isEmpty()) {
                ecritures.save(new EcritureComptable(derniere.getId(), centerId, derniere.getJournalCode(),
                        derniere.getDateEcriture(), derniere.getDatePiece(), derniere.getNumeroPiece(),
                        derniere.getLibelle(), GenerateurEcritureStock.lignes(reste, derniere.getLibelle(), centerId),
                        derniere.getStatut(), derniere.getSourceId()));
                return Issue.SORTIES;
            }
        }
        if (famille.size() >= COMPLEMENTS_MAX) {
            return Issue.IGNOREE;
        }
        LocalDate dateEcriture = periodes.isClotured(centerId, YearMonth.from(jour)) ? aujourdhui : jour;
        if (periodes.isClotured(centerId, YearMonth.from(dateEcriture))) {
            return Issue.IGNOREE;
        }
        boolean complement = !famille.isEmpty();
        String libelle = (complement ? "Complément sorties de stock du " : "Sorties de stock du ") + jour.format(JOUR);
        creer(centerId, mapping.journalDe(OperationComptable.STOCK_SORTIE), dateEcriture, jour, libelle, manque,
                sourceDuJour(centerId, jour, famille.size()));
        return complement ? Issue.COMPLEMENT : Issue.SORTIES;
    }

    private void creer(UUID centerId, JournalCode journal, LocalDate dateEcriture, LocalDate datePiece, String libelle,
                       Map<String, BigDecimal> soldes, UUID sourceId) {
        ecritures.save(new EcritureComptable(UUID.randomUUID(), centerId, journal, dateEcriture, datePiece,
                ecritures.nextNumeroPiece(centerId, journal, dateEcriture.getYear()), libelle,
                GenerateurEcritureStock.lignes(soldes, libelle, centerId), StatutEcriture.VALIDEE, sourceId));
    }

    /**
     * Écritures de sorties d'une journée : la première, puis ses éventuels compléments, dans l'ordre de création.
     */
    private List<EcritureComptable> famille(UUID centerId, LocalDate jour) {
        List<EcritureComptable> famille = new ArrayList<>();
        for (int rang = 0; rang <= COMPLEMENTS_MAX; rang++) {
            var trouvee = ecritures.findBySourceId(sourceDuJour(centerId, jour, rang), centerId);
            if (trouvee.isEmpty()) break;
            famille.add(trouvee.get());
        }
        return famille;
    }

    private boolean modifiable(UUID centerId, EcritureComptable ecriture) {
        return ecriture.getStatut() != StatutEcriture.EXPORTEE
                && !periodes.isClotured(centerId, YearMonth.from(ecriture.getDateEcriture()));
    }

    /**
     * Ce qu'il est advenu d'une pièce (bon, inventaire, journée de sorties).
     */
    private enum Issue {
        RIEN, RECEPTION, SORTIES, INVENTAIRE, COMPLEMENT, IGNOREE
    }
}
