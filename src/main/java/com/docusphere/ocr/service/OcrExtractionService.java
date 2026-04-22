package com.docusphere.ocr.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OcrExtractionService {

    @Value("${ocr.python.script.path:scripts/paddle_ocr.py}")
    private String pythonScriptPath;

    private final PythonExecutor pythonExecutor;

    public String extractText(String filePath) {
        Map<String, String> env = Map.of(
            "FLAGS_use_mkldnn", "1",
            "PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True"
        );
        return pythonExecutor.executeScript(pythonScriptPath, List.of(filePath), env);
    }
}

