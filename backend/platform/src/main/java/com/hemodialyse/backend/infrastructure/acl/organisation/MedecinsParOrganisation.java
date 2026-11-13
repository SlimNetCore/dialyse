package com.hemodialyse.backend.infrastructure.acl.organisation;

import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.DescripteursDeLOrganisation;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins.Anomalie;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins.BilanDImport;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins.FicheDeMedecin;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.GestionDesMedecins.LigneAImporter;
import com.hemodialyse.backend.contexts.organisation.publishedlanguage.RefusDeMedecin;
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
 * ACL de l'administration générique des référentiels vers l'Organisation (PL11-3, ADR 0159) : les médecins sont gérés par
 * {@link GestionDesMedecins}. Traduit les valeurs du formulaire ou du fichier ({@code nom}, {@code prenom}, {@code specialite}) vers le
 * langage publié, et les refus du contexte vers les erreurs de l'administration (400 {@code VALIDATION}, 422
 * {@code REFERENTIAL_NOT_FOUND}).
 */
public class MedecinsParOrganisation implements ReferentialAdminDelegate {

    private static final String LIBELLE = ReferentielsDeLOrganisation.MEDECIN.label();

    private final GestionDesMedecins medecins;

    public MedecinsParOrganisation(GestionDesMedecins medecins) {
        this.medecins = medecins;
    }

    @Override
    public boolean prend(ReferentialKind kind) {
        return kind.equals(ReferentielsDeLOrganisation.MEDECIN);
    }

    @Override
    public List<ReferentialKind> descripteurs() {
        return List.of(ReferentielsDeLOrganisation.MEDECIN);
    }

    static ReferentialEntry entree(FicheDeMedecin fiche) {
        var champs = DescripteursDeLOrganisation.MEDECIN.champs();
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put(champs.get(0).cle(), fiche.nom());
        valeurs.put(champs.get(1).cle(), fiche.prenom());
        valeurs.put(champs.get(2).cle(), fiche.specialite());
        return new ReferentialEntry(fiche.id(), valeurs, Map.of());
    }

    private static ValidationIssue anomalie(Anomalie a) {
        return new ValidationIssue(a.ligne(), a.champ(), a.code(), a.message(), a.parametres());
    }

    static RuntimeException traduire(RefusDeMedecin refus) {
        return switch (refus.motif()) {
            case SAISIE_INVALIDE -> new ReferentialValidationException(refus.anomalies().stream().map(MedecinsParOrganisation::anomalie).toList());
            case DEJA_EXISTANT -> new ReferentialValidationException(List.of(new ValidationIssue(0, "nom", "ALREADY_EXISTS",
                    "Un élément de « " + LIBELLE + " » avec ce nom + prénom existe déjà.", Map.of())));
            case INTROUVABLE -> new BusinessException("REFERENTIAL_NOT_FOUND", "Élément introuvable dans « " + LIBELLE + " » pour ce centre.");
        };
    }

    @Override
    public PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
        var resultat = medecins.lister(centerId.valeur(), search, page, size);
        return PagedResult.of(resultat.lignes().stream().map(MedecinsParOrganisation::entree).toList(), resultat.total(),
                resultat.page(), resultat.taille());
    }

    @Override
    public ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        try {
            return entree(medecins.creer(centerId.valeur(), values.get("nom"), values.get("prenom"), values.get("specialite")));
        } catch (RefusDeMedecin refus) {
            throw traduire(refus);
        }
    }

    @Override
    public ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        try {
            return entree(medecins.modifier(centerId.valeur(), id, values.get("nom"), values.get("prenom"), values.get("specialite")));
        } catch (RefusDeMedecin refus) {
            throw traduire(refus);
        }
    }

    @Override
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        try {
            medecins.desactiver(centerId.valeur(), id);
        } catch (RefusDeMedecin refus) {
            throw traduire(refus);
        }
    }

    @Override
    public Outcome importRows(CentreId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
        List<LigneAImporter> lignes = rows.stream().map(r -> new LigneAImporter(r.lineNumber(), r.values().get("nom"),
                r.values().get("prenom"), r.values().get("specialite"))).toList();
        BilanDImport bilan = medecins.importer(centerId.valeur(), lignes, !dryRun);
        return new Outcome(bilan.aCreer(), bilan.aMettreAJour(),
                bilan.anomalies().stream().map(MedecinsParOrganisation::anomalie).toList(), bilan.appliquee());
    }
}
