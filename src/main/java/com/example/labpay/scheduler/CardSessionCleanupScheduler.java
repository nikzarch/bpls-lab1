package com.example.labpay.scheduler;
import com.example.labpay.repository.CardBindingSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CardSessionCleanupScheduler {

    private final CardBindingSessionRepository repository;

    @Scheduled(cron = "0 */5 * * * *")
    public void cleanup() {
        repository.findAll()
                .stream()
                .filter(x -> x.getExpiresAt().isBefore(java.time.Instant.now()))
                .forEach(repository::delete);
    }
}