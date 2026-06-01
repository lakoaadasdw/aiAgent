package com.example.aiagent.Tools;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.awt.event.FocusEvent;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;


/**
 * agent工具类
 */
@SuppressWarnings({"all"})
@Component
public class ResultTools {


    private static final Logger log = LoggerFactory.getLogger(ResultTools.class);

    @Tool(description = "AI创建文件专用功能,在用户未声明具体的文件名,地址,内容时ai自行决定,指定了则严格按照用户指定的内容")
    public String createFile(String address, String name, String content) {
        try {
            Path path = Path.of(address);

//          当文件路径不存在就创建
            if (!Files.exists(path)){
                Files.createDirectories(path);
                log.info("创建目录成功");
            }
//          创建文件
            BufferedWriter writer = Files.newBufferedWriter(path.resolve(name));
            writer.write(content);
            writer.flush();
            writer.close();
        }catch (IOException e){
            log.info("创建目录失败：{}",e.getMessage());
            return "创建目录失败：" + e.getMessage();
        }
        return "创建成功";
    }


    @Tool(description = "AI删除文件专用功能,在用户未声明具体的文件名,地址时ai自行决定,指定了则严格按照用户指定的内容")
    public String deleteFile(String address, String name) {
        try {
            Path path = Path.of(address);
            Files.deleteIfExists(path.resolve(name));
        }catch (IOException e){
            log.info("删除文件失败：{}",e.getMessage());
            return "删除文件失败：" + e.getMessage();
        }
        return "删除成功";
    }

    @Tool(description = "AI修改文件专用功能,在用户未声明具体的文件名,地址,内容时ai自行决定,指定了则严格按照用户指定的内容")
    public String modifyFile(String address, String name, String content) {
        try {
            Path path = Path.of(address);
            BufferedWriter writer = Files.newBufferedWriter(path.resolve(name));
            writer.write(content);
            writer.flush();
            writer.close();
        }catch (IOException e){
            log.info("修改文件失败：{}",e.getMessage());
            return "修改文件失败：" + e.getMessage();
        }
        return "修改成功";
    }

    @Tool(description = "AI查看文件专用功能,在用户未声明具体的文件名,地址时ai自行决定,指定了则严格按照用户指定的内容")
    public String viewFile(String address, String name) {
        try {
            Path path = Path.of(address);
            String content = Files.readString(path.resolve(name));
            return content;
        }catch (IOException e){
            log.info("查看文件失败：{}",e.getMessage());
            return "查看文件失败：" + e.getMessage();
        }
    }
}
