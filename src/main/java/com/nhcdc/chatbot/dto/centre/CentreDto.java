package com.nhcdc.chatbot.dto.centre;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CentreDto {
    private Long id;
    private String name;
    private String address;
    private String contactNumber;
    private String mapsLink;
}
