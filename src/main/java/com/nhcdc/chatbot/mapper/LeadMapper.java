package com.nhcdc.chatbot.mapper;

import com.nhcdc.chatbot.dto.lead.LeadResponseDto;
import com.nhcdc.chatbot.entity.Lead;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LeadMapper {

    @Mapping(target = "centreName", source = "centre.name")
    @Mapping(target = "serviceName", source = "serviceOffering.name")
    LeadResponseDto toDto(Lead lead);

    List<LeadResponseDto> toDtoList(List<Lead> leads);
}
