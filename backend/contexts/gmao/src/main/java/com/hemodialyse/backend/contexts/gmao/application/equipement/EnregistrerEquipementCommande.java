package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.UtilisateurId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Enregistrement d'un équipement. {@code type} est le code reçu (reconnu par le cas d'usage). */
public record EnregistrerEquipementCommande(CentreId centreId, String code, String designation, String type,
                                            String fabricant, String modele, String numeroSerie, Instant dateInstallation,
                                            String localisation, UUID salleId, BigDecimal prixAcquisition,
                                            UtilisateurId par) {
}
