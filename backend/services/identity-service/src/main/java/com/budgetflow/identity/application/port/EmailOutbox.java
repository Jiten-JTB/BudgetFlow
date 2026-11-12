package com.budgetflow.identity.application.port;

/** Queues an email in the current transaction; a relay delivers it, so no request ever waits on SMTP. */
public interface EmailOutbox {

    void enqueue(Message message);

    record Message(String to, String subject, String text, String html) {}
}
