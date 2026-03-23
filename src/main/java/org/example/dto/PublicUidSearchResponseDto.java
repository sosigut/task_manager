package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUidSearchResponseDto {

    private Long id;

    private String publicUid;

    private String firstName;

    private String lastName;

}
