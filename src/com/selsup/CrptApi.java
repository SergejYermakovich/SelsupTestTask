package com.selsup;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


public class CrptApi {
    private final TimeUnit timeUnit;
    private final int requestLimit;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notFull = lock.newCondition();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final URL url = new URL("https://ismp.crpt.ru/api/v3/lk/documents/create");

    public static void main(String[] args) throws MalformedURLException, InterruptedException {
        CrptApi crptApi = new CrptApi(TimeUnit.MINUTES, 10);
        crptApi.createDocument(new Document(), "ww1g2vv2212vtv2e2te21et1");
    }

    public CrptApi(final TimeUnit timeUnit, final int requestLimit) throws MalformedURLException {
        this.timeUnit = timeUnit;
        this.requestLimit = requestLimit;
        scheduler.scheduleAtFixedRate(() -> {
            lock.lock();
            try {
                requestCount.set(0);
                notFull.signalAll();
            } finally {
                lock.unlock();
            }
        }, 0, 1, timeUnit);
    }

    public void createDocument(final Document document, final String signature) throws InterruptedException {
        lock.lock();
        try {
            while (requestCount.get() >= requestLimit) {
                notFull.await();
            }
            requestCount.incrementAndGet();
        } finally {
            lock.unlock();
        }

        callAPI(document, signature);
    }

    private void callAPI(final Document document, final String signature) {
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Signature", signature);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                ObjectWriter ow = new ObjectMapper().writer().withDefaultPrettyPrinter();
                String jsonDocument = ow.writeValueAsString(document);
                byte[] input = jsonDocument.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new RuntimeException("Failed : HTTP error code : " + responseCode);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error with API call.", e);
        }
    }


    @Data
    public static class Document {
        @JsonProperty("description")
        private Description description;

        @JsonProperty("doc_id")
        private String docId;

        @JsonProperty("doc_status")
        private String docStatus;

        @JsonProperty("doc_type")
        private String docType;

        @JsonProperty("importRequest")
        private boolean importRequest;

        @JsonProperty("owner_inn")
        private String ownerInn;

        @JsonProperty("participant_inn")
        private String participantInn;

        @JsonProperty("producer_inn")
        private String producerInn;

        @JsonProperty("production_date")
        private String productionDate;

        @JsonProperty("production_type")
        private String productionType;

        @JsonProperty("products")
        private List<Product> products;

        @JsonProperty("reg_date")
        private String regDate;

        @JsonProperty("reg_number")
        private String regNumber;

        @Data
        public static class Description {
            @JsonProperty("participantInn")
            private String participantInn;
        }

        @Data
        public static class Product {
            @JsonProperty("certificate_document")
            private String certificateDocument;

            @JsonProperty("certificate_document_date")
            private String certificateDocumentDate;

            @JsonProperty("certificate_document_number")
            private String certificateDocumentNumber;

            @JsonProperty("owner_inn")
            private String ownerInn;

            @JsonProperty("producer_inn")
            private String producerInn;

            @JsonProperty("production_date")
            private String productionDate;

            @JsonProperty("tnved_code")
            private String tnvedCode;

            @JsonProperty("uit_code")
            private String uitCode;

            @JsonProperty("uitu_code")
            private String uituCode;
        }
    }

}