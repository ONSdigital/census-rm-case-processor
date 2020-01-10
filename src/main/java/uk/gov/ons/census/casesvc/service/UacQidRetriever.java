package uk.gov.ons.census.casesvc.service;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;

@Component
public class UacQidRetriever {
  private final UacQidServiceClient uacQidServiceClient;

  @Value("${uacservice.uacqid-cache-min}")
  private int cacheMin = 50;

  @Value("${uacservice.uacqid-fetch-count}")
  private int cacheFetch = 100;

  public UacQidRetriever(UacQidServiceClient uacQidServiceClient) {
    this.uacQidServiceClient = uacQidServiceClient;
  }

  // Does this need to be concurrent to stop 2 threads adding at the same time? slower but safer

  private Map<Integer, BlockingQueue<UacQidDTO>> uacQidLinkQueueMap = new ConcurrentHashMap<>();

  public UacQidDTO generateUacQid(int questionnaireType) {

    if (!uacQidLinkQueueMap.containsKey(questionnaireType)) {
      BlockingQueue<UacQidDTO> newUacQidTypeQueue = new LinkedBlockingDeque<>();
      uacQidLinkQueueMap.put(questionnaireType, newUacQidTypeQueue);
    }

    try {
      // Take will wait until one is available
      return uacQidLinkQueueMap.get(questionnaireType).take();
    } catch (InterruptedException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  @PostConstruct
  public void populateCache() {
    int[] questionnaireTypes = {1, 2, 4, 21, 22, 23, 24, 31, 32, 34};

    // could make QuestionnaireTypes a List and parallel it.  Should maybe do this for the topping
    // up?

    long start_time = System.nanoTime();

    for (int questionnaireType : questionnaireTypes) {
      BlockingQueue<UacQidDTO> newUacQidTypeQueue = new LinkedBlockingDeque<>();
      newUacQidTypeQueue.addAll(uacQidServiceClient.getUacQids(questionnaireType, cacheFetch));
      uacQidLinkQueueMap.put(questionnaireType, newUacQidTypeQueue);
    }

    long end_time = System.nanoTime();
    double difference = (end_time - start_time) / 1e6;

    System.out.println(
        "Initial population of UacQid Cache took: " + difference / 1000 + " seconds");
  }

  @Scheduled(fixedRate = 500, initialDelay = 0)
  public void startCacheTopperUp() {
    // Check every n milliseconds that each Queue has minimum values in it.

    uacQidLinkQueueMap
        .entrySet()
        .parallelStream()
        .forEach(
            entry -> {
              if (entry.getValue().size() < cacheMin) {
                entry.getValue().addAll(uacQidServiceClient.getUacQids(entry.getKey(), cacheFetch));
              }
            });
  }
}
