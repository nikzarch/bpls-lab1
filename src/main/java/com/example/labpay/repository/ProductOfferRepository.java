package com.example.labpay.repository;

import com.example.labpay.domain.widget.ProductOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductOfferRepository extends JpaRepository<ProductOffer, Long> {
    List<ProductOffer> findByWidgetId(Long widgetId);
}
