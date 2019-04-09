package uk.gov.ons.census.casesvc.iac;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.casesvc.client.InternetAccessCodeSvcClient;

@Component
public class IacDispenser implements Runnable {
  private InternetAccessCodeSvcClient internetAccessCodeSvcClient;
  private BlockingQueue<String> iacCodePool = new LinkedBlockingQueue<>();
  private boolean isFetchingIacCodes = false;

  @Value("${iacservice.pool-size-min}")
  private int iacPoolSizeMin;

  @Value("${iacservice.pool-size-max}")
  private int iacPoolSizeMax;

  public IacDispenser(InternetAccessCodeSvcClient internetAccessCodeSvcClient) {
    this.internetAccessCodeSvcClient = internetAccessCodeSvcClient;
  }

  public String getIacCode() {
    topUpPoolIfNecessary();

    try {
      return iacCodePool.take();
    } catch (InterruptedException e) {
      throw new RuntimeException("Thread shut down while waiting for IAC code");
    }
  }

  // This function has to be synchronized to protect against a dead-heat request for a top-up
  private synchronized void topUpPoolIfNecessary() {
    // Theoretically we could end up with more threads waiting for IAC codes than we've requested
    // so we should never have more worker threads than the max pool size.
    if (!isFetchingIacCodes && iacCodePool.size() < iacPoolSizeMin) {
      isFetchingIacCodes = true; // Don't ask for more codes until the last request has completed

      Thread thread = new Thread(this);
      thread.run();
    }
  }

  @Override
  public void run() {
    try {
      // In theory this could fail, but it's retryable and it will recover so long as requests for
      // IAC codes continue to arrive. In the worst case scenario messages will build up on the
      // Rabbit queue until somebody spots the errors and resolves the issue
      List<String> generatedIacCodes = internetAccessCodeSvcClient.generateIACs(iacPoolSizeMax);

      iacCodePool.addAll(generatedIacCodes);
    } catch (Exception exception) {
      // This is more of a warning because it's recoverable but it can cause an error
      //      log.error("Unexpected exception when requesting IAC codes to top up pool", exception);
      exception.printStackTrace();
    } finally {
      isFetchingIacCodes = false;
    }
  }
}
