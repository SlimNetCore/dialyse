package com.hemodialyse.backend.contexts.gmao.domain.intervention;

/** La tâche visée n'appartient pas à l'intervention. 400 BAD_REQUEST, comme une ligne de coût inconnue. */
public class TacheInconnue extends IllegalArgumentException {
    public TacheInconnue() {
        super("Tâche non trouvée");
    }
}
