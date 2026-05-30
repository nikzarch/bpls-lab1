package com.example.labpay.batch;

import com.example.labpay.domain.BankOperation;
import com.example.labpay.domain.BankOperationStatus;
import com.example.labpay.service.ReconciliationService;
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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class BankReconciliationJobConfig {

    private final EntityManagerFactory emf;
    private final ReconciliationService reconciliationService;

    @Value("${app.batch.chunk-size:25}")
    private int chunkSize;

    @Bean
    @StepScope
    public ItemReader<BankOperation> bankOpReader() {
        return new ItemReader<>() {
            private final Deque<BankOperation> buffer = new ArrayDeque<>();
            private boolean exhausted = false;
            private long lastId = 0L;

            @Override
            public BankOperation read() {
                if (buffer.isEmpty()) {
                    if (exhausted) {
                        return null;
                    }

                    List<BankOperation> page = fetchPage(lastId);

                    if (page.isEmpty()) {
                        exhausted = true;
                        return null;
                    }

                    buffer.addAll(page);
                    lastId = page.get(page.size() - 1).getId();

                    if (page.size() < chunkSize) {
                        exhausted = true;
                    }
                }

                return buffer.pollFirst();
            }
        };
    }

    private List<BankOperation> fetchPage(long afterId) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery("""
                            select o
                            from BankOperation o
                            where o.status in :statuses
                              and o.id > :afterId
                            order by o.id asc
                            """, BankOperation.class)
                    .setParameter("statuses", List.of(
                            BankOperationStatus.PENDING_RECONCILE,
                            BankOperationStatus.PENDING_FINALIZE
                    ))
                    .setParameter("afterId", afterId)
                    .setMaxResults(chunkSize)
                    .getResultList();
        } finally {
            em.close();
        }
    }

    @Bean
    public ItemProcessor<BankOperation, BankOperation> bankOpProcessor() {
        return op -> {
            reconciliationService.reconcile(op);
            return null;
        };
    }

    @Bean
    public ItemWriter<BankOperation> bankOpWriter() {
        return items -> {
        };
    }

    @Bean
    public Step bankReconciliationStep(
            JobRepository jobRepository,
            PlatformTransactionManager tm,
            @Qualifier("bankOpReader") ItemReader<BankOperation> bankOpReader
    ) {
        return new StepBuilder("bankReconciliationStep", jobRepository)
                .<BankOperation, BankOperation>chunk(chunkSize, tm)
                .reader(bankOpReader)
                .processor(bankOpProcessor())
                .writer(bankOpWriter())
                .build();
    }

    @Bean
    public Job bankReconciliationJob(
            JobRepository jobRepository,
            Step bankReconciliationStep
    ) {
        return new JobBuilder("bankReconciliationJob", jobRepository)
                .start(bankReconciliationStep)
                .build();
    }
}