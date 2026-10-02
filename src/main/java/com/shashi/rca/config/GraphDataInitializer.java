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

        // The demo graph is rebuilt on every startup so it always matches this code
        String resetQuery = "MATCH (s:Service) DETACH DELETE s";
        String seedQuery = """
                CREATE (gateway:Service {name: 'api-gateway'})
                CREATE (signup:Service {name: 'signup-service'})
                CREATE (kyc:Service {name: 'kyc-service'})
                CREATE (acct:Service {name: 'account-service'})

                CREATE (gateway)-[:CALLS]->(signup)
                CREATE (signup)-[:CALLS]->(acct)
                CREATE (kyc)-[:CALLS]->(acct)
                """;

        try (Session session = neo4jDriver.session()) {
            session.run(resetQuery);
            session.run(seedQuery);
            log.info("✅ Neo4j Topology Mesh Seeded Successfully using Native Session Mapping.");
        } catch (Exception e) {
            log.error("❌ Failed to execute native Neo4j seeding operations: ", e);
        }
    }
}
