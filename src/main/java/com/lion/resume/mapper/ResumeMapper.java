package com.lion.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lion.resume.entity.Resume;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResumeMapper extends BaseMapper<Resume> {
}
