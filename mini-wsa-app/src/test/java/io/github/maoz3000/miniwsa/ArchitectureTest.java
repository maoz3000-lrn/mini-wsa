package io.github.maoz3000.miniwsa;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.base.DescribedPredicate.and;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Layering rules: services do not build HTTP responses, the domain depends on nothing else, and Kafka, Redis and
 * ClickHouse are only touched by their adapters.
 */
@AnalyzeClasses(packages = "io.github.maoz3000.miniwsa", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String APP = "io.github.maoz3000.miniwsa..";

    @ArchTest
    static final ArchRule servicesDoNotDependOnApiTypes = noClasses()
            .that().areAnnotatedWith(Service.class)
            .should().dependOnClassesThat().resideInAPackage("..miniwsa.api..")
            .because("services return business results; controllers map them to DTOs");

    @ArchTest
    static final ArchRule domainDependsOnNothingElse = classes()
            .that().resideInAPackage("..miniwsa.domain..")
            // lombok only for the @Generated marker on builders
            .should().onlyDependOnClassesThat().resideInAnyPackage("..miniwsa.domain..", "java..", "lombok..")
            .because("the domain is shared by every layer and must not carry framework or validation concerns");

    @ArchTest
    static final ArchRule infrastructureClientsOnlyInAdapters = noClasses()
            .that().haveSimpleNameNotStartingWith("ClickHouse")
            .and().haveSimpleNameNotStartingWith("Kafka")
            .and().haveSimpleNameNotStartingWith("Redis")
            .and().haveSimpleNameNotEndingWith("Config")
            .should().dependOnClassesThat().belongToAnyOf(JdbcClient.class, JdbcTemplate.class,
                    NamedParameterJdbcTemplate.class, KafkaTemplate.class, RedisTemplate.class)
            .because("business code reaches stores and brokers only through interfaces");

    @ArchTest
    static final ArchRule consumerRecordsOnlyInTheListener = noClasses()
            .that().doNotHaveSimpleName("DlrBatchConsumer")
            .should().dependOnClassesThat().belongToAnyOf(ConsumerRecord.class)
            .because("Kafka records stay in the listener; processing works on domain events");

    @ArchTest
    static final ArchRule controllersDoNotUseStorage = noClasses()
            .that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().resideInAPackage("..miniwsa.storage..");

    @ArchTest
    static final ArchRule clickHouseClassesStayInStorage = noClasses()
            .that().resideOutsideOfPackage("..miniwsa.storage..")
            .should().dependOnClassesThat(and(resideInAPackage(APP), simpleNameStartingWith("ClickHouse")));
}
