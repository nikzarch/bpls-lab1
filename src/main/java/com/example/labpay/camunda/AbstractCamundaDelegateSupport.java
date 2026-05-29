
package com.example.labpay.camunda;

import org.camunda.bpm.engine.delegate.DelegateExecution;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public abstract class AbstractCamundaDelegateSupport {

    protected String string(DelegateExecution execution, String key) {
        Object value = execution.getVariable(key);
        return value == null ? null : String.valueOf(value);
    }

    protected Long longValue(DelegateExecution execution, String key) {
        Object value = execution.getVariable(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    protected BigDecimal decimal(DelegateExecution execution, String key) {
        Object value = execution.getVariable(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value));
    }

    protected Boolean bool(DelegateExecution execution, String key) {
        Object value = execution.getVariable(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    protected <E extends Enum<E>> E enumValue(DelegateExecution execution, String key, Class<E> type) {
        Object value = execution.getVariable(key);
        if (value == null) {
            return null;
        }
        return Enum.valueOf(type, String.valueOf(value));
    }

    protected Map<String, Object> snapshot(DelegateExecution execution, String... keys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : keys) {
            out.put(key, execution.getVariable(key));
        }
        return out;
    }
}
