package com.example.labpay.service;

import com.example.labpay.dto.request.CreateProductRequest;
import com.example.labpay.dto.request.CreateWidgetRequest;
import com.example.labpay.dto.response.ProductResponse;
import com.example.labpay.dto.response.WidgetResponse;

import java.util.List;

public interface WidgetService {
    WidgetResponse createWidget(String username, CreateWidgetRequest request);
    List<WidgetResponse> getMerchantWidgets(String username);
    ProductResponse createProduct(String username, Long widgetId, CreateProductRequest request);
    List<ProductResponse> getWidgetProducts(Long widgetId);
}