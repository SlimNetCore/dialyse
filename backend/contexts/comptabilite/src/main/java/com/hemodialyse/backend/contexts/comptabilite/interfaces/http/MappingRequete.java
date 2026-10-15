package com.hemodialyse.backend.contexts.comptabilite.interfaces.http;

import com.hemodialyse.backend.contexts.comptabilite.application.mapping.ModifierLeMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Corps de {@code PUT /mapping} : cinq comptes obligatoires (20 caractères au plus), la TVA et les cinq comptes du stock
 * facultatifs, et le journal de certaines opérations seulement.
 *
 * @param journaux journal choisi par nom d'opération ; une opération absente garde son journal actuel
 */
public record MappingRequete(
        UUID centerId,
        @NotBlank @Size(max = 20) String compteVentes,
        @NotBlank @Size(max = 20) String compteClientPatient,
        @NotBlank @Size(max = 20) String compteClientDefaut,
        @NotBlank @Size(max = 20) String compteBanque,
        @NotBlank @Size(max = 20) String compteCaisse,
        @Size(max = 20) String compteTVACollectee,
        @Size(max = 20) String compteStock,
        @Size(max = 20) String compteConsommation,
        @Size(max = 20) String compteFacturesNonParvenues,
        @Size(max = 20) String compteBoniInventaire,
        @Size(max = 20) String compteMaliInventaire,
        Map<String, String> journaux) {

    ModifierLeMapping.Commande commande() {
        return new ModifierLeMapping.Commande(compteVentes, compteClientPatient, compteClientDefaut, compteBanque, compteCaisse,
                compteTVACollectee, compteStock, compteConsommation, compteFacturesNonParvenues, compteBoniInventaire,
                compteMaliInventaire, journaux);
    }
}
