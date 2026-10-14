package com.hemodialyse.backend.contexts.gmao.application.intervenant;

import com.hemodialyse.backend.contexts.gmao.domain.CentreId;
import com.hemodialyse.backend.contexts.gmao.domain.intervenant.IntervenantId;

import java.util.Optional;

/**
 * Port sortant de lecture (read model) des intervenants. Contrat observé du legacy : {@code parId} ne filtre pas le centre
 * (le use case le fait) ; la liste d'un centre inclut les intervenants désactivés, trié par nom croissant (ordre de
 * collation de la base, KB-GMAO-28), paginée (page à partir de 0 ; {@code page} et {@code size} déjà validés).
 */
public interface LectureIntervenants {

    Optional<IntervenantVue> parId(IntervenantId id);

    PageDIntervenants duCentre(CentreId centre, int page, int size);
}
