package com.example.labpay.repository;

import com.example.labpay.domain.BankOperation;
import com.example.labpay.domain.BankOperationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BankOperationRepository extends JpaRepository<BankOperation, Long> {
    Optional<BankOperation> findByCorrelationId(String correlationId);
    List<BankOperation> findByStatusIn(List<BankOperationStatus> statuses);
}