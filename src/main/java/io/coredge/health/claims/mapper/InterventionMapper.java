package io.coredge.health.claims.mapper;

import io.coredge.health.claims.db.entity.intervention.*;
import io.coredge.health.claims.models.intervention.response.BusinessComponentResponse;

import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.beanutils.BeanUtilsBean;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InterventionMapper extends BeanUtilsBean {

  private final ModelMapper modelMapper;

  @Autowired
  public InterventionMapper(ModelMapper modelMapper) {
    this.modelMapper = modelMapper;
  }

  public List<BusinessComponentResponse> toBusinessComponentResponse(
      List<BusinessComponent> businessComponent) {
    if (businessComponent == null || businessComponent.isEmpty()) {
      return Collections.emptyList();
    }
    return businessComponent.stream().map(this::toBusinessComponentResponse).toList();
  }

  public BusinessComponentResponse toBusinessComponentResponse(
      BusinessComponent businessComponent) {
    if (businessComponent == null) {
      return null;
    }
    return modelMapper.map(businessComponent, BusinessComponentResponse.class);
  }

  @Override
  public void copyProperty(Object dest, String name, Object value) throws IllegalAccessException, InvocationTargetException {
    if (value != null) {
      super.copyProperty(dest, name, value);
    }
  }
}
