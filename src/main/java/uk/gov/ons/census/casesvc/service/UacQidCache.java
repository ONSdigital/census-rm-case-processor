package uk.gov.ons.census.casesvc.service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;

@Component
public class UacQidCache {
  private final UacQidServiceClient uacQidServiceClient;

  @Value("${uacservice.uacqid-cache-min}")
  private int cacheMin;

  @Value("${uacservice.uacqid-fetch-count}")
  private int cacheFetch;

  @Value("${uacservice.uacqid-get-timeout}")
  private long uacQidGetTimout;

  private static Executor executor = Executors.newFixedThreadPool(8);

  private Map<Integer, BlockingQueue<UacQidDTO>> uacQidLinkQueueMap = new ConcurrentHashMap<>();
  private Set<Integer> isToppingUpQueue = ConcurrentHashMap.newKeySet();

  public UacQidCache(UacQidServiceClient uacQidServiceClient) {
    this.uacQidServiceClient = uacQidServiceClient;
  }

  public UacQidDTO getUacQidPair(int questionnaireType) {
    uacQidLinkQueueMap.computeIfAbsent(questionnaireType, key -> new LinkedBlockingDeque<>());

    try {
      topUpQueue(questionnaireType);
      UacQidDTO uacQidDTO =
          uacQidLinkQueueMap.get(questionnaireType).poll(uacQidGetTimout, TimeUnit.SECONDS);

      if (uacQidDTO == null) {
        // The cache topper upper is executed in a separate thread, which can fail if uacqid api
        // down
        // So check we get a non null result otherwise throw a RunTimeException to re-enqueue msg
        throw new RuntimeException(
            "Timeout getting UacQidDTO for questionnaireType :" + questionnaireType);
      }

      return uacQidDTO;
    } catch (InterruptedException e) {
      throw new RuntimeException(e.getMessage());
    }
  }

  private synchronized void topUpQueue(int questionnaireType) {
    if (!isToppingUpQueue.contains(questionnaireType)
        && uacQidLinkQueueMap.get(questionnaireType).size() < cacheMin) {
      isToppingUpQueue.add(questionnaireType);

      executor.execute(
          () -> {
            try {
              uacQidLinkQueueMap
                  .get(questionnaireType)
                  .addAll(uacQidServiceClient.getUacQids(questionnaireType, cacheFetch));
            } finally {
              isToppingUpQueue.remove(questionnaireType);
            }
          });
    }
  }
}
