package com.docusphere.backend.ocr.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class PythonExecutor {

    @Value("${ocr.python.executable:python}")
    private String pythonExecutable;

    public String executeScript(String scriptPath, List<String> args) {
        return executeScript(scriptPath, args, Map.of());
    }

    public String executeScript(String scriptPath, List<String> args, Map<String, String> environment) {
        StringBuilder output = new StringBuilder();
        try {
            List<String> command = new ArrayList<>();
            command.add(pythonExecutable);
            command.add(scriptPath);
            command.addAll(args);

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().putAll(environment);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                log.error("Python script {} failed with exit code: {}. Output:\n{}", scriptPath, exitCode, output.toString());
                throw new RuntimeException("Python script execution failed. Exit code: " + exitCode);
            }

            return output.toString().trim();

        } catch (Exception e) {
            log.error("Error executing Python script {}: {}", scriptPath, e.getMessage());
            throw new RuntimeException("Error during Python script execution", e);
        }
    }
}
