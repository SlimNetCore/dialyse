package com.hemodialyse.backend.domain.comptabilite.valueobject;

import java.util.UUID;

/**
 * Table de correspondance comptes SCF pour un centre donné.
 * Configurable par centerId — jamais codée en dur dans le domaine.
 */
public record MappingComptable(
        UUID centerId,
        String compteVentes,          // ex. 706
        String compteClientPatient,   // ex. 411100
        String compteClientCnas,      // ex. 411200
        String compteClientCasnos,    // ex. 411300
        String compteClientMutuelle,  // ex. 411400
        String compteClientAutre,     // ex. 411500
        String compteBanque,          // ex. 512
        String compteCaisse,          // ex. 530
        String compteTVACollectee     // ex. 44571 (prévu pour future activation TVA)
) {
    public MappingComptable {
        if (centerId == null) throw new IllegalArgumentException("Le centerId est obligatoire");
        if (compteVentes == null || compteVentes.isBlank())
            throw new IllegalArgumentException("Le compte ventes est obligatoire");
        if (compteClientPatient == null || compteClientPatient.isBlank())
            throw new IllegalArgumentException("Le compte client patient est obligatoire");
        if (compteClientCnas == null || compteClientCnas.isBlank())
            throw new IllegalArgumentException("Le compte client CNAS est obligatoire");
        if (compteClientCasnos == null || compteClientCasnos.isBlank())
            throw new IllegalArgumentException("Le compte client CASNOS est obligatoire");
        if (compteClientMutuelle == null || compteClientMutuelle.isBlank())
            throw new IllegalArgumentException("Le compte client mutuelle est obligatoire");
        if (compteClientAutre == null || compteClientAutre.isBlank())
            throw new IllegalArgumentException("Le compte client autre est obligatoire");
        if (compteBanque == null || compteBanque.isBlank())
            throw new IllegalArgumentException("Le compte banque est obligatoire");
        if (compteCaisse == null || compteCaisse.isBlank())
            throw new IllegalArgumentException("Le compte caisse est obligatoire");
    }

    /**
     * Mapping par défaut — utilisé tant qu'un centre n'a pas personnalisé son plan comptable.
     */
    public static MappingComptable defaultFor(UUID centerId) {
        return new MappingComptable(centerId,
                "706", "411100", "411200", "411300", "411400", "411500",
                "512", "530", "44571");
    }

    /**
     * Retourne le compte 411 applicable selon le type de tiers payeur.
     */
    public String compteClient(TypeTiersPayeur type) {
        return switch (type) {
            case PATIENT_DIRECT -> compteClientPatient;
            case CNAS -> compteClientCnas;
            case CASNOS -> compteClientCasnos;
            case MUTUELLE -> compteClientMutuelle;
            case AUTRE -> compteClientAutre;
        };
    }
}

