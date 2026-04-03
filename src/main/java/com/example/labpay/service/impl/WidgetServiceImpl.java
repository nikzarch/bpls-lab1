package com.example.labpay.service.impl;

import com.example.labpay.domain.user.AppUser;
import com.example.labpay.domain.user.Role;
import com.example.labpay.domain.widget.ProductOffer;
import com.example.labpay.domain.widget.Widget;
import com.example.labpay.dto.request.CreateProductRequest;
import com.example.labpay.dto.request.CreateWidgetRequest;
import com.example.labpay.dto.response.ProductResponse;
import com.example.labpay.dto.response.WidgetResponse;
import com.example.labpay.exception.BusinessException;
import com.example.labpay.exception.NotFoundException;
import com.example.labpay.repository.ProductOfferRepository;
import com.example.labpay.repository.WidgetRepository;
import com.example.labpay.service.UserService;
import com.example.labpay.service.WidgetService;
import com.example.labpay.transaction.TransactionManagerFacade;
import com.example.labpay.transaction.TransactionOptions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.RoundingMode;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class WidgetServiceImpl implements WidgetService {

    private final WidgetRepository widgetRepository;
    private final ProductOfferRepository productRepository;
    private final UserService userService;
    private final TransactionManagerFacade transactionManagerFacade;

    @Override
    public WidgetResponse createWidget(String username, CreateWidgetRequest request) {
        Widget widget = transactionManagerFacade.execute(
                TransactionOptions.defaults("create-widget-transaction"),
                () -> {
                    AppUser merchant = userService.getByUsername(username);
                    if (merchant.getRole() != Role.MERCHANT) {
                        throw new BusinessException("Only merchants can create widgets");
                    }

                    return widgetRepository.save(Widget.builder()
                            .merchant(merchant)
                            .name(request.name())
                            .callbackUrl(request.callbackUrl())
                            .build());
                },
                committedWidget -> log.info("Widget {} committed for merchant {}", committedWidget.getId(), username),
                ex -> log.error("Create widget rolled back for user {}: {}", username, ex.getMessage())
        );

        return toResponse(widget);
    }

    @Override
    public List<WidgetResponse> getMerchantWidgets(String username) {
        AppUser merchant = userService.getByUsername(username);
        return widgetRepository.findByMerchantId(merchant.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ProductResponse createProduct(String username, Long widgetId, CreateProductRequest request) {
        ProductOffer product = transactionManagerFacade.execute(
                TransactionOptions.defaults("create-product-transaction"),
                () -> {
                    AppUser merchant = userService.getByUsername(username);
                    Widget widget = widgetRepository.findById(widgetId)
                            .orElseThrow(() -> new NotFoundException("Widget not found"));

                    if (!widget.getMerchant().getId().equals(merchant.getId())) {
                        throw new BusinessException("Widget does not belong to merchant");
                    }

                    return productRepository.save(ProductOffer.builder()
                            .widget(widget)
                            .title(request.title())
                            .type(request.type())
                            .price(request.price().setScale(2, RoundingMode.HALF_UP))
                            .description(request.description())
                            .build());
                },
                committedProduct -> log.info(
                        "Product {} committed for widget {} by merchant {}",
                        committedProduct.getId(),
                        widgetId,
                        username
                ),
                ex -> log.error(
                        "Create product rolled back for widget {} and user {}: {}",
                        widgetId,
                        username,
                        ex.getMessage()
                )
        );

        return toProductResponse(product);
    }

    @Override
    public List<ProductResponse> getWidgetProducts(Long widgetId) {
        if (!widgetRepository.existsById(widgetId)) {
            throw new NotFoundException("Widget not found");
        }

        return productRepository.findByWidgetId(widgetId).stream()
                .map(this::toProductResponse)
                .toList();
    }

    private WidgetResponse toResponse(Widget w) {
        return new WidgetResponse(
                w.getId(),
                w.getName(),
                w.getCallbackUrl(),
                w.getMerchant().getId()
        );
    }

    private ProductResponse toProductResponse(ProductOffer p) {
        return new ProductResponse(
                p.getId(),
                p.getTitle(),
                p.getType(),
                p.getPrice().setScale(2, RoundingMode.HALF_UP),
                p.getDescription()
        );
    }
}