package com.budgetflow.identity.adapter.in.web;

import com.budgetflow.identity.application.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;

public final class ClientInfos {

    private static final int MAX_USER_AGENT_LENGTH = 512;

    private ClientInfos() {}

    /** Remote address is proxy-aware via server.forward-headers-strategy=framework. */
    public static ClientInfo from(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent != null && userAgent.length() > MAX_USER_AGENT_LENGTH) {
            userAgent = userAgent.substring(0, MAX_USER_AGENT_LENGTH);
        }
        return new ClientInfo(request.getRemoteAddr(), userAgent);
    }
}
