package com.example.labpay.batch;

import com.example.labpay.domain.card.CardBindingSession;
import com.example.labpay.repository.CardBindingSessionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class CardSessionCleanupJobConfig {

    private final EntityManagerFactory emf;
    private final CardBindingSessionRepository sessionRepository;

    @Value("${app.batch.chunk-size:200}")
    private int chunkSize;

    @Bean
    @StepScope
    public ItemReader<CardBindingSession> cardSessionReader(
            @Value("#{jobParameters['now']}") String nowParam
    ) {
        Instant cutoff = nowParam == null ? Instant.now() : Instant.parse(nowParam);

        return new ItemReader<>() {
            private final Deque<CardBindingSession> buffer = new ArrayDeque<>();
            private boolean exhausted = false;

            @Override
            public CardBindingSession read() {
                if (buffer.isEmpty()) {
                    if (exhausted) {
                        return null;
                    }

                    List<CardBindingSession> page = fetchPage(cutoff);

                    if (page.isEmpty()) {
                        exhausted = true;
                        return null;
                    }

                    buffer.addAll(page);

                    if (page.size() < chunkSize) {
                        exhausted = true;
                    }
                }

                return buffer.pollFirst();
            }
        };
    }

    private List<CardBindingSession> fetchPage(Instant cutoff) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("""
                            select s
                            from CardBindingSession s
                            where s.expiresAt < :cutoff
                            order by s.id asc
                            """, CardBindingSession.class)
                    .setParameter("cutoff", cutoff)
                    .setMaxResults(chunkSize)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    @Bean
    public ItemProcessor<CardBindingSession, CardBindingSession> cardSessionProcessor() {
        return session -> session;
    }

    @Bean
    public ItemWriter<CardBindingSession> cardSessionWriter() {
        return items -> {
            List<CardBindingSession> sessions = new ArrayList<>(items.getItems());

            if (!sessions.isEmpty()) {
                sessionRepository.deleteAllInBatch(sessions);
            }
        };
    }

    @Bean
    public Step cardSessionCleanupStep(
            JobRepository jobRepository,
            PlatformTransactionManager tm,
            @Qualifier("cardSessionReader") ItemReader<CardBindingSession> cardSessionReader
    ) {
        return new StepBuilder("cardSessionCleanupStep", jobRepository)
                .<CardBindingSession, CardBindingSession>chunk(chunkSize, tm)
                .reader(cardSessionReader)
                .processor(cardSessionProcessor())
                .writer(cardSessionWriter())
                .build();
    }

    @Bean
    public Job cardSessionCleanupJob(
            JobRepository jobRepository,
            Step cardSessionCleanupStep
    ) {
        return new JobBuilder("cardSessionCleanupJob", jobRepository)
                .start(cardSessionCleanupStep)
                .build();
    }
}