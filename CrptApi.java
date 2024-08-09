import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Data;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CrptApi {

  private final HttpClient httpClient;
  private final Gson gson;
  private final Lock lock = new ReentrantLock();
  private final int requestLimit;
  private final long timeIntervalMillis;
  private int requestCount = 0;
  private long lastRequestTime = System.currentTimeMillis();

  public CrptApi(TimeUnit timeUnit, int requestLimit) {
    this.httpClient = HttpClient.newHttpClient();
    this.gson = new GsonBuilder().create();
    this.requestLimit = requestLimit;
    this.timeIntervalMillis = timeUnit.toMillis(1);
  }

  public void createDocument(Document document, String signature) {
    lock.lock();
    try {
      long currentTime = System.currentTimeMillis();
      if (currentTime - lastRequestTime > timeIntervalMillis) {
        requestCount = 0;
        lastRequestTime = currentTime;
      }

      while (requestCount >= requestLimit) {
        long waitTime = timeIntervalMillis - (currentTime - lastRequestTime);
        if (waitTime > 0) {
          try {
            TimeUnit.MILLISECONDS.sleep(waitTime);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
        currentTime = System.currentTimeMillis();
        if (currentTime - lastRequestTime > timeIntervalMillis) {
          requestCount = 0;
          lastRequestTime = currentTime;
        }
      }

      requestCount++;
      sendRequest(document, signature);
    } finally {
      lock.unlock();
    }
  }

  private void sendRequest(Document document, String signature) {
    String json = gson.toJson(document);
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
        .header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + signature)
        .timeout(Duration.ofSeconds(10))
        .POST(HttpRequest.BodyPublishers.ofString(json))
        .build();

    try {
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      log.info("Response: " + response.body());
    } catch (IOException | InterruptedException e) {
      e.printStackTrace();
    }
  }

  @Data
  public static class Document {

    private Description description;
    private String doc_id;
    private String doc_status;
    private DocType doc_type;
    private boolean importRequest;
    private String owner_inn;
    private String participant_inn;
    private String producer_inn;
    private Date production_date;
    private String production_type;
    private Product[] products;
    private Date reg_date;
    private String reg_number;
  }

  @Data
  public static class Description {

    private String participantInn;
  }

  public enum DocType {
    LP_INTRODUCE_GOODS,
  }

  @Data
  public static class Product {

    private String certificate_document;
    private Date certificate_document_date;
    private String certificate_document_number;
    private String owner_inn;
    private String producer_inn;
    private Date production_date;
    private String tnved_code;
    private String uit_code;
    private String uitu_code;
  }
}
