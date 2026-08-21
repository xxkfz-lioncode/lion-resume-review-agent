package com.lion.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lion.resume.common.exception.BizException;
import com.lion.resume.entity.Resume;
import com.lion.resume.mapper.ResumeMapper;
import com.lion.resume.util.ResumeParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 简历服务：上传存储、文本抽取
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeMapper resumeMapper;

    @Value("${resume.upload-dir:./data/uploads}")
    private String uploadDir;

    /**
     * 上传简历文件
     */
    public Resume upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            originalName = "resume_" + System.currentTimeMillis();
        }
        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            String storeName = UUID.randomUUID().toString().replace("-", "")
                    + "_" + originalName;
            Path target = dir.resolve(storeName);
            file.transferTo(target.toFile());

            Resume resume = new Resume();
            resume.setFileName(originalName);
            resume.setFilePath(target.toString());
            resume.setFileSize(file.getSize());
            resume.setContentType(file.getContentType());
            resume.setParseStatus("PENDING");
            resume.setCreateTime(LocalDateTime.now());
            resume.setUpdateTime(LocalDateTime.now());
            resumeMapper.insert(resume);
            log.info("简历上传成功: id={}, name={}", resume.getId(), originalName);
            return resume;
        } catch (IOException e) {
            throw new BizException(500, "简历文件保存失败: " + e.getMessage());
        }
    }

    /**
     * 抽取简历文本（PDF / TXT / MD / JSON）
     */
    public String extractText(Resume resume) {
        try {
            Path path = Paths.get(resume.getFilePath());
            byte[] bytes = Files.readAllBytes(path);
            return extractFromBytes(bytes, resume.getFileName());
        } catch (Exception e) {
            log.warn("简历文本抽取失败, id={}: {}", resume.getId(), e.getMessage());
            return "";
        }
    }

    private String extractFromBytes(byte[] bytes, String fileName) throws IOException {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return ResumeParser.extractPdf(bytes);
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public Resume getById(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) {
            throw new BizException(404, "简历不存在: " + id);
        }
        return resume;
    }

    /**
     * 按文件名模糊查询，仅返回匹配的简历 ID（只查 id 列）
     */
    public List<Long> listIdsByFileNameLike(String keyword) {
        LambdaQueryWrapper<Resume> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.select(Resume::getId)
                .like(Resume::getFileName, keyword);
        return resumeMapper.selectObjs(queryWrapper).stream()
                .map(obj -> Long.valueOf(String.valueOf(obj)))
                .toList();
    }

    /**
     * 按 ID 集合批量查询简历
     */
    public List<Resume> listByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return resumeMapper.selectBatchIds(ids);
    }

    public void update(Resume resume) {
        resume.setUpdateTime(LocalDateTime.now());
        resumeMapper.updateById(resume);
    }
}
