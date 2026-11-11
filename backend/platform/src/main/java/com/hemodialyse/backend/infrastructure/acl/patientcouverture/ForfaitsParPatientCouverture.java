package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.Anomalie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.BilanDImport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.FicheDeForfait;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDuCatalogueDeForfaits.LigneAImporter;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeForfait;
import com.hemodialyse.backend.contexts.sharedkernel.domain.PagedResult;
import com.hemodialyse.backend.application.erreur.BusinessException;
import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
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
 * ACL de l'administration générique des référentiels vers patient-couverture (RF2) : le catalogue de forfaits est désormais géré par
 * {@link GestionDuCatalogueDeForfaits}. Traduit les valeurs du formulaire ou du fichier ({@code code}, {@code libelle}, {@code prix})
 * vers le langage publié, et les refus du contexte vers les erreurs de l'administration (mêmes codes et mêmes messages qu'avant :
 * {@code REFERENTIAL_VALIDATION}, {@code REFERENTIAL_NOT_FOUND}, {@code REFERENTIAL_IN_USE}).
 */
public class ForfaitsParPatientCouverture implements ReferentialAdminDelegate {

    private static final String LIBELLE = ReferentialKind.FORFAIT.label();

    private final GestionDuCatalogueDeForfaits forfaits;

    public ForfaitsParPatientCouverture(GestionDuCatalogueDeForfaits forfaits) {
        this.forfaits = forfaits;
    }

    @Override
    public boolean prend(ReferentialKind kind) {
        return kind == ReferentialKind.FORFAIT;
    }

    private static ReferentialEntry entree(FicheDeForfait fiche) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put("code", fiche.code());
        valeurs.put("libelle", fiche.libelle());
        valeurs.put("prix", fiche.prix() == null ? null : fiche.prix().toPlainString());
        return new ReferentialEntry(fiche.id(), valeurs, Map.of());
    }

    private static ValidationIssue anomalie(Anomalie a) {
        return new ValidationIssue(a.ligne(), a.champ(), a.code(), a.message(), a.parametres());
    }

    /** Les refus du contexte, dans les erreurs de l'administration générique. */
    private static RuntimeException traduire(RefusDeForfait refus) {
        return switch (refus.motif()) {
            case SAISIE_INVALIDE -> new ReferentialValidationException(refus.anomalies().stream().map(ForfaitsParPatientCouverture::anomalie).toList());
            case DEJA_EXISTANT -> new ReferentialValidationException(List.of(new ValidationIssue(0, "code", "ALREADY_EXISTS",
                    "Un élément de « " + LIBELLE + " » avec ce code existe déjà.", Map.of())));
            case INTROUVABLE -> new BusinessException("REFERENTIAL_NOT_FOUND", "Élément introuvable dans « " + LIBELLE + " » pour ce centre.");
            case UTILISE -> new BusinessException("REFERENTIAL_IN_USE", "Suppression impossible : cet élément de « " + LIBELLE
                    + " » est encore utilisé (" + refus.references() + " référence(s) : patients, prises en charge, séances ou autres référentiels).");
        };
    }

    @Override
    public PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
        var resultat = forfaits.lister(centerId.valeur(), search, page, size);
        return PagedResult.of(resultat.lignes().stream().map(ForfaitsParPatientCouverture::entree).toList(), resultat.total(),
                resultat.page(), resultat.taille());
    }

    @Override
    public ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        try {
            return entree(forfaits.creer(centerId.valeur(), values.get("code"), values.get("libelle"), values.get("prix")));
        } catch (RefusDeForfait refus) {
            throw traduire(refus);
        }
    }

    @Override
    public ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        try {
            return entree(forfaits.modifier(centerId.valeur(), id, values.get("code"), values.get("libelle"), values.get("prix")));
        } catch (RefusDeForfait refus) {
            throw traduire(refus);
        }
    }

    @Override
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        try {
            forfaits.supprimer(centerId.valeur(), id);
        } catch (RefusDeForfait refus) {
            throw traduire(refus);
        }
    }

    @Override
    public Outcome importRows(CentreId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
        List<LigneAImporter> lignes = rows.stream()
                .map(r -> new LigneAImporter(r.lineNumber(), r.values().get("code"), r.values().get("libelle"), r.values().get("prix"))).toList();
        BilanDImport bilan = forfaits.importer(centerId.valeur(), lignes, !dryRun);
        return new Outcome(bilan.aCreer(), bilan.aMettreAJour(),
                bilan.anomalies().stream().map(ForfaitsParPatientCouverture::anomalie).toList(), bilan.appliquee());
    }
}
