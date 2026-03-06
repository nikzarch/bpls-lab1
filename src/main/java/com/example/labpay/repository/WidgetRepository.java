package com.example.labpay.repository;

import com.example.labpay.domain.widget.Widget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WidgetRepository extends JpaRepository<Widget, Long> {
    List<Widget> findByMerchantId(Long merchantId);
}
