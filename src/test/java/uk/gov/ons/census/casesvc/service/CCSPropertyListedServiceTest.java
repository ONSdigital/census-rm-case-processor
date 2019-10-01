package uk.gov.ons.census.casesvc.service;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.anyString;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementCCSAddressListedEvent;

import java.util.UUID;
import org.jeasy.random.EasyRandom;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class CCSPropertyListedServiceTest {

  private static final UUID TEST_ACTION_PLAN_ID = UUID.randomUUID();
  private static final UUID TEST_COLLECTION_EXERCISE_ID = UUID.randomUUID();

  @Mock
  UacQidLinkRepository uacQidLinkRepository;

  @Mock
  UacService uacService;

  @Mock
  EventLogger eventLogger;

  @Mock
  CaseService caseService;

  @InjectMocks
  CCSPropertyListedService underTest;

  @Test
  public void testGoodCCSPropertyListed() {

    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementCCSAddressListedEvent();
    Case expectedCase = new EasyRandom().nextObject(Case.class);
    expectedCase.setUacQidLinks(null);
    expectedCase.setEvents(null);

    String expectedCaseId =
        managementEvent.getPayload().getCcsProperty().getCollectionCase().getId();

    ReflectionTestUtils.setField(underTest, "actionPlanId", TEST_ACTION_PLAN_ID.toString());
    ReflectionTestUtils
        .setField(underTest, "collectionExerciseId", TEST_COLLECTION_EXERCISE_ID.toString());

    when(caseService
        .saveCCSCase(expectedCaseId, managementEvent.getPayload().getCcsProperty().getSampleUnit(),
            TEST_ACTION_PLAN_ID.toString(), TEST_COLLECTION_EXERCISE_ID.toString()))
        .thenReturn(expectedCase);

    // When
    underTest.processCCSPropertyListed(managementEvent);

    // Then

  }
}