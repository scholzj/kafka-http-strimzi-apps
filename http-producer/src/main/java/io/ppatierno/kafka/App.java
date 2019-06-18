package io.ppatierno.kafka;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public final class App {
    
    private static final Logger log = LoggerFactory.getLogger(App.class);

    private static String deploymentId;

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();

        ConfigStoreOptions envStore = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject().put("raw-data", true));

        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(envStore);

        ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

        retriever.getConfig(ar -> {
            Map<String, Object> envConfig = ar.result().getMap();
            HttpKafkaProducerConfig httpKafkaConsumerConfig = HttpKafkaProducerConfig.fromMap(envConfig);

            HttpKafkaProducer httpKafkaProducer = new HttpKafkaProducer(httpKafkaConsumerConfig);

            vertx.deployVerticle(httpKafkaProducer, done -> {
                if (done.succeeded()) {
                    deploymentId = done.result();
                    log.info("HTTP Kafka producer started successfully");
                } else {
                    log.error("Failed to deploy HTTP Kafka producer", done.cause());
                    System.exit(1);
                }
            });
        });

        Runtime.getRuntime().addShutdownHook(new Thread() {

            @Override
            public void run() {
                CountDownLatch latch = new CountDownLatch(1);
                vertx.undeploy(deploymentId, v -> latch.countDown());
                try {
                    if (!latch.await(60000, TimeUnit.MILLISECONDS)) {
                        log.info("App exiting for timeout on undeploy verticle");
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } finally {
                    vertx.close();
                }
            }
        });
    }
}
