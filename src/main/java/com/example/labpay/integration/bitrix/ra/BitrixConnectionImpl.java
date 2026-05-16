package com.example.labpay.integration.bitrix.ra;

import com.example.labpay.integration.bitrix.ra.dto.BitrixDealData;
import com.example.labpay.integration.bitrix.ra.interfaces.BitrixConnection;
import jakarta.resource.ResourceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@Slf4j
public class BitrixConnectionImpl implements BitrixConnection {

    private final BitrixManagedConnection owner;
    private final BitrixApiClient apiClient;
    private final AtomicBoolean closed = new AtomicBoolean(false);


    @Override
    public long upsertDeal(BitrixDealData data) throws ResourceException {
        ensureOpen();
        return apiClient.findDealIdByExternalOrderId(data.externalOrderId())
                .map(existingId -> {
                    apiClient.updateDeal(existingId, data);
                    return existingId;
                })
                .orElseGet(() -> apiClient.createDeal(data));
    }

    @Override
    public void addTimelineComment(long dealId, String comment) throws ResourceException {
        ensureOpen();
        apiClient.addTimelineComment(dealId, comment);
    }

    @Override
    public void close() {
        closed.compareAndSet(false, true);
        owner.cleanup();
    }

    private void ensureOpen() throws ResourceException {
        if (closed.get()) {
            throw new ResourceException("Bitrix connection is closed");
        }
    }
}
