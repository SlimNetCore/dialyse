package com.hemodialyse.backend.contexts.seance.domain;

/**
 * Cause métier d'un refus de séance. {@link #code()} est le code d'erreur de l'API observé par le filet de caractérisation (422) ;
 * les trois causes de facturation partagent le code générique {@code BUSINESS_RULE_VIOLATION} (KB-SEA-10, reproduit).
 */
public enum MotifDeRefus {
    PRISE_EN_CHARGE_ABSENTE("BUSINESS_RULE_VIOLATION"),
    PRISE_EN_CHARGE_NON_VALIDEE("BUSINESS_RULE_VIOLATION"),
    ATTESTATION_DE_DROITS_ABSENTE("BUSINESS_RULE_VIOLATION"),
    CENTRE_FERME("SEANCE_CENTRE_FERME"),
    JOUR_NON_DIALYSE("SEANCE_HORS_PLANNING_JOUR"),
    PATIENT_NON_ATTENDU("SEANCE_HORS_PLANNING_PATIENT"),
    MOTIF_DE_DEROGATION_REQUIS("SEANCE_DEROGATION_MOTIF_REQUIS"),
    PRECISION_DE_DEROGATION_REQUISE("SEANCE_DEROGATION_PRECISION_REQUISE"),
    SEANCE_DU_JOUR_ABSENTE("BUSINESS_RULE_VIOLATION"),
    /** SE2 : une séance facturée ne se modifie plus (validation, signature, déverrouillage). */
    SEANCE_FACTUREE("SEANCE_FACTUREE_NON_MODIFIABLE"),
    /** SE2 : seule une séance « créée » se valide (statut autre que créée, validée, signée, facturée : le reliquat « absent »). */
    STATUT_NON_VALIDABLE("BUSINESS_RULE_VIOLATION"),
    /** SE2 : la signature du médecin suppose la validation infirmière (KB-SEA-20 : message reproduit pour toutes les causes). */
    SIGNATURE_AVANT_VALIDATION("BUSINESS_RULE_VIOLATION"),
    /** SE2 : ligne incohérente, validée mais déjà signée par un médecin. */
    DEJA_SIGNEE("BUSINESS_RULE_VIOLATION"),
    /** SE2 : seule une séance « créée » d'un jour passé se déverrouille. */
    DEVERROUILLAGE_INVALIDE("SEANCE_DEVERROUILLAGE_INVALIDE"),
    /** SE2 : l'infirmier ne valide pas une séance d'un jour passé sans le déverrouillage de l'administrateur. */
    REGULARISATION_NON_DEVERROUILLEE("SEANCE_REGULARISATION_NON_DEVERROUILLEE"),
    /** SE3 : on n'ajoute un consommable qu'à une séance validée ou signée (KB-SEA-24 : la modification et le retrait sont plus larges). */
    CONSOMMABLES_AVANT_VALIDATION("BUSINESS_RULE_VIOLATION");

    private final String code;

    MotifDeRefus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
