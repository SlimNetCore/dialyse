package com.hemodialyse.backend.infrastructure.persistence.adapter;

import com.hemodialyse.backend.domain.seance.port.SeanceRaccourciRepositoryPort;
import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class SeanceRaccourciJdbcAdapter implements SeanceRaccourciRepositoryPort {

    private final JdbcTemplate jdbc;

    public SeanceRaccourciJdbcAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<UUID> findArticleIds(CenterId centerId) {
        return jdbc.query(
                "SELECT article_id FROM seance_raccourcis_articles WHERE center_id = ? ORDER BY ordre",
                (rs, rowNum) -> rs.getObject("article_id", UUID.class),
                centerId.value());
    }

    @Override
    public void replace(CenterId centerId, List<UUID> articleIds) {
        jdbc.update("DELETE FROM seance_raccourcis_articles WHERE center_id = ?", centerId.value());
        int ordre = 0;
        for (UUID articleId : articleIds) {
            jdbc.update("INSERT INTO seance_raccourcis_articles (center_id, article_id, ordre) VALUES (?, ?, ?)",
                    centerId.value(), articleId, ordre++);
        }
    }
}
