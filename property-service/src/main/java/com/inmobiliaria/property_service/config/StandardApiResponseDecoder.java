package com.inmobiliaria.property_service.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inmobiliaria.property_service.dto.response.ApiResponse;
import feign.Response;
import feign.Util;
import feign.codec.Decoder;
import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Custom Feign Decoder that automatically unwraps the "data" field from the standardized
 * ApiResponse. This keeps the business services clean of the transport wrapper.
 */
public class StandardApiResponseDecoder implements Decoder {

  private final ObjectMapper objectMapper;

  public StandardApiResponseDecoder(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public Object decode(Response response, Type type) throws IOException {
    if (response.body() == null) {
      return null;
    }

    String bodyStr = Util.toString(response.body().asReader(Util.UTF_8));

    // Construct the type ApiResponse<T> where T is the expected return type of the Feign method
    JavaType apiResponseType =
        objectMapper
            .getTypeFactory()
            .constructParametricType(ApiResponse.class, objectMapper.constructType(type));

    ApiResponse<?> apiResponse = objectMapper.readValue(bodyStr, apiResponseType);

    if (apiResponse == null) {
      return null;
    }

    // You could also add logic here to throw custom exceptions if apiResponse.isSuccess() is false
    // but usually ErrorDecoder is better for non-2xx statuses.

    return apiResponse.getData();
  }
}
