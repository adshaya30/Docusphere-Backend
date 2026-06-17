package com.docusphere.backend.onlyoffice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OnlyOfficeCallback {
    private String key;
    private String url;
    private Integer status;
    private List<String> users;
    private String changesurl;
}
