package com.shashi.rca.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import tools.jackson.core.JacksonException;

import java.time.Duration;

@Configuration
public class KafkaConfig {

    public static final String RAW_TOPIC = "app.telemetry.raw";
    public static final String ANALYSIS_TOPIC = "rca.analysis.requests";
    public static final String RAW_DLT = RAW_TOPIC + ".DLT";
    public static final String ANALYSIS_DLT = ANALYSIS_TOPIC + ".DLT";

    @Bean
    NewTopic rawTopic() {
        // One partition keeps ingest ordered, which the error grouping relies on
        return TopicBuilder.name(RAW_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic analysisTopic() {
        // Three partitions allow up to three LLM analyses in parallel
        return TopicBuilder.name(ANALYSIS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic rawDeadLetterTopic() {
        return TopicBuilder.name(RAW_DLT).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic analysisDeadLetterTopic() {
        return TopicBuilder.name(ANALYSIS_DLT).partitions(1).replicas(1).build();
    }

    /**
     * Spring Boot applies this handler to every @KafkaListener: retry with exponential backoff,
     * then publish the message to "<topic>.DLT" instead of retrying forever or dropping it.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                          @Value("${rca.kafka.retry.max-retries:3}") int maxRetries,
                                          @Value("${rca.kafka.retry.initial-interval:2s}") Duration initialInterval) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1)); // -1 = let Kafka pick the partition

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(initialInterval.toMillis());
        backOff.setMultiplier(2.0);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Retrying cannot fix a malformed message, so send it to the DLT straight away
        handler.addNotRetryableExceptions(JacksonException.class, IllegalArgumentException.class);
        return handler;
    }
}
