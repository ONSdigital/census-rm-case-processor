package uk.gov.ons.census.casesvc.model.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Data;


public class UnlinkedCase {
    private String questionnaireId;
    private OffsetDateTime dateTime;
    private UUID caseId;
}
