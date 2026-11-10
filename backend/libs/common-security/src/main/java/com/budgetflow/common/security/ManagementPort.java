package com.budgetflow.common.security;

import org.springframework.core.env.Environment;

/** The separate management (actuator) port of this service, or -1 when actuator shares the public port. */
final class ManagementPort {

    private ManagementPort() {
    }

    static int resolve(Environment environment) {
        // local.management.port holds the bound port (also when configured as 0); fall back to the configured one.
        int port = environment.getProperty("local.management.port", Integer.class,
                environment.getProperty("management.server.port", Integer.class, -1));
        int serverPort = environment.getProperty("local.server.port", Integer.class,
                environment.getProperty("server.port", Integer.class, 8080));
        return port > 0 && port != serverPort ? port : -1;
    }
}
