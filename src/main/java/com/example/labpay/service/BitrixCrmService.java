package com.example.labpay.service;

import com.example.labpay.mq.events.BitrixDealSyncEvent;

public interface BitrixCrmService {
    void syncPaidOrder(BitrixDealSyncEvent event);
}