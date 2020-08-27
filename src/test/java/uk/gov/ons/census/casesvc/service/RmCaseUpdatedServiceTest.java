package uk.gov.ons.census.casesvc.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.RmCaseUpdated;
import uk.gov.ons.census.casesvc.model.entity.Case;

@RunWith(MockitoJUnitRunner.class)
public class RmCaseUpdatedServiceTest {
  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @Mock private Set<String> estabTypes;

  @InjectMocks private RmCaseUpdatedService underTest;

  @Test
  public void testHappyPath() {
    ResponseManagementEvent rme = new ResponseManagementEvent();

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    RmCaseUpdated rmCaseUpdated = new RmCaseUpdated();
    payload.setRmCaseUpdated(rmCaseUpdated);

    rmCaseUpdated.setTreatmentCode("TEST TreatmentCode CODE");
    rmCaseUpdated.setOa("TEST Oa CODE");
    rmCaseUpdated.setLsoa("TEST Lsoa CODE");
    rmCaseUpdated.setMsoa("TEST Msoa CODE");
    rmCaseUpdated.setLad("TEST Lad CODE");
    rmCaseUpdated.setFieldCoordinatorId("TEST FieldCoordinatorId CODE");
    rmCaseUpdated.setFieldOfficerId("TEST FieldOfficerId CODE");
    rmCaseUpdated.setLatitude("123.456");
    rmCaseUpdated.setLongitude("000.000");

    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(any())).thenReturn(new Case());
    when(estabTypes.contains(any())).thenReturn(true);

    underTest.processMessage(rme, messageTimestamp);
  }
}
