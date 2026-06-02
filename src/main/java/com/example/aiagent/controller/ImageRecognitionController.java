package com.example.aiagent.controller;

import com.example.aiagent.service.PythonRecognitionClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 图片识别控制器
 * 接收前端上传的图片，转发给Python服务器识别，返回识别结果
 *
 * 流程: 前端上传 → Java接收 → 转发Python服务器 → 返回识别结果
 */
@RestController
@RequestMapping("/api/image")
public class ImageRecognitionController {

    @Autowired
    private PythonRecognitionClient pythonClient;

    /**
     * 图片识别接口
     * 前端通过 multipart/form-data 上传图片
     *
     * @param image 图片文件（字段名: image）
     * @param lang  OCR语言（可选，默认 chi_sim+eng）
     * @param ocr   是否启用OCR（可选，默认 true）
     * @return 识别结果JSON
     */
    @PostMapping(value = "/recognize", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> recognizeImage(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "lang", defaultValue = "chi_sim+eng") String lang,
            @RequestParam(value = "ocr", defaultValue = "true") boolean ocr) {

        Map<String, Object> result = new HashMap<>();

        // 1. 校验图片
        if (image.isEmpty()) {
            result.put("success", false);
            result.put("error", "图片文件为空");
            return ResponseEntity.badRequest().body(result);
        }

        // 校验文件类型
        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            result.put("success", false);
            result.put("error", "仅支持图片文件（image/*）");
            return ResponseEntity.badRequest().body(result);
        }

        // 校验文件大小（限制10MB）
        if (image.getSize() > 10 * 1024 * 1024) {
            result.put("success", false);
            result.put("error", "图片大小不能超过10MB");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            // 2. 转发到Python服务器进行识别
            String recognitionResult = pythonClient.recognizeImage(image, lang, ocr);

            // 3. 返回识别结果
            result.put("success", true);
            result.put("data", recognitionResult);
            result.put("filename", image.getOriginalFilename());
            result.put("file_size", image.getSize());
            result.put("content_type", contentType);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "图片识别失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 批量图片识别（多文件上传）
     */
    @PostMapping(value = "/recognize/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> recognizeImages(
            @RequestParam("images") MultipartFile[] images,
            @RequestParam(value = "lang", defaultValue = "chi_sim+eng") String lang,
            @RequestParam(value = "ocr", defaultValue = "true") boolean ocr) {

        Map<String, Object> result = new HashMap<>();

        if (images.length == 0) {
            result.put("success", false);
            result.put("error", "未上传任何图片");
            return ResponseEntity.badRequest().body(result);
        }

        if (images.length > 10) {
            result.put("success", false);
            result.put("error", "单次最多上传10张图片");
            return ResponseEntity.badRequest().body(result);
        }

        try {
            java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();

            for (MultipartFile image : images) {
                Map<String, Object> item = new HashMap<>();
                item.put("filename", image.getOriginalFilename());
                item.put("file_size", image.getSize());

                try {
                    String recognitionResult = pythonClient.recognizeImage(image, lang, ocr);
                    item.put("success", true);
                    item.put("data", recognitionResult);
                } catch (Exception e) {
                    item.put("success", false);
                    item.put("error", e.getMessage());
                }

                results.add(item);
            }

            result.put("success", true);
            result.put("total", images.length);
            result.put("results", results);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "批量识别失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 检查Python服务器健康状态
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> checkPythonHealth() {
        Map<String, Object> healthInfo = pythonClient.checkHealth();
        return ResponseEntity.ok(healthInfo);
    }
}
