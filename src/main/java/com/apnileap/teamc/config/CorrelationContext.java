package com.apnileap.teamc.config;

public final class CorrelationContext {
    public static final String HEADER = "X-Correlation-ID";
    public static final String MDC_KEY = "correlation_id";
    public static final String REQUEST_ATTR = "teamc.correlation_id";

    private CorrelationContext() {}

    public static String current() {
        String mdc = org.slf4j.MDC.get(MDC_KEY);
        return mdc == null ? "" : mdc;
    }
}
