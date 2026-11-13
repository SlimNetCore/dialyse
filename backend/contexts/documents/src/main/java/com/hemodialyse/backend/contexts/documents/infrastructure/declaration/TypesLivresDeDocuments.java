package com.hemodialyse.backend.contexts.documents.infrastructure.declaration;

import com.hemodialyse.backend.contexts.documents.publishedlanguage.DeclarationDesTypesDeDocument;
import com.hemodialyse.backend.contexts.documents.publishedlanguage.TypeDeDocumentDeclare;

import java.util.List;

/**
 * Déclaration, par Documents lui-même, des six documents du patient et de ses listes (ADR 0165, 0166) : fiche, attestation, prise en charge,
 * liste des patients, des prises en charge et des attestations. Ce sont les jeux de données que Documents rend par l'impression générique
 * (DOC1) ; leur gabarit lit le patient par la source de données du moteur, aucun autre contexte n'en fournit les paramètres. Les huit autres
 * types livrés sont déclarés par leur contexte propriétaire (Facturation, Dossier médical, Stock, GMAO, Planification, Séance, Pilotage de la
 * direction) par le même port.
 */
public final class TypesLivresDeDocuments implements DeclarationDesTypesDeDocument {

    @Override
    public List<TypeDeDocumentDeclare> types() {
        return List.of(
                new TypeDeDocumentDeclare("FICHE_PATIENT", "Fiche signalétique patient", "reports/fiche_patient.jrxml", List.of(), 1,
                        "Fiche complète du patient avec ses informations personnelles et médicales"),
                new TypeDeDocumentDeclare("ATTESTATION", "Attestation d'ouverture de droit", "reports/attestation.jrxml", List.of(), 1,
                        "Attestation d'ouverture de droit du patient"),
                new TypeDeDocumentDeclare("PEC", "Prise en charge", "reports/prise_en_charge.jrxml", List.of(), 1,
                        "Document de prise en charge pour la caisse d'assurance"),
                new TypeDeDocumentDeclare("LISTE_PATIENTS", "Liste des patients", "reports/liste_patients.jrxml", List.of(), 1,
                        "Liste complète des patients du centre"),
                new TypeDeDocumentDeclare("LISTE_PEC", "Liste des prises en charge", "reports/liste_pec.jrxml", List.of(), 1,
                        "Liste de toutes les prises en charge du centre"),
                new TypeDeDocumentDeclare("LISTE_ATTESTATIONS", "Liste des attestations", "reports/liste_attestations.jrxml", List.of(), 1,
                        "Liste de toutes les attestations du centre"));
    }
}
