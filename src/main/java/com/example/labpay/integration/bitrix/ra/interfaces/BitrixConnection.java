package com.example.labpay.integration.bitrix.ra.interfaces;

import com.example.labpay.integration.bitrix.ra.dto.BitrixDealData;
import jakarta.resource.ResourceException;

public interface BitrixConnection extends AutoCloseable {

    long upsertDeal(BitrixDealData data) throws ResourceException;

    void setDealProductRows(long dealId, BitrixDealData data) throws ResourceException;

    void addTimelineComment(long dealId, String comment) throws ResourceException;

    @Override
    void close() throws ResourceException;
}