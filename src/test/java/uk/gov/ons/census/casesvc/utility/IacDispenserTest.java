package uk.gov.ons.census.casesvc.utility;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.client.InternetAccessCodeSvcClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class IacDispenserTest {
  @Mock private InternetAccessCodeSvcClient internetAccessCodeSvcClient;

  @InjectMocks private IacDispenser underTest;

  @Test
  public void testDispenserDispenses() {
    // Given
    ReflectionTestUtils.setField(underTest, "iacPoolSizeMin", 500);
    ReflectionTestUtils.setField(underTest, "iacPoolSizeMax", 1000);
    when(internetAccessCodeSvcClient.generateIACs(anyInt()))
        .thenReturn(Collections.singletonList("Foo"));

    // When
    String actualResult = underTest.getIacCode();

    // Then
    assertEquals("Foo", actualResult);
  }

  @Test
  public void testDispenserTopsItselfUp() {
    // Given
    List<String> iacCodes = new ArrayList<>(1000);
    for (int i = 0; i < 1000; i++) {
      iacCodes.add("Bar");
    }
    ReflectionTestUtils.setField(underTest, "iacPoolSizeMin", 500);
    ReflectionTestUtils.setField(underTest, "iacPoolSizeMax", 1000);
    when(internetAccessCodeSvcClient.generateIACs(anyInt())).thenReturn(iacCodes);

    // When
    for (int i = 0; i < 502; i++) {
      underTest.getIacCode();
    }

    // Then
    verify(internetAccessCodeSvcClient, times(2)).generateIACs(anyInt());
  }
}
