package com.nhcdc.chatbot.mapper;

import com.nhcdc.chatbot.dto.service.ServiceOfferingDto;
import com.nhcdc.chatbot.entity.ServiceOffering;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ServiceOfferingMapper {

    ServiceOfferingDto toDto(ServiceOffering serviceOffering);

    List<ServiceOfferingDto> toDtoList(List<ServiceOffering> serviceOfferings);
}
