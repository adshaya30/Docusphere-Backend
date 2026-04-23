package com.docusphere.backend.Common.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MessageResponse {
    private String message;
    private int statusCode = 200;
}
