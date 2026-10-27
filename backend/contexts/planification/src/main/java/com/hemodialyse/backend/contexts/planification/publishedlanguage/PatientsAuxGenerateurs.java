package com.hemodialyse.backend.contexts.planification.publishedlanguage;

import java.util.Collection;
import java.util.UUID;

/**
 * Port publié (lecture) : combien de patients du centre sont rattachés à ces générateurs, <b>quel que soit leur état</b> (patients
 * sortis compris : c'est la règle du chiffre « patients sur équipement indisponible » de la Direction, comme l'alerte du générateur
 * indisponible de P5, KB-PLN-63). Remplace la jointure SQL entre {@code patients.generateur_id} et le parc GMAO. Bornée au centre.
 */
public interface PatientsAuxGenerateurs {

    long nombre(UUID centre, Collection<UUID> generateurs);
}
