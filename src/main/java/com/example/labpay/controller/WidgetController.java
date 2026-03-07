package com.example.labpay.controller;

import com.example.labpay.dto.request.CreateProductRequest;
import com.example.labpay.dto.request.CreateWidgetRequest;
import com.example.labpay.dto.response.ProductResponse;
import com.example.labpay.dto.response.WidgetResponse;
import com.example.labpay.service.WidgetService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/widgets")
@RequiredArgsConstructor
@Tag(name = "Widgets", description = "Виджеты продавцов и товары")
public class WidgetController {

    private final WidgetService widgetService;

    @PostMapping
    public WidgetResponse create(Authentication auth, @RequestBody CreateWidgetRequest request) {
        return widgetService.createWidget(auth.getName(), request);
    }

    @GetMapping
    public List<WidgetResponse> list(Authentication auth) {
        return widgetService.getMerchantWidgets(auth.getName());
    }

    @PostMapping("/{widgetId}/products")
    public ProductResponse createProduct(Authentication auth, @PathVariable Long widgetId, @RequestBody CreateProductRequest request) {
        return widgetService.createProduct(auth.getName(), widgetId, request);
    }

    @GetMapping("/{widgetId}/products")
    public List<ProductResponse> listProducts(@PathVariable Long widgetId) {
        return widgetService.getWidgetProducts(widgetId);
    }
}