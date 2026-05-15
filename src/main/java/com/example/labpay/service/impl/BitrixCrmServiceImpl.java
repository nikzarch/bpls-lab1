package com.example.labpay.service.impl;

import com.example.labpay.exception.BusinessException;
import com.example.labpay.integration.bitrix.ra.BitrixConnection;
import com.example.labpay.integration.bitrix.ra.BitrixConnectionFactory;
import com.example.labpay.integration.bitrix.ra.BitrixDealData;
import com.example.labpay.mq.events.BitrixDealSyncEvent;
import com.example.labpay.service.BitrixCrmService;
import jakarta.resource.ResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BitrixCrmServiceImpl implements BitrixCrmService {

    private final BitrixConnectionFactory bitrixConnectionFactory;

    @Override
    public void syncPaidOrder(BitrixDealSyncEvent event) {
        try (BitrixConnection connection = bitrixConnectionFactory.getConnection()) {
            BitrixDealData data = new BitrixDealData(
                    event.orderId(),
                    event.externalOrderId(),
                    event.buyerUsername(),
                    event.widgetId(),
                    event.merchantId(),
                    event.productTitle(),
                    event.amount(),
                    event.status(),
                    event.paidAt()
            );
            long dealId = connection.upsertDeal(data);
            connection.addTimelineComment(
                    dealId,
                    "Order " + event.externalOrderId() + " synchronized from LabPay. Status=" + event.status()
            );

            log.info("Bitrix deal synchronized: order={}, dealId={}", event.externalOrderId(), dealId);
        } catch (ResourceException e) {
            throw new BusinessException("Bitrix sync failed: " + e.getMessage());
        }
    }
}