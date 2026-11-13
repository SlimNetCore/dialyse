package com.hemodialyse.backend.infrastructure.acl.organisation;

import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles.Anomalie;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles.BilanDImport;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles.FicheDeSalle;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesSalles.LigneAImporter;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeSalle;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialValidationException;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ValidationIssue;
import com.hemodialyse.backend.infrastructure.referential.admin.port.ReferentialAdminDelegate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ACL de l'administration générique des référentiels vers l'Organisation (PL11-4, ADR 0160) : les salles sont gérées par
 * {@link GestionDesSalles}. Traduit les valeurs du formulaire ou du fichier ({@code code}, {@code nom}, {@code isolement},
 * {@code capacite}) vers le langage publié, et les refus du contexte vers les erreurs de l'administration (400 {@code VALIDATION}, 422
 * {@code REFERENTIAL_NOT_FOUND}, 422 {@code REFERENTIAL_USAGE_UNVERIFIABLE}).
 */
public class SallesParOrganisation implements ReferentialAdminDelegate {

    private static final String LIBELLE = ReferentielsDeLOrganisation.SALLE.label();

    private final GestionDesSalles salles;

    public SallesParOrganisation(GestionDesSalles salles) {
        this.salles = salles;
    }

    @Override
    public boolean prend(ReferentialKind kind) {
        return kind.equals(ReferentielsDeLOrganisation.SALLE);
    }

    @Override
    public List<ReferentialKind> descripteurs() {
        return List.of(ReferentielsDeLOrganisation.SALLE);
    }

    private static ReferentialEntry entree(FicheDeSalle fiche) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put("code", fiche.code());
        valeurs.put("nom", fiche.nom());
        valeurs.put("isolement", fiche.isolement());
        valeurs.put("capacite", fiche.capacite() == null ? null : String.valueOf(fiche.capacite()));
        return new ReferentialEntry(fiche.id(), valeurs, Map.of());
    }

    private static ValidationIssue anomalie(Anomalie a) {
        return new ValidationIssue(a.ligne(), a.champ(), a.code(), a.message(), a.parametres());
    }

    private static RuntimeException traduire(RefusDeSalle refus) {
        return switch (refus.motif()) {
            case SAISIE_INVALIDE -> new ReferentialValidationException(refus.anomalies().stream().map(SallesParOrganisation::anomalie).toList());
            case DEJA_EXISTANTE -> new ReferentialValidationException(List.of(new ValidationIssue(0, "code", "ALREADY_EXISTS",
                    "Un élément de « " + LIBELLE + " » avec ce code existe déjà.", Map.of())));
            case INTROUVABLE -> new BusinessException("REFERENTIAL_NOT_FOUND", "Élément introuvable dans « " + LIBELLE + " » pour ce centre.");
            case CONTROLE_D_USAGE_IMPOSSIBLE -> new BusinessException("REFERENTIAL_USAGE_UNVERIFIABLE",
                    "Suppression impossible : l'usage de cet élément de « " + LIBELLE + " » n'a pas pu être vérifié. Réessayez plus tard.");
        };
    }

    @Override
    public PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
        var resultat = salles.lister(centerId.valeur(), search, page, size);
        return PagedResult.of(resultat.lignes().stream().map(SallesParOrganisation::entree).toList(), resultat.total(),
                resultat.page(), resultat.taille());
    }

    @Override
    public ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        try {
            return entree(salles.creer(centerId.valeur(), values.get("code"), values.get("nom"), values.get("isolement"),
                    values.get("capacite")));
        } catch (RefusDeSalle refus) {
            throw traduire(refus);
        }
    }

    @Override
    public ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        try {
            return entree(salles.modifier(centerId.valeur(), id, values.get("code"), values.get("nom"), values.get("isolement"),
                    values.get("capacite")));
        } catch (RefusDeSalle refus) {
            throw traduire(refus);
        }
    }

    @Override
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        try {
            salles.supprimer(centerId.valeur(), id);
        } catch (RefusDeSalle refus) {
            throw traduire(refus);
        }
    }

    @Override
    public Outcome importRows(CentreId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
        List<LigneAImporter> lignes = rows.stream().map(r -> new LigneAImporter(r.lineNumber(), r.values().get("code"),
                r.values().get("nom"), r.values().get("isolement"), r.values().get("capacite"))).toList();
        BilanDImport bilan = salles.importer(centerId.valeur(), lignes, !dryRun);
        return new Outcome(bilan.aCreer(), bilan.aMettreAJour(),
                bilan.anomalies().stream().map(SallesParOrganisation::anomalie).toList(), bilan.appliquee());
    }
}
