package uk.gov.ons.census.casesvc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;

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

    private Map<Integer, BlockingQueue<UacQidDTO>> uacQidLinkQueueMap = new HashMap<>();

    public UacQidDTO generateUacQid(int questionnaireType) {

        if (!uacQidLinkQueueMap.containsKey(questionnaireType)) {
            BlockingQueue<UacQidDTO> newUacQidTypeQueue = new LinkedBlockingDeque<>();
            uacQidLinkQueueMap.put(questionnaireType, newUacQidTypeQueue);
        }

        try {
            //Take will wait until one is available
            return  uacQidLinkQueueMap.get(questionnaireType).take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Scheduled(fixedRate = 500)
    public void startCacheTopperUp() {
    // Check every 500 milliseconds that each Queue has minimum values in it.

    uacQidLinkQueueMap
        .entrySet()
        .forEach(
            queue -> {
              if (queue.getValue().size() < cacheMin) {

                System.out.println("For questionnaireType: " + queue.getKey() + " there are only "
                                + queue.getValue().size() + " fetching more");

                List<UacQidDTO> uacQidDTOList =
                    uacQidServiceClient.getUacQids(queue.getKey(), cacheFetch);
                queue.getValue().addAll(uacQidDTOList);
              }
            });
    }
}
