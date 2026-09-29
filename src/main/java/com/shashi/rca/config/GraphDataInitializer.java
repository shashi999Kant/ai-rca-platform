package com.shashi.rca.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GraphDataInitializer implements CommandLineRunner {

    private final Driver neo4jDriver;

    @Override
    public void run(String... args) {
        log.info("Seeding Neo4j Microservice Topology Map via Native Cypher...");

        // MERGE only creates a node/relationship if it does not exist yet, so this is safe to run on every startup
        String seedQuery = """
                MERGE (cust:Service {name: 'customer-service'})
                MERGE (acct:Service {name: 'account-service'})
                MERGE (notif:Service {name: 'notification-service'})

                MERGE (cust)-[:CALLS]->(acct)
                MERGE (acct)-[:CALLS]->(notif)
                """;

        try (Session session = neo4jDriver.session()) {
            session.run(seedQuery);
            log.info("✅ Neo4j Topology Mesh Seeded Successfully using Native Session Mapping.");
        } catch (Exception e) {
            log.error("❌ Failed to execute native Neo4j seeding operations: ", e);
        }
    }
}
