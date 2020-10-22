package uk.gov.ons.census.casesvc.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Arrays;

public class RedactHelper {
  private static final String REDACTION_TARGET = "setUac";
  private static final String REDACT_TO_THIS_VALUE = "XxxxREDACTEDxxxX";
  private static final ObjectMapper objectMapper = ObjectMapperFactory.objectMapper();

  public static Object redact(Object rootObjectToRedact) {
    try {
      Object rootObjectToRedactDeepCopy =
          objectMapper.readValue(
              objectMapper.writeValueAsString(rootObjectToRedact), rootObjectToRedact.getClass());
      recursivelyRedact(rootObjectToRedactDeepCopy, REDACTION_TARGET, rootObjectToRedactDeepCopy.getClass().getPackageName());
      return rootObjectToRedactDeepCopy;
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Failed to redact sensitive data", e);
    }
  }

  private static void recursivelyRedact(Object object, String methodTofind, String packageName) {
    Arrays.stream(object.getClass().getMethods())
        .filter(item -> Modifier.isPublic(item.getModifiers()))
        .forEach(
            method -> {
              if (method.getName().startsWith("get")
                  && method.getReturnType().getPackageName().equals(packageName)) {
                try {
                  Object invokeResult = method.invoke(object);
                  if (invokeResult != null) {
                    recursivelyRedact(invokeResult, methodTofind, packageName);
                  }
                } catch (IllegalAccessException | InvocationTargetException e) {
                  // Ignored
                }
              }

              if (method.getName().equals(methodTofind)
                  && method.getParameterTypes()[0].equals(String.class)) {
                try {
                  method.invoke(object, REDACT_TO_THIS_VALUE);
                } catch (IllegalAccessException | InvocationTargetException e) {
                  // Ignored
                }
              }
            });
  }
}
