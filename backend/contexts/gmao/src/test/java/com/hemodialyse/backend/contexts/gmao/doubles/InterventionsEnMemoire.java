package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.intervention.LectureInterventions;
import com.hemodialyse.backend.contexts.gmao.application.intervention.PageDInterventions;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Intervention;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionId;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.InterventionRepository;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.LigneDeCout;
import com.hemodialyse.backend.contexts.gmao.domain.intervention.Rectification;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Double in-memory du repository et du read model des interventions. Fidèle au contrat partagé
 * ({@code InterventionContrat}) : les mêmes tests s'exécutent contre l'adapter JPA. Il reproduit les contraintes de la base
 * observées au legacy (KB-GMAO-44/45) : quantité arrondie à trois décimales et prix à deux (demi supérieur), précision 12,
 * libellé de 255 caractères au plus ; l'intervention supprimée logiquement reste lisible, absente des listes, et une
 * écriture la rétablit.
 */
public class InterventionsEnMemoire implements InterventionRepository, LectureInterventions {

    private static final BigDecimal QUANTITE_MAX = new BigDecimal("999999999.999");
    private static final BigDecimal PRIX_MAX = new BigDecimal("9999999999.99");

    private final Map<InterventionId, Intervention> interventions = new LinkedHashMap<>();
    private final Set<InterventionId> supprimees = new HashSet<>();

    @Override
    public void enregistrer(Intervention intervention) {
        verifierLesContraintesDeLaBase(intervention);
        interventions.put(intervention.id(), copie(intervention));
        supprimees.remove(intervention.id());
    }

    @Override
    public Optional<Intervention> trouverParId(InterventionId id) {
        return Optional.ofNullable(interventions.get(id)).map(InterventionsEnMemoire::copie);
    }

    @Override
    public Optional<Intervention> chargerPourModification(InterventionId id) {
        return trouverParId(id);
    }

    public void supprimerLogiquement(InterventionId id) {
        supprimees.add(id);
    }

    @Override
    public Optional<Intervention> parId(InterventionId id) {
        return supprimees.contains(id) ? Optional.empty() : trouverParId(id);
    }

    @Override
    public Optional<Intervention> derniereDeLEquipement(EquipementId equipement) {
        return interventions.values().stream().filter(i -> i.equipementId().equals(equipement) && !supprimees.contains(i.id()))
                .max(Comparator.comparing(Intervention::debut, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(InterventionsEnMemoire::copie);
    }

    @Override
    public PageDInterventions<Intervention> duCentre(CentreId centre, String statut, int page, int size) {
        return page(i -> i.centreId().equals(centre) && (statut == null || statut.isBlank() || i.statut().name().equals(statut)),
                page, size);
    }

    @Override
    public PageDInterventions<Intervention> deLEquipement(EquipementId equipement, int page, int size) {
        return page(i -> i.equipementId().equals(equipement), page, size);
    }

    private PageDInterventions<Intervention> page(Predicate<Intervention> filtre, int page, int size) {
        List<Intervention> toutes = interventions.values().stream()
                .filter(i -> !supprimees.contains(i.id()))
                .filter(filtre)
                .sorted(Comparator.comparing((Intervention i) -> i.creation().le()).reversed())
                .map(InterventionsEnMemoire::copie)
                .toList();
        long debut = (long) page * size;
        List<Intervention> items = debut >= toutes.size() ? List.of()
                : toutes.subList((int) debut, (int) Math.min(toutes.size(), debut + size));
        return new PageDInterventions<>(items, toutes.size(), page, size);
    }

    private static void verifierLesContraintesDeLaBase(Intervention intervention) {
        for (LigneDeCout ligne : intervention.lignes()) {
            if (ligne.libelle().length() > 255) {
                throw new IllegalStateException("Valeur trop longue pour la colonne");
            }
            if (ligne.quantite().setScale(3, RoundingMode.HALF_UP).compareTo(QUANTITE_MAX) > 0
                    || ligne.prixUnitaire().setScale(2, RoundingMode.HALF_UP).compareTo(PRIX_MAX) > 0) {
                throw new IllegalStateException("Précision dépassée");
            }
        }
    }

    /** Ce que la base restitue : quantité à trois décimales, prix à deux (demi supérieur). */
    private static Intervention copie(Intervention i) {
        List<LigneDeCout> lignes = i.lignes().stream().map(l -> LigneDeCout.reconstituer(l.id(), l.type(), l.libelle(),
                l.quantite().setScale(3, RoundingMode.HALF_UP), l.prixUnitaire().setScale(2, RoundingMode.HALF_UP),
                l.articleStockId(), l.estAutomatique())).toList();
        List<Rectification> rectifications = List.copyOf(i.rectifications());
        return Intervention.reconstituer(i.id(), i.equipementId(), i.centreId(), i.type(), i.statut(), i.debut(), i.fin(),
                i.intervenantId(), i.description(), i.actions(), i.pieceRemplacee(), i.observations(), i.etatAvant(),
                i.etatApres(), i.priorite(), i.echeance(), i.symptome(), i.cause(), i.creation(), i.modification(),
                i.demarrage(), i.cloture(), i.annulation(), lignes, rectifications, i.taches());
    }
}
