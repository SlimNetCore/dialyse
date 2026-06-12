package com.hemodialyse.backend.domain.stock.service;

import com.hemodialyse.backend.domain.shared.vo.CenterId;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PmpRecalculationCoordinator {

    private final PmpEngine pmpEngine;
    private final SimpMessagingTemplate messaging;
    private final ConcurrentHashMap<UUID, JobState> jobs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, UUID> articleLocks = new ConcurrentHashMap<>();

    public PmpRecalculationCoordinator(PmpEngine pmpEngine, SimpMessagingTemplate messaging) {
        this.pmpEngine = pmpEngine;
        this.messaging = messaging;
    }

    public UUID start(CenterId centerId, Collection<UUID> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            throw new IllegalArgumentException("Aucun article a recalculer");
        }

        List<UUID> uniqueArticles = articleIds.stream().filter(a -> a != null).distinct().toList();
        if (uniqueArticles.isEmpty()) {
            throw new IllegalArgumentException("Aucun article valide a recalculer");
        }

        UUID jobId = UUID.randomUUID();
        JobState jobState = new JobState(jobId, centerId.value(), new ArrayList<>(uniqueArticles));
        jobs.put(jobId, jobState);

        for (UUID articleId : uniqueArticles) {
            articleLocks.put(lockKey(centerId.value(), articleId), jobId);
        }

        publishLocksChanged(centerId.value(), uniqueArticles, "STARTED");

        CompletableFuture.runAsync(() -> execute(centerId, jobState));
        return jobId;
    }

    public JobSnapshot get(UUID jobId) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            throw new IllegalArgumentException("Job de recalcul introuvable: " + jobId);
        }
        return state.snapshot();
    }

    public List<UUID> lockedArticles(CenterId centerId) {
        List<UUID> ids = new ArrayList<>();
        String prefix = centerId.value() + ":";
        for (String key : articleLocks.keySet()) {
            if (key.startsWith(prefix)) {
                ids.add(UUID.fromString(key.substring(prefix.length())));
            }
        }
        return ids;
    }

    public boolean isLocked(CenterId centerId, UUID articleId) {
        return articleLocks.containsKey(lockKey(centerId.value(), articleId));
    }

    private void execute(CenterId centerId, JobState jobState) {
        jobState.status = JobStatus.RUNNING;
        jobState.message = "Recalcul en cours";

        try {
            for (UUID articleId : jobState.articleIds) {
                pmpEngine.recalculerArticle(centerId, articleId);
                jobState.processed += 1;
                jobState.message = "Article " + jobState.processed + "/" + jobState.articleIds.size();
            }
            jobState.status = JobStatus.COMPLETED;
            jobState.message = "Recalcul termine";
        } catch (Exception e) {
            jobState.status = JobStatus.FAILED;
            jobState.message = e.getMessage() != null ? e.getMessage() : "Erreur de recalcul";
        } finally {
            jobState.finishedAt = OffsetDateTime.now();
            for (UUID articleId : jobState.articleIds) {
                articleLocks.remove(lockKey(jobState.centerId, articleId));
            }
            publishLocksChanged(jobState.centerId, jobState.articleIds, "FINISHED");
        }
    }

    private void publishLocksChanged(UUID centerId, List<UUID> articleIds, String status) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "STOCK_RECALC_LOCKS_CHANGED");
        event.put("centerId", centerId.toString());
        Map<String, String> payload = new HashMap<>();
        payload.put("status", status);
        payload.put("articleIds", articleIds.stream().map(UUID::toString).reduce((a, b) -> a + "," + b).orElse(""));
        payload.put("count", Integer.toString(articleIds.size()));
        event.put("payload", payload);
        event.put("timestamp", java.time.Instant.now().toString());
        messaging.convertAndSend("/topic/center/" + centerId + "/events", (Object) event);
    }

    private String lockKey(UUID centerId, UUID articleId) {
        return centerId + ":" + articleId;
    }

    public enum JobStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }

    public record JobSnapshot(
            UUID jobId,
            UUID centerId,
            JobStatus status,
            int total,
            int processed,
            String message,
            OffsetDateTime startedAt,
            OffsetDateTime finishedAt,
            List<UUID> articleIds
    ) {
    }

    private static final class JobState {
        private final UUID jobId;
        private final UUID centerId;
        private final List<UUID> articleIds;
        private final OffsetDateTime startedAt;
        private volatile JobStatus status;
        private volatile int processed;
        private volatile String message;
        private volatile OffsetDateTime finishedAt;

        private JobState(UUID jobId, UUID centerId, List<UUID> articleIds) {
            this.jobId = jobId;
            this.centerId = centerId;
            this.articleIds = articleIds;
            this.status = JobStatus.PENDING;
            this.processed = 0;
            this.message = "En attente";
            this.startedAt = OffsetDateTime.now();
            this.finishedAt = null;
        }

        private JobSnapshot snapshot() {
            return new JobSnapshot(
                    jobId,
                    centerId,
                    status,
                    articleIds.size(),
                    processed,
                    message,
                    startedAt,
                    finishedAt,
                    List.copyOf(articleIds)
            );
        }
    }
}



