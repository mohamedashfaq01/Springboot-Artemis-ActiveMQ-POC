package com.learnwithashfaq.artemis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

/**
 * JmsConfig — Configures HOW messages are serialized/deserialized in JMS.
 *
 * ═══════════════════════════════════════════════════════════════
 * WHY DO WE NEED THIS?
 * ═══════════════════════════════════════════════════════════════
 *
 * When you send an OrderEvent object through JMS, it needs to be converted
 * to a format that can travel through the queue. We have two options:
 *
 *   1. Java Serialization (default) → Binary, hard to debug, Java-only
 *   2. JSON (our choice)            → Text, readable, language-agnostic ✅
 *
 * This class tells Spring JMS: "Convert all messages to JSON using Jackson."
 *
 * ═══════════════════════════════════════════════════════════════
 * HOW IT WORKS:
 * ═══════════════════════════════════════════════════════════════
 *
 * PRODUCER SIDE (sending):
 *   OrderEvent object → Jackson ObjectMapper → JSON string → JMS TextMessage
 *
 * CONSUMER SIDE (receiving):
 *   JMS TextMessage → JSON string → Jackson ObjectMapper → OrderEvent object
 *
 * The "_type" property is a JMS header that tells the consumer what Java class
 * to deserialize the JSON into. Without it, the consumer wouldn't know if
 * the JSON is an OrderEvent, a PaymentEvent, or something else.
 */
@Configuration
@Slf4j
public class JmsConfig {

    /**
     * Custom ObjectMapper configured for JMS message serialization.
     *
     * We register JavaTimeModule so that Java 8 date/time types
     * (LocalDateTime, Instant, etc.) serialize properly in JSON.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Register Java 8 date/time support
        mapper.registerModule(new JavaTimeModule());
        // Write dates as "2024-01-15T10:30:00" instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * MappingJackson2MessageConverter — The bridge between Java objects and JSON.
     *
     * KEY SETTINGS:
     *
     * 1. setTargetType(MessageType.TEXT)
     *    → Sends messages as JMS TextMessage (human-readable JSON)
     *    → Alternative: MessageType.BYTES (binary, smaller but not readable)
     *
     * 2. setTypeIdPropertyName("_type")
     *    → Adds a JMS property "_type" with the fully qualified class name
     *    → Example: "_type" = "com.learnwithashfaq.artemis.dto.OrderEvent"
     *    → The consumer reads this to know which class to deserialize into
     *
     * When you debug, you'll see the actual JSON message in logs — much easier
     * than debugging binary serialization!
     */
    @Bean
    public MessageConverter jacksonJmsMessageConverter(ObjectMapper objectMapper) {
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();

        // Send as TEXT (JSON string), not BYTES
        converter.setTargetType(MessageType.TEXT);

        // Add class type info as a JMS message property
        // The consumer uses this to deserialize JSON → correct Java class
        converter.setTypeIdPropertyName("_type");

        // Use our configured ObjectMapper (with date/time support)
        converter.setObjectMapper(objectMapper);

        log.info("✅ JMS Message Converter configured — messages will be sent as JSON");
        return converter;
    }
}
