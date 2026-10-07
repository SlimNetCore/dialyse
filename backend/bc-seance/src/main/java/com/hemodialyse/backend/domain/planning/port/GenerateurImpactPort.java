package com.hemodialyse.backend.domain.planning.port;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Port de lecture : patients dont la place habituelle est un générateur donné. Sert à mesurer ce qu'une panne ou une
 * maintenance de ce générateur touche. Toujours borné au centre (AGENTS.md §2).
 */
public interface GenerateurImpactPort {

    /**
     * Noms (« NOM Prénom ») des patients encore pris en charge à la date donnée dont le générateur est celui-ci, par
     * ordre alphabétique. Un patient sorti (transfert, décès, greffe, guérison) avant cette date n'est pas compté.
     */
    List<String> patientsPlacesSur(UUID centerId, UUID generateurId, LocalDate aujourdhui);
}
