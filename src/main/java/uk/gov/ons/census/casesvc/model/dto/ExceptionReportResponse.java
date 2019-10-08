package uk.gov.ons.census.casesvc.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExceptionReportResponse {
  private ResponseType response;
  private boolean peek;

  public enum ResponseType {
    LOG_IT,
    DO_NOT_LOG_IT,
    SKIP_IT
  }
}
