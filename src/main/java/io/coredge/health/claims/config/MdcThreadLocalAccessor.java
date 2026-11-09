package io.coredge.health.claims.config;

import io.micrometer.context.ThreadLocalAccessor;
import org.slf4j.MDC;
import java.util.Map;

public class MdcThreadLocalAccessor implements ThreadLocalAccessor<Map<String, String>> {
    public static final String KEY = "mdc";

    @Override
    public Object key() {
        return KEY;
    }

    @Override
    public Map<String, String> getValue() {
        return MDC.getCopyOfContextMap();
    }

    @Override
    public void setValue(Map<String, String> value) {
        if (value != null) {
            MDC.setContextMap(value);
        } else {
            MDC.clear();
        }
    }

    @Override
    public void reset() {
        MDC.clear();
    }
}
