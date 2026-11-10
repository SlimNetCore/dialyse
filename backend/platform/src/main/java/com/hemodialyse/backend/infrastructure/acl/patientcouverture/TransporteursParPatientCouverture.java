package com.hemodialyse.backend.infrastructure.acl.patientcouverture;

import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.Anomalie;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.BilanDImport;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.FicheDeTransporteur;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.GestionDesTransporteurs.LigneAImporter;
import com.hemodialyse.backend.contexts.patientcouverture.publishedlanguage.RefusDeTransporteur;
import com.hemodialyse.backend.domain.shared.PagedResult;
import com.hemodialyse.backend.domain.shared.exception.BusinessException;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
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
 * ACL de l'administration générique des référentiels vers patient-couverture (RF1) : les transporteurs sont désormais gérés par
 * {@link GestionDesTransporteurs}. Traduit les valeurs du formulaire ou du fichier ({@code nom}, {@code telephone}) vers le langage
 * publié, et les refus du contexte vers les erreurs de l'administration (mêmes codes et mêmes messages qu'avant : 400
 * {@code VALIDATION}, 404 {@code REFERENTIAL_NOT_FOUND}, 409 {@code REFERENTIAL_IN_USE}).
 */
public class TransporteursParPatientCouverture implements ReferentialAdminDelegate {

    private static final String LIBELLE = ReferentialKind.TRANSPORTEUR.label();

    private final GestionDesTransporteurs transporteurs;

    public TransporteursParPatientCouverture(GestionDesTransporteurs transporteurs) {
        this.transporteurs = transporteurs;
    }

    @Override
    public boolean prend(ReferentialKind kind) {
        return kind == ReferentialKind.TRANSPORTEUR;
    }

    private static ReferentialEntry entree(FicheDeTransporteur fiche) {
        Map<String, String> valeurs = new LinkedHashMap<>();
        valeurs.put("nom", fiche.nom());
        valeurs.put("telephone", fiche.telephone());
        return new ReferentialEntry(fiche.id(), valeurs, Map.of());
    }

    private static ValidationIssue anomalie(Anomalie a) {
        return new ValidationIssue(a.ligne(), a.champ(), a.code(), a.message(), a.parametres());
    }

    /** Les refus du contexte, dans les erreurs de l'administration générique. */
    private static RuntimeException traduire(RefusDeTransporteur refus) {
        return switch (refus.motif()) {
            case SAISIE_INVALIDE -> new ReferentialValidationException(refus.anomalies().stream().map(TransporteursParPatientCouverture::anomalie).toList());
            case DEJA_EXISTANT -> new ReferentialValidationException(List.of(new ValidationIssue(0, "nom", "ALREADY_EXISTS",
                    "Un élément de « " + LIBELLE + " » avec ce nom existe déjà.", Map.of())));
            case INTROUVABLE -> new BusinessException("REFERENTIAL_NOT_FOUND", "Élément introuvable dans « " + LIBELLE + " » pour ce centre.");
            case UTILISE -> new BusinessException("REFERENTIAL_IN_USE", "Suppression impossible : cet élément de « " + LIBELLE
                    + " » est encore utilisé (" + refus.references() + " référence(s) : patients, prises en charge, séances ou autres référentiels).");
        };
    }

    @Override
    public PagedResult<ReferentialEntry> list(CenterId centerId, ReferentialKind kind, String search, int page, int size) {
        var resultat = transporteurs.lister(centerId.value(), search, page, size);
        return PagedResult.of(resultat.lignes().stream().map(TransporteursParPatientCouverture::entree).toList(), resultat.total(),
                resultat.page(), resultat.taille());
    }

    @Override
    public ReferentialEntry create(CenterId centerId, ReferentialKind kind, Map<String, String> values) {
        try {
            return entree(transporteurs.creer(centerId.value(), values.get("nom"), values.get("telephone")));
        } catch (RefusDeTransporteur refus) {
            throw traduire(refus);
        }
    }

    @Override
    public ReferentialEntry update(CenterId centerId, ReferentialKind kind, UUID id, Map<String, String> values) {
        try {
            return entree(transporteurs.modifier(centerId.value(), id, values.get("nom"), values.get("telephone")));
        } catch (RefusDeTransporteur refus) {
            throw traduire(refus);
        }
    }

    @Override
    public void delete(CenterId centerId, ReferentialKind kind, UUID id) {
        try {
            transporteurs.supprimer(centerId.value(), id);
        } catch (RefusDeTransporteur refus) {
            throw traduire(refus);
        }
    }

    @Override
    public Outcome importRows(CenterId centerId, ReferentialKind kind, List<Row> rows, boolean dryRun) {
        List<LigneAImporter> lignes = rows.stream()
                .map(r -> new LigneAImporter(r.lineNumber(), r.values().get("nom"), r.values().get("telephone"))).toList();
        BilanDImport bilan = transporteurs.importer(centerId.value(), lignes, !dryRun);
        return new Outcome(bilan.aCreer(), bilan.aMettreAJour(),
                bilan.anomalies().stream().map(TransporteursParPatientCouverture::anomalie).toList(), bilan.appliquee());
    }
}
