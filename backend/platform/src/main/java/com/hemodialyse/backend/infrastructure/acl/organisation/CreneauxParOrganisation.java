package com.hemodialyse.backend.infrastructure.acl.organisation;

import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DescripteursDeLOrganisation;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux.Anomalie;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux.BilanDImport;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux.FicheDeCreneau;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesCreneaux.LigneAImporter;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeCreneau;
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
 * ACL de l'administration générique des référentiels vers l'Organisation (PL11-2, ADR 0158) : les créneaux sont gérés par
 * {@link GestionDesCreneaux}. Traduit les valeurs du formulaire ou du fichier ({@code code}, {@code libelle}) vers le langage publié, et
 * les refus du contexte vers les erreurs de l'administration (400 {@code VALIDATION}, 422 {@code REFERENTIAL_NOT_FOUND}, 422
 * {@code REFERENTIAL_USAGE_UNVERIFIABLE}).
 */
public class CreneauxParOrganisation implements ReferentialAdminDelegate {

    private static final String LIBELLE = ReferentielsDeLOrganisation.CRENEAU.label();

    private final GestionDesCreneaux creneaux;

    public CreneauxParOrganisation(GestionDesCreneaux creneaux) {
        this.creneaux = creneaux;
    }

    @Override
    public boolean prend(ReferentialKind kind) {
        return kind.equals(ReferentielsDeLOrganisation.CRENEAU);
    }

    @Override
    public List<ReferentialKind> descripteurs() {
        return List.of(ReferentielsDeLOrganisation.CRENEAU);
    }

    private static ReferentialEntry entree(FicheDeCreneau fiche) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put(DescripteursDeLOrganisation.CRENEAU.champs().get(0).cle(), fiche.code());
        valeurs.put(DescripteursDeLOrganisation.CRENEAU.champs().get(1).cle(), fiche.libelle());
        return new ReferentialEntry(fiche.id(), valeurs, Map.of());
    }

    private static ValidationIssue anomalie(Anomalie a) {
        return new ValidationIssue(a.ligne(), a.champ(), a.code(), a.message(), a.parametres());
    }

    private static RuntimeException traduire(RefusDeCreneau refus) {
        return switch (refus.motif()) {
            case SAISIE_INVALIDE -> new ReferentialValidationException(refus.anomalies().stream().map(CreneauxParOrganisation::anomalie).toList());
            case DEJA_EXISTANT -> new ReferentialValidationException(List.of(new ValidationIssue(0, "code", "ALREADY_EXISTS",
                    "Un élément de « " + LIBELLE + " » avec ce code existe déjà.", Map.of())));
            case INTROUVABLE -> new BusinessException("REFERENTIAL_NOT_FOUND", "Élément introuvable dans « " + LIBELLE + " » pour ce centre.");
            case CONTROLE_D_USAGE_IMPOSSIBLE -> new BusinessException("REFERENTIAL_USAGE_UNVERIFIABLE",
                    "Suppression impossible : l'usage de cet élément de « " + LIBELLE + " » n'a pas pu être vérifié. Réessayez plus tard.");
        };
    }

    @Override
    public PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
        var resultat = creneaux.lister(centerId.valeur(), search, page, size);
        return PagedResult.of(resultat.lignes().stream().map(CreneauxParOrganisation::entree).toList(), resultat.total(),
                resultat.page(), resultat.taille());
    }

    @Override
    public ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        try {
            return entree(creneaux.creer(centerId.valeur(), values.get("code"), values.get("libelle")));
        } catch (RefusDeCreneau refus) {
            throw traduire(refus);
        }
    }

    @Override
    public ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        try {
            return entree(creneaux.modifier(centerId.valeur(), id, values.get("code"), values.get("libelle")));
        } catch (RefusDeCreneau refus) {
            throw traduire(refus);
        }
    }

    @Override
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        try {
            creneaux.supprimer(centerId.valeur(), id);
        } catch (RefusDeCreneau refus) {
            throw traduire(refus);
        }
    }

    @Override
    public Outcome importRows(CentreId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
        List<LigneAImporter> lignes = rows.stream()
                .map(r -> new LigneAImporter(r.lineNumber(), r.values().get("code"), r.values().get("libelle"))).toList();
        BilanDImport bilan = creneaux.importer(centerId.valeur(), lignes, !dryRun);
        return new Outcome(bilan.aCreer(), bilan.aMettreAJour(),
                bilan.anomalies().stream().map(CreneauxParOrganisation::anomalie).toList(), bilan.appliquee());
    }
}
