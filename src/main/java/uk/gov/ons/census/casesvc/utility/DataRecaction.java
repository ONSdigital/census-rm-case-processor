package uk.gov.ons.census.casesvc.utility;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacCreatedDTO;

public class DataRecaction {
  public static void main(String[] params) {
    ResponseManagementEvent rme = new ResponseManagementEvent();
    PayloadDTO payload = new PayloadDTO();
    UacCreatedDTO uacCreated = new UacCreatedDTO();
    uacCreated.setUac("redactme");
    payload.setUacQidCreated(uacCreated);
    rme.setPayload(payload);

    redactSensitiveData(rme);

    System.out.println(uacCreated.getUac());
  }

  public static void redactSensitiveData(ResponseManagementEvent rme) {
    recursivelyRedact(rme, "setUac");
    recursivelyRedact(rme, "setUac");
  }

  public static void recursivelyRedact(Object object, String methodTofind) {
    Arrays.stream(object.getClass().getMethods())
        .filter(item -> Modifier.isPublic(item.getModifiers()))
        .forEach(
            method -> {
              if (method.getName().startsWith("get")
                  && method
                      .getReturnType()
                      .getPackageName()
                      .equals("uk.gov.ons.census.casesvc.model.dto")) {
                try {
                  Object invokeResult = method.invoke(object);
                  if (invokeResult != null) {
                    recursivelyRedact(invokeResult, methodTofind);
                  }
                } catch (IllegalAccessException e) {
                } catch (InvocationTargetException e) {
                }
              }

              if (method.getName().equals(methodTofind)
                  && method.getParameterTypes()[0].equals(String.class)) {
                try {
                  method.invoke(object, "XXX");
                } catch (IllegalAccessException e) {
                  e.printStackTrace();
                } catch (InvocationTargetException e) {
                  e.printStackTrace();
                }
              }
            });
  }
}
