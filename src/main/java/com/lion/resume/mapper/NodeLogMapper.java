package com.lion.resume.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lion.resume.entity.NodeLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 节点执行日志 Mapper
 */
@Mapper
public interface NodeLogMapper extends BaseMapper<NodeLog> {

    /**
     * 按任务聚合统计：给「日志查询」页面提供任务下拉与概览数据。
     *
     * @param limit 返回最近 N 个任务
     * @return [{ taskId, nodeCount, totalDurationMs, firstStartTime, lastEndTime, errorCount }]
     */
    @Select("SELECT task_id AS taskId, " +
            "COUNT(*) AS nodeCount, " +
            "IFNULL(SUM(duration_ms), 0) AS totalDurationMs, " +
            "MIN(start_time) AS firstStartTime, " +
            "MAX(end_time) AS lastEndTime, " +
            "SUM(CASE WHEN status = 'error' THEN 1 ELSE 0 END) AS errorCount " +
            "FROM t_graph_node_log " +
            "GROUP BY task_id " +
            "ORDER BY MIN(start_time) DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectTaskSummary(@Param("limit") int limit);
}
