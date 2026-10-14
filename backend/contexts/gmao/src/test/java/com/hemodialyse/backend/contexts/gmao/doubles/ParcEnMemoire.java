package com.hemodialyse.backend.contexts.gmao.doubles;

import com.hemodialyse.backend.contexts.gmao.application.equipement.EquipementVue;
import com.hemodialyse.backend.contexts.gmao.application.equipement.LectureEquipements;
import com.hemodialyse.backend.contexts.gmao.application.equipement.PageDEquipements;
import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.ChangementDeStatut;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodeEquipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.CodesEquipementDuCentre;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.Equipement;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementRepository;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.PrixAcquisition;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Double in-memory du repository, du read model et des codes du parc. Fidèle au contrat partagé ({@code EquipementContrat}) :
 * les mêmes tests s'exécutent contre l'adapter JPA. Il reproduit les contraintes de la base observées au legacy : longueurs
 * refusées (KB-GMAO-35), prix ramené à deux décimales (demi supérieur) et précision 14, unicité (centre, code) sur toutes les
 * lignes, supprimées comprises (KB-GMAO-33), suppression logique que seule la base sait poser (KB-GMAO-34).
 */
public class ParcEnMemoire implements EquipementRepository, LectureEquipements, CodesEquipementDuCentre {

    private static final BigDecimal PRIX_MAX = new BigDecimal("999999999999.99");

    private final Map<EquipementId, Equipement> equipements = new LinkedHashMap<>();
    private final Map<EquipementId, List<ChangementDeStatut>> historiques = new LinkedHashMap<>();
    private final Set<EquipementId> supprimes = new HashSet<>();

    @Override
    public void enregistrer(Equipement equipement) {
        verifierLesContraintesDeLaBase(equipement);
        List<ChangementDeStatut> nouveaux = equipement.tirerChangementsDeStatut();
        equipements.put(equipement.id(), copie(equipement));
        historiques.computeIfAbsent(equipement.id(), id -> new ArrayList<>()).addAll(nouveaux);
        supprimes.remove(equipement.id());
    }

    @Override
    public Optional<Equipement> trouverParId(EquipementId id) {
        return Optional.ofNullable(equipements.get(id)).map(ParcEnMemoire::copie);
    }

    @Override
    public List<ChangementDeStatut> historiqueDe(EquipementId id) {
        return historiques.getOrDefault(id, List.of()).stream().sorted(Comparator.comparing(ChangementDeStatut::le)).toList();
    }

    @Override
    public boolean estUtilise(CentreId centre, CodeEquipement code) {
        return equipements.values().stream().anyMatch(e -> !supprimes.contains(e.id()) && e.centreId().equals(centre)
                && e.code().equals(code));
    }

    @Override
    public Optional<EquipementVue> parId(EquipementId id) {
        return Optional.ofNullable(equipements.get(id)).map(EquipementVue::depuis);
    }

    @Override
    public PageDEquipements duCentre(CentreId centre, String statut, int page, int size) {
        List<EquipementVue> tous = equipements.values().stream()
                .filter(e -> !supprimes.contains(e.id()) && e.centreId().equals(centre))
                .filter(e -> statut == null || statut.isBlank() || e.statut().name().equals(statut))
                .sorted(Comparator.comparing(Equipement::creeLe).reversed())
                .map(EquipementVue::depuis)
                .toList();
        long debut = (long) page * size;
        List<EquipementVue> items = debut >= tous.size() ? List.of()
                : tous.subList((int) debut, (int) Math.min(tous.size(), debut + size));
        return new PageDEquipements(items, tous.size(), page, size);
    }

    @Override
    public List<EquipementVue> parcDuCentre(CentreId centre) {
        return equipements.values().stream().filter(e -> !supprimes.contains(e.id()) && e.centreId().equals(centre))
                .map(EquipementVue::depuis).toList();
    }

    /** Ce que la base sait faire et le domaine non : poser {@code deleted_at}. */
    public void supprimerLogiquement(EquipementId id) {
        supprimes.add(id);
    }

    private void verifierLesContraintesDeLaBase(Equipement e) {
        if (e.code().valeur().length() > 50 || e.designation().length() > 255
                || trop(e.fabricant(), 100) || trop(e.modele(), 100) || trop(e.numeroSerie(), 100)
                || trop(e.localisation(), 255)) {
            throw new IllegalStateException("Valeur trop longue pour la colonne");
        }
        if (e.prixAcquisition() != null
                && e.prixAcquisition().valeur().setScale(2, RoundingMode.HALF_UP).compareTo(PRIX_MAX) > 0) {
            throw new IllegalStateException("Précision du prix dépassée");
        }
        boolean codeDejaPris = equipements.values().stream().anyMatch(autre -> !autre.id().equals(e.id())
                && autre.centreId().equals(e.centreId()) && autre.code().equals(e.code()));
        if (codeDejaPris) {
            throw new IllegalStateException("Contrainte d'unicité (centre, code)");
        }
    }

    private static boolean trop(String valeur, int longueur) {
        return valeur != null && valeur.length() > longueur;
    }

    /** Ce que la base restitue : prix ramené à deux décimales (demi supérieur). */
    private static Equipement copie(Equipement e) {
        PrixAcquisition prix = e.prixAcquisition() == null ? null
                : new PrixAcquisition(e.prixAcquisition().valeur().setScale(2, RoundingMode.HALF_UP));
        return Equipement.reconstituer(e.id(), e.centreId(), e.code(), e.designation(), e.type(), e.fabricant(), e.modele(),
                e.numeroSerie(), e.dateInstallation(), e.statut(), e.localisation(), e.observations(), e.salleId(), prix,
                e.creeLe(), e.modifieLe(), e.creePar(), e.modifiePar());
    }
}
