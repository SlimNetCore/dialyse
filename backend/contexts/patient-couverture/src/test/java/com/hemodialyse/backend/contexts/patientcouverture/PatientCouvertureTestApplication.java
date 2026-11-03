package com.hemodialyse.backend.contexts.patientcouverture;

import org.springframework.boot.SpringBootConfiguration;

/**
 * Racine de test du module : les tests de contrat s'exécutent sans l'application legacy. Volontairement sans balayage ni
 * auto-configuration : la composition réelle (qui exige les ports publiés de la Planification) n'est pas chargée, seuls les
 * adapters JDBC sont instanciés à la main par les tests.
 */
@SpringBootConfiguration
public class PatientCouvertureTestApplication {
}
