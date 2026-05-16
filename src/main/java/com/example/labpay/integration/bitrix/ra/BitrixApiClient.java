package com.example.labpay.integration.bitrix.ra;

import com.example.labpay.integration.bitrix.ra.dto.BitrixDealData;
import com.example.labpay.integration.bitrix.ra.exception.BitrixApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class BitrixApiClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String externalOrderField;
    private final String sourceId;
    private final String stageId;
    private final String currencyId;
    private final Integer responsibleUserId;

    public Optional<Long> findDealIdByExternalOrderId(String externalOrderId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("filter[" + externalOrderField + "]", externalOrderId);
        form.add("select[0]", "ID");
        form.add("select[1]", externalOrderField);

        JsonNode root = invoke("crm.deal.list", form);
        JsonNode result = root.path("result");
        if (!result.isArray() || result.isEmpty()) {
            return Optional.empty();
        }

        JsonNode first = result.get(0);
        String id = first.path("ID").asText(null);
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(Long.parseLong(id));
    }

    public long createDeal(BitrixDealData data) {
        MultiValueMap<String, String> form = buildDealFields(data);
        JsonNode root = invoke("crm.deal.add", form);
        return extractLongResult(root, "crm.deal.add");
    }

    public void updateDeal(long dealId, BitrixDealData data) {
        MultiValueMap<String, String> form = buildDealFields(data);
        form.add("id", String.valueOf(dealId));
        invoke("crm.deal.update", form);
    }

    public void addTimelineComment(long dealId, String comment) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("fields[ENTITY_ID]", String.valueOf(dealId));
        form.add("fields[ENTITY_TYPE]", "deal");
        form.add("fields[COMMENT]", comment);

        invoke("crm.timeline.comment.add", form);
    }

    private MultiValueMap<String, String> buildDealFields(BitrixDealData data) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();

        form.add("fields[TITLE]", "Order " + data.externalOrderId() + " — " + data.productTitle());
        form.add("fields[OPPORTUNITY]", data.amount().toPlainString());
        form.add("fields[CURRENCY_ID]", currencyId);
        form.add("fields[SOURCE_ID]", sourceId);
        form.add("fields[COMMENTS]", buildComments(data));
        form.add("fields[" + externalOrderField + "]", data.externalOrderId());

        if (stageId != null && !stageId.isBlank()) {
            form.add("fields[STAGE_ID]", stageId);
        }
        if (responsibleUserId != null) {
            form.add("fields[ASSIGNED_BY_ID]", String.valueOf(responsibleUserId));
        }

        form.add("fields[UF_CRM_1778872243]", String.valueOf(data.widgetId()));
        form.add("fields[UF_CRM_1778872261]", String.valueOf(data.merchantId()));
        form.add("fields[UF_CRM_1778872296]", data.buyerUsername());
        form.add("fields[UF_CRM_1778872365]", data.status());
        form.add("fields[UF_CRM_1778872379]", String.valueOf(data.orderId()));

        return form;
    }

    private String buildComments(BitrixDealData data) {
        return """
                External order id: %s
                Buyer: %s
                Widget id: %s
                Merchant id: %s
                Amount: %s
                Status: %s
                Paid at: %s
                """.formatted(
                data.externalOrderId(),
                data.buyerUsername(),
                data.widgetId(),
                data.merchantId(),
                data.amount().toPlainString(),
                data.status(),
                data.paidAt()
        );
    }

    private JsonNode invoke(String method, MultiValueMap<String, String> form) {
        try {
            String body = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path("/").path(method).path(".json").build())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new BitrixApiException("Empty response from Bitrix for method " + method);
            }

            JsonNode root = objectMapper.readTree(body);

            if (root.has("error")) {
                String error = root.path("error").asText("unknown_error");
                String desc = root.path("error_description").asText("");
                throw new BitrixApiException("Bitrix error on " + method + ": " + error + " " + desc);
            }

            return root;
        } catch (Exception e) {
            if (e instanceof BitrixApiException) {
                throw (BitrixApiException) e;
            }
            throw new BitrixApiException("Bitrix request failed for " + method, e);
        }
    }

    private long extractLongResult(JsonNode root, String method) {
        JsonNode result = root.path("result");
        if (result.isMissingNode() || result.isNull()) {
            throw new BitrixApiException("Missing result from Bitrix for " + method);
        }
        if (result.isIntegralNumber()) {
            return result.longValue();
        }
        if (result.isTextual()) {
            return Long.parseLong(result.asText());
        }
        throw new BitrixApiException("Unexpected result format from Bitrix for " + method);
    }
}