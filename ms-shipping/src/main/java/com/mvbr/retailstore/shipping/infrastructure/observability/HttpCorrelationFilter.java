package com.mvbr.retailstore.shipping.infrastructure.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class HttpCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            putFromHeader(request, HeaderNames.CORRELATION_ID, MdcKeys.CORRELATION_ID);
            putFromHeader(request, HeaderNames.CAUSATION_ID, MdcKeys.PARENT_CORRELATION_ID);

            String aggregateId = request.getHeader(HeaderNames.AGGREGATE_ID);
            String aggregateType = request.getHeader(HeaderNames.AGGREGATE_TYPE);

            putIfPresent(HeaderNames.AGGREGATE_ID, MdcKeys.AGGREGATE_ID, aggregateId);
            putIfPresent(HeaderNames.AGGREGATE_TYPE, MdcKeys.AGGREGATE_TYPE, aggregateType);

            if ("Order".equalsIgnoreCase(aggregateType) && aggregateId != null && !aggregateId.isBlank()) {
                MDC.put(MdcKeys.ORDER_ID, aggregateId);
            }

            putFromHeader(request, HeaderNames.SAGA_ID, MdcKeys.SAGA_ID);
            putFromHeader(request, HeaderNames.SAGA_NAME, MdcKeys.SAGA_NAME);
            putFromHeader(request, HeaderNames.SAGA_STEP, MdcKeys.SAGA_STEP);

            putFromHeader(request, HeaderNames.COMMAND_ID, MdcKeys.COMMAND_ID);
            putFromHeader(request, HeaderNames.COMMAND_TYPE, MdcKeys.COMMAND_TYPE);
            putFromHeader(request, HeaderNames.EVENT_ID, MdcKeys.EVENT_ID);
            putFromHeader(request, HeaderNames.EVENT_TYPE, MdcKeys.EVENT_TYPE);
            putFromHeader(request, HeaderNames.PRODUCER, MdcKeys.PRODUCER);

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static void putFromHeader(HttpServletRequest request, String headerName, String mdcKey) {
        putIfPresent(headerName, mdcKey, request.getHeader(headerName));
    }

    private static void putIfPresent(String headerName, String mdcKey, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(mdcKey, value);
        if (headerName != null) {
            MDC.put(headerName, value);
        }
    }
}
