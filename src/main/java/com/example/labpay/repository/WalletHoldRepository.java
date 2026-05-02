package com.example.labpay.repository;

import com.example.labpay.domain.wallet.HoldStatus;
import com.example.labpay.domain.wallet.WalletHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WalletHoldRepository extends JpaRepository<WalletHold, Long> {

    Optional<WalletHold> findByExternalRef(String externalRef);

    @Query("SELECT COALESCE(SUM(h.amount), 0) FROM WalletHold h " +
            "WHERE h.wallet.id = :walletId AND h.status = com.example.labpay.domain.wallet.HoldStatus.ACTIVE")
    BigDecimal sumActiveByWalletId(@Param("walletId") Long walletId);

    List<WalletHold> findByStatusAndExpiresAtBefore(HoldStatus status, Instant before);
}