package com.example.labpay.batch;

import com.example.labpay.domain.BankOperation;
import com.example.labpay.domain.BankOperationStatus;
import com.example.labpay.service.ReconciliationService;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class BankReconciliationJobConfig {

    private final EntityManagerFactory emf;
    private final ReconciliationService reconciliationService;

    @Value("${app.batch.chunk-size:200}")
    private int chunkSize;

    @Bean
    public JpaPagingItemReader<BankOperation> bankOpReader() {
        return new JpaPagingItemReaderBuilder<BankOperation>()
                .name("bankOpReader")
                .entityManagerFactory(emf)
                .queryString("select o from BankOperation o where o.status in :statuses order by o.id asc")
                .parameterValues(Map.of("statuses", List.of(
                        BankOperationStatus.PENDING_RECONCILE,
                        BankOperationStatus.PENDING_FINALIZE
                )))
                .pageSize(chunkSize)
                .saveState(false)
                .build();
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
        return items -> {};
    }

    @Bean
    public Step bankReconciliationStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("bankReconciliationStep", jobRepository)
                .<BankOperation, BankOperation>chunk(chunkSize, tm)
                .reader(bankOpReader())
                .processor(bankOpProcessor())
                .writer(bankOpWriter())
                .build();
    }

    @Bean
    public Job bankReconciliationJob(JobRepository jobRepository, Step bankReconciliationStep) {
        return new JobBuilder("bankReconciliationJob", jobRepository)
                .start(bankReconciliationStep)
                .build();
    }
}