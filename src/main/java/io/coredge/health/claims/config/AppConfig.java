package io.coredge.health.claims.config;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.parser.StrictErrorHandler;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import ca.uhn.fhir.validation.FhirValidator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.coredge.health.claims.context.HealthIdContextHolder;
import io.micrometer.context.ContextExecutorService;
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AppConfig {

  @Value("${FHIR_SERVER_BASE_URL:https://dev-mis.apeiro-digital.com/fhir}")
  private String serverFhirBaseUrl;

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }

  @Bean
  public IGenericClient fhirClient() {
    FhirContext fhirContext = FhirContext.forR4(); // for R4
    // Create and configure the FHIR client
    String serverBaseUrl = serverFhirBaseUrl; // Set the base URL of your FHIR server
      //Enable debug on Interceptor.
    //client.registerInterceptor(new ca.uhn.fhir.rest.client.interceptor.LoggingInterceptor(true));
    return fhirContext.newRestfulGenericClient(serverBaseUrl);
  }

  @Bean
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  public WebClient webClient() {
    return WebClient.builder().build();
  }

  @Bean
  public IParser fhirParser() {
    IParser parser = fhirContext().newJsonParser();
    parser.setParserErrorHandler(new StrictErrorHandler());
    return parser;
  }

  @Bean(name = "taskExecutor")
  public Executor taskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(50);  // Increase pool size
    executor.setMaxPoolSize(100);
    executor.setQueueCapacity(500);  // Ensure enough queue capacity
    executor.setThreadNamePrefix("Async-");
    executor.initialize();
    return executor;
  }

  @Bean(name = "virtualThreadExecutor")
  public Executor virtualThreadExecutor() {
    ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
    return ContextExecutorService.wrap(delegate);
  }

  @Bean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }

  @Bean
  public ObjectMapper objectMapper(){
    ObjectMapper mapper = new ObjectMapper();
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    javaTimeModule.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    mapper.registerModule(javaTimeModule);
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  @Bean
  public ModelMapper modelMapper() {
    return new ModelMapper();
  }

  @EnableJpaAuditing(auditorAwareRef = "auditorAware")
  public static class JpaAuditingConfig {
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(HealthIdContextHolder.userId()).or(() -> Optional.of("system"));
    }
  }

  @Bean
  public FhirValidator fhirValidator(FhirContext fhirContext) {
    return fhirContext.newValidator();
  }

}
