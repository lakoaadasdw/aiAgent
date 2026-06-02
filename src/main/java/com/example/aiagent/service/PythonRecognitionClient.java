package com.example.aiagent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Python 图片识别服务器客户端
 * 负责将图片转发给 Python 服务器进行识别并返回结果
 */
@Service
public class PythonRecognitionClient {

    private final RestTemplate restTemplate;

    @Value("${python.server.url:http://localhost:5001}")
    private String pythonServerUrl;

    public PythonRecognitionClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 上传图片到 Python 服务器进行识别
     *
     * @param imageFile 上传的图片文件
     * @param ocrLang   OCR语言（默认 chi_sim+eng）
     * @param enableOcr 是否启用OCR
     * @return Python服务器返回的JSON字符串
     */
    public String recognizeImage(MultipartFile imageFile, String ocrLang, boolean enableOcr) throws IOException {
        String url = pythonServerUrl + "/recognize";

        // 构建 multipart 请求
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();

        // 添加图片文件
        ByteArrayResource fileResource = new ByteArrayResource(imageFile.getBytes()) {
            @Override
            public String getFilename() {
                return imageFile.getOriginalFilename() != null ?
                        imageFile.getOriginalFilename() : "image.jpg";
            }
        };
        body.add("image", fileResource);

        // 添加参数
        body.add("lang", ocrLang != null ? ocrLang : "chi_sim+eng");
        body.add("ocr", String.valueOf(enableOcr));

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    requestEntity,
                    String.class
            );
            return response.getBody();
        } catch (Exception e) {
            // 如果 Python 服务器不可用，返回错误信息
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("error", "Python图片识别服务器连接失败: " + e.getMessage());
            errorResult.put("python_server_url", pythonServerUrl);
            errorResult.put("suggestion", "请确保Python服务器已启动: cd python_server && pip install -r requirements.txt && python app.py");
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(errorResult);
        }
    }

    /**
     * 简化调用 - 使用默认参数
     */
    public String recognizeImage(MultipartFile imageFile) throws IOException {
        return recognizeImage(imageFile, "chi_sim+eng", true);
    }

    /**
     * 检查 Python 服务器健康状态
     */
    public Map<String, Object> checkHealth() {
        String url = pythonServerUrl + "/health";
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            Map<String, Object> result = new HashMap<>();
            result.put("connected", true);
            result.put("response", response.getBody());
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("connected", false);
            result.put("error", e.getMessage());
            return result;
        }
    }
}
