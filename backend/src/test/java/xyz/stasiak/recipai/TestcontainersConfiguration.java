package xyz.stasiak.recipai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import xyz.stasiak.recipai.config.s3.TestS3Configuration;

@TestConfiguration(proxyBeanMethods = false)
@Import({TestAiConfiguration.class, TestS3Configuration.class})
public class TestcontainersConfiguration {

	private static final PostgreSQLContainer POSTGRES;

	static {
		POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:17.5"));
		POSTGRES.start();
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

}
