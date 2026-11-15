package com.hemodialyse.backend.contexts.documents.infrastructure.rendu;

import com.hemodialyse.backend.contexts.documents.application.ConnexionDeRendu;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Adapter du port transitoire {@link ConnexionDeRendu} (DOC5a, ADR 0193) : une connexion du pool, passée en lecture seule le temps du
 * travail puis rendue au pool dans son état d'origine. Aucune table n'est nommée ici.
 */
public class ConnexionDeRenduDeLaBase implements ConnexionDeRendu {

    private final DataSource source;

    public ConnexionDeRenduDeLaBase(DataSource source) {
        this.source = source;
    }

    @Override
    public <T> T avecLaConnexion(Travail<T> travail) throws Exception {
        try (Connection connexion = source.getConnection()) {
            boolean lectureSeuleAvant = connexion.isReadOnly();
            connexion.setReadOnly(true);
            try {
                return travail.executer(connexion);
            } finally {
                connexion.setReadOnly(lectureSeuleAvant);
            }
        }
    }
}
