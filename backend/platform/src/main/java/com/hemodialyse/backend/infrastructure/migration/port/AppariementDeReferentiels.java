package com.hemodialyse.backend.infrastructure.migration.port;

import com.hemodialyse.backend.contexts.sharedkernel.domain.CentreId;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialEntry;
import com.hemodialyse.backend.infrastructure.referential.admin.model.ReferentialKind;

import java.util.List;

/**
 * Port de la reprise de données (PL11-5, ADR 0160) : les éléments proposés d'un référentiel du centre, pour apparier les valeurs lues
 * dans les anciens fichiers (salle, créneau, médecin, payeur, transporteur, forfait). Les données appartiennent aux contextes ; la
 * reprise ne les lit que par leurs lectures publiées (voir {@code AppariementParLesContextes}).
 */
public interface AppariementDeReferentiels {

    List<ReferentialEntry> proposes(CentreId centre, ReferentialKind kind);
}
