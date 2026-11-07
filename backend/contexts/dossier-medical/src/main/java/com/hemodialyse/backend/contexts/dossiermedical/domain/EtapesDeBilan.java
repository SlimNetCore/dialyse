package com.hemodialyse.backend.contexts.dossiermedical.domain;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Port sortant du domaine : les étapes du bilan pré-greffe (table {@code etapes_bilan_pre_greffe}, inchangée, sans unicité). Toujours
 * cherchées par (identifiant, patient, centre). Une ligne dont la catégorie ou le statut est illisible en base est ignorée par la
 * lecture (WARN avec son identifiant seul) ; elle ne peut plus être mise à jour (limite consignée, section 51 des vérifications).
 */
public interface EtapesDeBilan {
    /** Étapes du patient, par catégorie puis libellé (départage : création puis identifiant). */
    List<EtapeDeBilan> duPatient(CentreId centre, PatientId patient);

    /** Clés {@code CATEGORIE|libellé} de TOUTES les lignes du patient, lisibles ou non (la génération ne doit rien doubler). */
    Set<String> clesDuPatient(CentreId centre, PatientId patient);

    /** Verrou de génération de la liste standard du patient, tenu jusqu'à la fin de la transaction. */
    void verrouillerLaGeneration(CentreId centre, PatientId patient);

    void ajouter(EtapeDeBilan etape);

    void enregistrer(EtapeDeBilan etape);

    /** Lecture avec verrou de ligne. Lève {@link EcritureConcurrenteSurUneEtapeDeBilan}. */
    Optional<EtapeDeBilan> trouverEnVerrouillant(CentreId centre, PatientId patient, UUID id);

    /** Supprime l'étape (lisible ou non) ; faux si elle n'existait pas. */
    boolean supprimer(CentreId centre, PatientId patient, UUID id);
}
