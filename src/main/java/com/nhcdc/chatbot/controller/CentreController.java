package com.nhcdc.chatbot.controller;

import com.nhcdc.chatbot.dto.centre.CentreDto;
import com.nhcdc.chatbot.mapper.CentreMapper;
import com.nhcdc.chatbot.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/centres")
@RequiredArgsConstructor
public class CentreController {

    private final ContentService contentService;
    private final CentreMapper centreMapper;

    @GetMapping
    public List<CentreDto> getActiveCentres() {
        return centreMapper.toDtoList(contentService.getActiveCentres());
    }
}
