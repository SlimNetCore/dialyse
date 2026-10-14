package com.hemodialyse.backend.contexts.gmao.contrat;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Enregistre le SQL qu'Hibernate envoie à la base (tests seulement) : permet de constater qu'une liste ne lit pas les octets. */
public class InspecteurDeRequetes implements StatementInspector {

    public static final List<String> REQUETES = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        REQUETES.add(sql);
        return sql;
    }
}
