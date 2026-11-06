package com.budgetflow.common.messaging.saga;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Wire contract between the orchestrator and saga participants.
 * <ul>
 *   <li>Commands: exchange {@code budgetflow.commands}, type {@code <service>.<CommandName>}, routing key
 *       {@code <service>.<command-name>}; body always carries {@code sagaId} and {@code commandId}.</li>
 *   <li>Replies: exchange {@code budgetflow.replies}, routing key {@link #REPLY_ROUTING_KEY}, body {@link SagaReply}.</li>
 * </ul>
 */
public final class SagaContract {

    public static final String REPLY_ROUTING_KEY = "orchestrator.saga-reply";
    public static final String REPLY_TYPE = "saga.Reply";

    private static final Pattern TYPE = Pattern.compile("([a-z][a-z0-9-]*)\\.([A-Z][A-Za-z0-9]*)");

    private SagaContract() {}

    /** {@code account.ApplyBalanceChange -> account.apply-balance-change} */
    public static String routingKeyFor(String commandType) {
        var matcher = TYPE.matcher(commandType == null ? "" : commandType);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Command type must be <service>.<CommandName>: " + commandType);
        }
        String kebab = matcher.group(2).replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        return matcher.group(1) + "." + kebab;
    }
}
