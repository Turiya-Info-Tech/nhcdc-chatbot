package com.nhcdc.chatbot.mapper;

import com.nhcdc.chatbot.dto.centre.CentreDto;
import com.nhcdc.chatbot.entity.Centre;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CentreMapper {

    CentreDto toDto(Centre centre);

    List<CentreDto> toDtoList(List<Centre> centres);
}
