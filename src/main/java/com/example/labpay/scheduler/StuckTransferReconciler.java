package com.example.labpay.scheduler;

import com.example.labpay.domain.transfer.TransferStatus;
import com.example.labpay.repository.TransferRepository;
import com.example.labpay.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@Profile({"worker", "all"})
@RequiredArgsConstructor
public class StuckTransferReconciler {

    private final TransferRepository transferRepository;
    private final WalletService walletService;

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void reconcile() {
        Instant cutoff = Instant.now().minus(30, ChronoUnit.MINUTES);
        var stuck = transferRepository.findByStatusAndCreatedAtBefore(TransferStatus.PENDING, cutoff);
        for (var t : stuck) {
            String holdRef = "transfer-" + t.getIdempotencyKey();
            try { walletService.releaseHold(holdRef); } catch (Exception ignored) {}
            t.setStatus(TransferStatus.FAILED);
            t.setCompletedAt(Instant.now());
        }
        if (!stuck.isEmpty()) log.warn("Reconciled {} stuck transfers", stuck.size());
    }
}