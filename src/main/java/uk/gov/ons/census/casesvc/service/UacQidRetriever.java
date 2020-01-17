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
  private int cacheMin;

  @Value("${uacservice.uacqid-fetch-count}")
  private int cacheMax;

  private Map<Integer, BlockingQueue<UacQidDTO>> uacQidLinkQueueMap = new ConcurrentHashMap<>();

  public UacQidRetriever(UacQidServiceClient uacQidServiceClient) {
    this.uacQidServiceClient = uacQidServiceClient;
  }

  public UacQidDTO generateUacQid(int questionnaireType) {

    if (!uacQidLinkQueueMap.containsKey(questionnaireType)) {
      BlockingQueue<UacQidDTO> newUacQidTypeQueue = new LinkedBlockingDeque<>();
      uacQidLinkQueueMap.put(questionnaireType, newUacQidTypeQueue);
    }

    try {
      return uacQidLinkQueueMap.get(questionnaireType).take();
    } catch (InterruptedException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  @PostConstruct
  public void populateCache() {
    int[] questionnaireTypes = {1, 2, 4, 21, 22, 23, 24, 31, 32, 34};

    for (int questionnaireType : questionnaireTypes) {
      BlockingQueue<UacQidDTO> newUacQidTypeQueue = new LinkedBlockingDeque<>();
      newUacQidTypeQueue.addAll(uacQidServiceClient.getUacQids(questionnaireType, cacheMax));
      uacQidLinkQueueMap.put(questionnaireType, newUacQidTypeQueue);
    }
  }

  @Scheduled(fixedRate = 500, initialDelay = 0)
  public void checkEachUacQidPairQueueIsToppedUp() {
    uacQidLinkQueueMap
        .entrySet()
        .parallelStream()
        .filter(e -> e.getValue().size() < cacheMin)
        .forEach(
            entry -> {
              entry
                  .getValue()
                  .addAll(
                      uacQidServiceClient.getUacQids(
                          entry.getKey(), cacheMax - entry.getValue().size()));
            });
  }
}
