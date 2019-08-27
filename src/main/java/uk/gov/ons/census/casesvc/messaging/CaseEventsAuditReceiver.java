package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.model.entity.EventType.SURVEY_LAUNCED;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

@MessageEndpoint
public class CaseEventsAuditReceiver {

    private final EventLogger eventLogger;

    private final CaseRepository caseRepository;

    public CaseEventsAuditReceiver(EventLogger eventLogger, CaseRepository caseRepository) {
        this.eventLogger = eventLogger;
        this.caseRepository = caseRepository;
    }

    @Transactional
    @ServiceActivator(inputChannel = "surveyLaunchedInputChannel")
    public void receiveMessage(ResponseManagementEvent event) {
        if (event.getEvent().getType() == EventTypeDTO.SURVEY_LAUNCHED) {
            Optional<Case> surveyLaunchedForCase = caseRepository.findByCaseId(UUID.fromString(event.getPayload().getReceipt().getCaseId()));
            if (surveyLaunchedForCase.isPresent()) {
                eventLogger.logCaseEvent(
                        surveyLaunchedForCase.get(),
                        OffsetDateTime.now(),
                        "Survey launched",
                        SURVEY_LAUNCED, event.getEvent(),
                        convertObjectToJson(event.getPayload()));
            }
        } else {
            throw new RuntimeException(); // Unexpected event type received
        }
    }
}
