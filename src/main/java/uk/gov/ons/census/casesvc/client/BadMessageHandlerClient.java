package uk.gov.ons.census.casesvc.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReport;
import uk.gov.ons.census.casesvc.model.dto.ExceptionReportResponse;

@Component
public class BadMessageHandlerClient {

  private String scheme = "http";

  private String host = "localhost";

  private String port = "8666";

  public ExceptionReportResponse reportError(
      String messageHash, String service, String queue, Exception exception) {

    ExceptionReport exceptionReport = new ExceptionReport();
    exceptionReport.setExceptionClass(exception.getClass().getName());
    exceptionReport.setExceptionMessage(exception.getMessage());
    exceptionReport.setMessageHash(messageHash);
    exceptionReport.setService(service);
    exceptionReport.setQueue(queue);

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents("/reportexception");

    return restTemplate.postForObject(
        uriComponents.toUri(), exceptionReport, ExceptionReportResponse.class);
  }

  private UriComponents createUriComponents(String path) {
    return UriComponentsBuilder.newInstance()
        .scheme(scheme)
        .host(host)
        .port(port)
        .path(path)
        .build()
        .encode();
  }
}
