package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;
import com.hemodialyse.backend.contexts.gmao.domain.equipement.EquipementId;

import java.math.BigDecimal;
import java.util.UUID;

/** Correction de l'identification d'un équipement du centre : remplacement total (KB-GMAO-36). */
public record CorrigerIdentificationCommande(EquipementId id, CentreId centreId, String designation, String fabricant,
                                             String modele, String numeroSerie, String localisation, UUID salleId,
                                             BigDecimal prixAcquisition, UtilisateurId par) {
}
