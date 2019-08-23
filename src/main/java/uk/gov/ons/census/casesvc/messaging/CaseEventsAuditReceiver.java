package uk.gov.ons.census.casesvc.messaging;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;

@MessageEndpoint
public class CaseEventsAuditReceiver {

    private final EventLogger eventLogger;

    public CaseEventsAuditReceiver(EventLogger eventLogger) {
        this.eventLogger = eventLogger;
    }

    @Transactional
    @ServiceActivator(inputChannel = "surveyLaunchedInputChannel")
    public void receiveMessage(ResponseManagementEvent event) {
        if (event.getEvent().getType() == EventTypeDTO.SURVEY_LAUNCHED) {
            //eventLogger.logCaseEvent();
        } else {
            throw new RuntimeException(); // Unexpected event type received
        }
    }
}
