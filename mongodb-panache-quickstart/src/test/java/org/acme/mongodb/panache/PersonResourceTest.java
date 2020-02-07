package org.acme.mongodb.panache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import de.flapdoodle.embed.mongo.Command;
import de.flapdoodle.embed.mongo.MongoImportExecutable;
import de.flapdoodle.embed.mongo.MongoImportStarter;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongoImportConfig;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongoImportConfigBuilder;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.config.RuntimeConfigBuilder;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.config.IRuntimeConfig;
import de.flapdoodle.embed.process.runtime.Network;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.http.ContentType;
import io.restassured.parsing.Parser;
import org.assertj.core.api.Assertions;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Objects;

import static io.restassured.config.LogConfig.logConfig;

@QuarkusTest
class PersonResourceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(PersonResourceTest.class);
    private static MongodExecutable MONGO;

    @BeforeAll
    static void initAll() throws IOException {
        final int mongoPort = 27017;
        LOGGER.info("Starting Mongo {} on port {}", Version.Main.V4_0, mongoPort);
        Net net = new Net(mongoPort, Network.localhostIsIPv6());
        IMongodConfig mongodConfig = new MongodConfigBuilder().version(Version.Main.V4_0).net(net).build();
        IRuntimeConfig runtimeConfig = new RuntimeConfigBuilder().defaults(Command.MongoD).build();
        MONGO = MongodStarter.getInstance(runtimeConfig).prepare(mongodConfig);
        MONGO.start();

        File jsonFile = new File(Objects
                .requireNonNull(Thread.currentThread().getContextClassLoader().getResource("person-dataset.json")).getFile());
        String importDatabase = "person";
        String importCollection = "ThePerson";
        MongoImportExecutable mongoImportProcess = mongoImportExecutable(mongoPort, importDatabase,
                importCollection, jsonFile.getAbsolutePath(), true, true, true);
        String mongoUrl = ConfigProvider.getConfig().getValue("quarkus.mongodb.connection-string", String.class);
        MongoClient mongoClient = MongoClients.create(mongoUrl);
        mongoImportProcess.start();
        Assertions.assertThat(mongoClient.getDatabase(importDatabase).getCollection(importCollection).estimatedDocumentCount())
                .isEqualTo(3);
        mongoImportProcess.stop();

        RestAssured.defaultParser = Parser.JSON;
        RestAssured.config
                .logConfig((logConfig().enableLoggingOfRequestAndResponseIfValidationFails()))
                .objectMapperConfig(new ObjectMapperConfig().jackson2ObjectMapperFactory((type, s) -> new ObjectMapper()
                        .registerModule(new Jdk8Module())
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)));
    }

    @AfterAll
    static void tearDownAll() {
        if (MONGO != null) {
            MONGO.stop();
        }
    }

    @Test
    void listTest() {
        final Person[] persons = RestAssured.given()
                .when()
                .contentType(ContentType.JSON)
                .get("/persons")
                .then()
                .statusCode(200)
                .extract()
                .body().as(Person[].class);

        Assertions.assertThat(persons.length).isEqualTo(3);
    }

    @Test
    void getTest() {
        final Person person = RestAssured
                .given()
                .when()
                .contentType(ContentType.JSON)
                .get("/persons/{id}", "5889273c093d1c3e614bf2fa")
                .then()
                .statusCode(200)
                .extract()
                .body().as(Person.class);

        Assertions.assertThat(person.id).isEqualTo(new ObjectId("5889273c093d1c3e614bf2fa"));
        Assertions.assertThat(person.name).isEqualTo("LOÏC");
        Assertions.assertThat(person.birthDate).isEqualTo(LocalDate.of(1988, 6, 19));
        Assertions.assertThat(person.status).isEqualTo(Status.LIVING);
    }

    @Test
    void createTest() {
        PersonDTO person = PersonDTO.Builder
                .aPersonDTO()
                .withId("5889273c093d1c3e614bf2fc")
                .withName("Logan")
                .withBirthDate(LocalDate.of(1964, 10, 31))
                .withStatus(Status.DECEASED)
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(person)
                .when()
                .post("/persons")
                .then()
                .statusCode(201);
    }

    @Test
    void updateTest() {
        PersonDTO person = PersonDTO.Builder
                .aPersonDTO()
                .withId("5889273c093d1c3e614bf2fb")
                .withName("ThePerson")
                .build();

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(person)
                .when()
                .put("/persons/{id}", "5889273c093d1c3e614bf2fa")
                .then()
                .statusCode(204);
    }

    @Test
    void deleteTest() {
        RestAssured
                .given()
                .contentType(ContentType.JSON)
                .when()
                .delete("/persons/{id}", "5889273c093d1c3e614bf2fb")
                .then()
                .statusCode(204);
    }

    @Test
    void searchByNameTest() {
        final Person person = RestAssured
                .given()
                .contentType(ContentType.JSON)
                .when()
                .get("/persons/search/{name}", "MONCEF")
                .then()
                .statusCode(200)
                .extract()
                .body().as(Person.class);

        Assertions.assertThat(person.id).isEqualTo(new ObjectId("5889273c093d1c3e614bf2f9"));
        Assertions.assertThat(person.name).isEqualTo("MONCEF");
        Assertions.assertThat(person.birthDate).isEqualTo(LocalDate.of(1993, 1, 18));
        Assertions.assertThat(person.status).isEqualTo(Status.LIVING);
    }

    @Test
    void countTest() {
        final Long count = RestAssured
                .given()
                .when()
                .contentType(ContentType.JSON)
                .get("/persons/count")
                .then()
                .statusCode(200)
                .extract()
                .body().as(Long.class);

        Assertions.assertThat(count).isGreaterThan(0);
    }

    private static MongoImportExecutable mongoImportExecutable(int port, String dbName, String collection,
                                                               String jsonFile, Boolean jsonArray,
                                                               Boolean upsert, Boolean drop) throws IOException {
        IMongoImportConfig mongoImportConfig = new MongoImportConfigBuilder()
                .version(Version.Main.V4_0)
                .net(new Net(port, Network.localhostIsIPv6()))
                .db(dbName)
                .collection(collection)
                .upsert(upsert)
                .dropCollection(drop)
                .jsonArray(jsonArray)
                .importFile(jsonFile)
                .build();

        return MongoImportStarter.getDefaultInstance().prepare(mongoImportConfig);
    }

}
