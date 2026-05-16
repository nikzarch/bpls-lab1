package com.example.labpay.batch;

import com.example.labpay.domain.transfer.Transfer;
import com.example.labpay.domain.transfer.TransferStatus;
import com.example.labpay.repository.TransferRepository;
import com.example.labpay.service.WalletService;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class StuckTransferJobConfig {

    private final EntityManagerFactory emf;
    private final TransferRepository transferRepository;
    private final WalletService walletService;

    @Value("${app.batch.chunk-size:200}")
    private int chunkSize;

    @Value("${app.batch.stuck-transfer.cutoff-minutes:30}")
    private int cutoffMinutes;

    @Bean
    public JpaPagingItemReader<Transfer> stuckTransferReader() {
        return new JpaPagingItemReaderBuilder<Transfer>()
                .name("stuckTransferReader")
                .entityManagerFactory(emf)
                .queryString("select t from Transfer t where t.status = :status and t.createdAt < :cutoff order by t.id asc")
                .parameterValues(Map.of(
                        "status", TransferStatus.PENDING,
                        "cutoff", Instant.now().minus(cutoffMinutes, ChronoUnit.MINUTES)
                ))
                .pageSize(chunkSize)
                .saveState(false)
                .build();
    }

    @Bean
    public ItemProcessor<Transfer, Transfer> stuckTransferProcessor() {
        return t -> {
            String holdRef = "transfer-" + t.getIdempotencyKey();
            try { walletService.releaseHold(holdRef); } catch (Exception e) { log.debug("Hold release skipped for {}: {}", holdRef, e.getMessage()); }
            t.setStatus(TransferStatus.FAILED);
            t.setCompletedAt(Instant.now());
            return t;
        };
    }

    @Bean
    public ItemWriter<Transfer> stuckTransferWriter() {
        return items -> transferRepository.saveAll(items.getItems());
    }

    @Bean
    public Step stuckTransferStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("stuckTransferStep", jobRepository)
                .<Transfer, Transfer>chunk(chunkSize, tm)
                .reader(stuckTransferReader())
                .processor(stuckTransferProcessor())
                .writer(stuckTransferWriter())
                .build();
    }

    @Bean
    public Job stuckTransferJob(JobRepository jobRepository, Step stuckTransferStep) {
        return new JobBuilder("stuckTransferJob", jobRepository)
                .start(stuckTransferStep)
                .build();
    }
}