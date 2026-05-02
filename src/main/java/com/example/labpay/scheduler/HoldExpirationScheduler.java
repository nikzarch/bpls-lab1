package com.example.labpay.scheduler;

import com.example.labpay.domain.wallet.HoldStatus;
import com.example.labpay.repository.WalletHoldRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@Profile({"worker", "all"})
@RequiredArgsConstructor
public class HoldExpirationScheduler {

    private final WalletHoldRepository walletHoldRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireStale() {
        var stale = walletHoldRepository.findByStatusAndExpiresAtBefore(HoldStatus.ACTIVE, Instant.now());
        for (var h : stale) {
            h.setStatus(HoldStatus.EXPIRED);
            h.setResolvedAt(Instant.now());
        }
        if (!stale.isEmpty()) log.info("Expired {} holds", stale.size());
    }
}