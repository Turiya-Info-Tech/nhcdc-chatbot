package com.nhcdc.chatbot.controller;

import com.nhcdc.chatbot.dto.service.ServiceOfferingDto;
import com.nhcdc.chatbot.mapper.ServiceOfferingMapper;
import com.nhcdc.chatbot.service.ContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceOfferingController {

    private final ContentService contentService;
    private final ServiceOfferingMapper serviceOfferingMapper;

    @GetMapping
    public List<ServiceOfferingDto> getActiveServices() {
        return serviceOfferingMapper.toDtoList(contentService.getActiveServices());
    }
}
