package com.example.labpay.repository;

import com.example.labpay.domain.OrderStatus;
import com.example.labpay.domain.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Long> {
    Optional<PaymentOrder> findByExternalOrderId(String externalOrderId);
    List<PaymentOrder> findByBuyerId(Long buyerId);
    Optional<PaymentOrder> findByPaymentCorrelationId(String paymentCorrelationId);
    List<PaymentOrder> findByStatus(OrderStatus status);
}