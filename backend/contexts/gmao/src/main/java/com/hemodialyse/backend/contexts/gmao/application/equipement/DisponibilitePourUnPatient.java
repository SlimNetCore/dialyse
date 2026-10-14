package com.hemodialyse.backend.contexts.gmao.application.equipement;

import com.hemodialyse.backend.contexts.gmao.domain.equipement.StatutEquipement;

/** Un équipement peut-il recevoir un patient ? Avertissement non bloquant : la décision reste au personnel. */
public record DisponibilitePourUnPatient(boolean disponible, StatutEquipement statut) {
}
