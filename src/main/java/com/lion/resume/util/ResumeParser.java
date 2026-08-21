package com.lion.resume.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 简历文件解析工具：抽取 PDF / TXT / MD / JSON 中的纯文本
 * 说明：真实生产环境可替换为 OCR 服务（如阿里云文档智能、百度 OCR）以支持扫描件
 */
public final class ResumeParser {

    private ResumeParser() {
    }

    public static String extractText(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (name == null) {
            name = "";
        }
        String lower = name.toLowerCase();
        byte[] bytes = file.getBytes();
        if (lower.endsWith(".pdf")) {
            return extractPdf(bytes);
        }
        // txt / md / json / 其他纯文本
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static String extractPdf(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }
}
