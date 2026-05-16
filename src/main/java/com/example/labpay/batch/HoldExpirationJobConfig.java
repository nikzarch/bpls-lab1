package com.example.labpay.batch;

import com.example.labpay.domain.wallet.HoldStatus;
import com.example.labpay.domain.wallet.WalletHold;
import com.example.labpay.repository.WalletHoldRepository;
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

import java.time.Instant;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class HoldExpirationJobConfig {

    private final EntityManagerFactory emf;
    private final WalletHoldRepository walletHoldRepository;

    @Value("${app.batch.chunk-size:200}")
    private int chunkSize;

    @Bean
    public JpaPagingItemReader<WalletHold> walletHoldReader() {
        return new JpaPagingItemReaderBuilder<WalletHold>()
                .name("walletHoldReader")
                .entityManagerFactory(emf)
                .queryString("select h from WalletHold h where h.status = :status and h.expiresAt < :now order by h.id asc")
                .parameterValues(Map.of("status", HoldStatus.ACTIVE, "now", Instant.now()))
                .pageSize(chunkSize)
                .saveState(false)
                .build();
    }

    @Bean
    public ItemProcessor<WalletHold, WalletHold> walletHoldProcessor() {
        return hold -> {
            hold.setStatus(HoldStatus.EXPIRED);
            hold.setResolvedAt(Instant.now());
            return hold;
        };
    }

    @Bean
    public ItemWriter<WalletHold> walletHoldWriter() {
        return items -> walletHoldRepository.saveAll(items.getItems());
    }

    @Bean
    public Step holdExpirationStep(JobRepository jobRepository, PlatformTransactionManager tm) {
        return new StepBuilder("holdExpirationStep", jobRepository)
                .<WalletHold, WalletHold>chunk(chunkSize, tm)
                .reader(walletHoldReader())
                .processor(walletHoldProcessor())
                .writer(walletHoldWriter())
                .build();
    }

    @Bean
    public Job holdExpirationJob(JobRepository jobRepository, Step holdExpirationStep) {
        return new JobBuilder("holdExpirationJob", jobRepository)
                .start(holdExpirationStep)
                .build();
    }
}