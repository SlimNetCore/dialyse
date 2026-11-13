package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.Anomalie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.BilanDImport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.FicheDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.LigneAImporter;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.Niveau;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesPayeurs.SaisieDePayeur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDePayeur;
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
 * ACL de l'administration générique des référentiels vers patient-couverture (RF3a) : la chaîne des payeurs (caisses d'assurance,
 * agences, centres payeurs) est désormais gérée par {@link GestionDesPayeurs}. Traduit les valeurs du formulaire ou du fichier
 * ({@code code}, {@code nom}, {@code typeCaisse}, {@code adresse}, {@code caisse}, {@code agence}) vers le langage publié, et les refus du
 * contexte vers les erreurs de l'administration (mêmes codes et mêmes messages qu'avant : {@code REFERENTIAL_VALIDATION},
 * {@code REFERENTIAL_NOT_FOUND}, {@code REFERENTIAL_IN_USE}).
 */
public class PayeursParPatientCouverture implements ReferentialAdminDelegate {

    private static final ReferentialKind CAISSE = ReferentielsDePatientCouverture.CAISSE;
    private static final ReferentialKind AGENCE = ReferentielsDePatientCouverture.AGENCE;
    private static final ReferentialKind CENTRE_PAYEUR = ReferentielsDePatientCouverture.CENTRE_PAYEUR;

    private final GestionDesPayeurs payeurs;

    public PayeursParPatientCouverture(GestionDesPayeurs payeurs) {
        this.payeurs = payeurs;
    }

    @Override
    public boolean prend(ReferentialKind kind) {
        return ReferentielsDePatientCouverture.PAYEURS.contains(kind);
    }

    @Override
    public List<ReferentialKind> descripteurs() {
        return ReferentielsDePatientCouverture.PAYEURS;
    }

    private static Niveau niveau(ReferentialKind kind) {
        if (kind.equals(CAISSE)) return Niveau.CAISSE;
        if (kind.equals(AGENCE)) return Niveau.AGENCE;
        if (kind.equals(CENTRE_PAYEUR)) return Niveau.CENTRE_PAYEUR;
        throw new IllegalStateException("Référentiel non géré par les payeurs : " + kind);
    }

    private static SaisieDePayeur saisie(ReferentialKind kind, Map<String, String> v) {
        if (kind.equals(CAISSE)) return new SaisieDePayeur(v.get("code"), v.get("nom"), v.get("typeCaisse"), null, null);
        if (kind.equals(AGENCE)) return new SaisieDePayeur(v.get("code"), v.get("nom"), null, null, v.get("caisse"));
        return new SaisieDePayeur(v.get("code"), v.get("nom"), null, v.get("adresse"), v.get("agence"));
    }

    private static ReferentialEntry entree(ReferentialKind kind, FicheDePayeur fiche) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        Map<String, String> references = new LinkedHashMap<>();
        valeurs.put("code", fiche.code());
        valeurs.put("nom", fiche.nom());
        if (kind.equals(CAISSE)) {
            valeurs.put("typeCaisse", fiche.typeCaisse());
        } else if (kind.equals(AGENCE)) {
            valeurs.put("caisse", fiche.parent() == null ? null : fiche.parent().toString());
            references.put("caisse", fiche.parentLibelle());
        } else {
            valeurs.put("adresse", fiche.adresse());
            valeurs.put("agence", fiche.parent() == null ? null : fiche.parent().toString());
            references.put("agence", fiche.parentLibelle());
        }
        return new ReferentialEntry(fiche.id(), valeurs, references);
    }

    private static ValidationIssue anomalie(Anomalie a) {
        return new ValidationIssue(a.ligne(), a.champ(), a.code(), a.message(), a.parametres());
    }

    /** Les refus du contexte, dans les erreurs de l'administration générique. */
    private static RuntimeException traduire(ReferentialKind kind, RefusDePayeur refus) {
        String libelle = kind.label();
        return switch (refus.motif()) {
            case SAISIE_INVALIDE -> new ReferentialValidationException(
                    refus.anomalies().stream().map(PayeursParPatientCouverture::anomalie).toList());
            case DEJA_EXISTANT -> new ReferentialValidationException(List.of(new ValidationIssue(0, "code", "ALREADY_EXISTS",
                    "Un élément de « " + libelle + " » avec ce code existe déjà.", Map.of())));
            case INTROUVABLE -> new BusinessException("REFERENTIAL_NOT_FOUND", "Élément introuvable dans « " + libelle + " » pour ce centre.");
            case UTILISE -> new BusinessException("REFERENTIAL_IN_USE", "Suppression impossible : cet élément de « " + libelle
                    + " » est encore utilisé (" + refus.references() + " référence(s) : patients, prises en charge, séances ou autres référentiels).");
        };
    }

    @Override
    public PagedResult<ReferentialEntry> list(CentreId centerId, ReferentialKind kind, String search, int page, int size) {
        var resultat = payeurs.lister(centerId.valeur(), niveau(kind), search, page, size);
        return PagedResult.of(resultat.lignes().stream().map(f -> entree(kind, f)).toList(), resultat.total(), resultat.page(),
                resultat.taille());
    }

    @Override
    public ReferentialEntry create(CentreId centerId, ReferentialKind kind, Map<String, String> values) {
        try {
            return entree(kind, payeurs.creer(centerId.valeur(), niveau(kind), saisie(kind, values)));
        } catch (RefusDePayeur refus) {
            throw traduire(kind, refus);
        }
    }

    @Override
    public ReferentialEntry update(CentreId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        try {
            return entree(kind, payeurs.modifier(centerId.valeur(), niveau(kind), id, saisie(kind, values)));
        } catch (RefusDePayeur refus) {
            throw traduire(kind, refus);
        }
    }

    @Override
    public void delete(CentreId centerId, ReferentialKind kind, UUID id) {
        try {
            payeurs.supprimer(centerId.valeur(), niveau(kind), id);
        } catch (RefusDePayeur refus) {
            throw traduire(kind, refus);
        }
    }

    @Override
    public Outcome importRows(CentreId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
        List<LigneAImporter> lignes = rows.stream().map(r -> new LigneAImporter(r.lineNumber(), saisie(kind, r.values()))).toList();
        BilanDImport bilan = payeurs.importer(centerId.valeur(), niveau(kind), lignes, !dryRun);
        return new Outcome(bilan.aCreer(), bilan.aMettreAJour(),
                bilan.anomalies().stream().map(PayeursParPatientCouverture::anomalie).toList(), bilan.appliquee());
    }
}
