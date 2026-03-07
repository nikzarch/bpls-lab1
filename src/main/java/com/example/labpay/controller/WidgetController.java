package com.example.labpay.controller;

import com.example.labpay.dto.request.CreateProductRequest;
import com.example.labpay.dto.request.CreateWidgetRequest;
import com.example.labpay.dto.response.ListResponse;
import com.example.labpay.dto.response.ProductResponse;
import com.example.labpay.dto.response.WidgetResponse;
import com.example.labpay.service.WidgetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/widgets")
@RequiredArgsConstructor
@Tag(name = "Widgets", description = "Виджеты продавцов и товары")
public class WidgetController {

    private final WidgetService widgetService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WidgetResponse create(Authentication auth, @Valid @RequestBody CreateWidgetRequest request) {
        return widgetService.createWidget(auth.getName(), request);
    }

    @GetMapping
    public ListResponse<WidgetResponse> list(Authentication auth) {
        return new ListResponse<>(widgetService.getMerchantWidgets(auth.getName()));
    }

    @PostMapping("/{widgetId}/products")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse createProduct(Authentication auth, @PathVariable Long widgetId, @Valid @RequestBody CreateProductRequest request) {
        return widgetService.createProduct(auth.getName(), widgetId, request);
    }

    @GetMapping("/{widgetId}/products")
    public ListResponse<ProductResponse> listProducts(@PathVariable Long widgetId) {
        return new ListResponse<>(widgetService.getWidgetProducts(widgetId));
    }
}