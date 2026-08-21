package com.lion.resume.controller;

import com.lion.resume.common.R;
import com.lion.resume.entity.Resume;
import com.lion.resume.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

/**
 * 简历上传与查询
 */
@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    /**
     * 上传简历文件并解析入库
     *
     * @param file 上传的简历文件（MultipartFile）
     * @return 上传结果，包含 resumeId（简历ID）、fileName（文件名）、fileSize（文件大小）
     */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        Resume resume = resumeService.upload(file);
        Map<String, Object> data = new HashMap<>();
        data.put("resumeId", String.valueOf(resume.getId()));
        data.put("fileName", resume.getFileName());
        data.put("fileSize", resume.getFileSize());
        return R.ok(data);
    }

    /**
     * 查询简历详情
     *
     * @param id 简历ID
     * @return 简历实体信息
     */
    @GetMapping("/{id}")
    public R<Resume> detail(@PathVariable Long id) {
        return R.ok(resumeService.getById(id));
    }
}
