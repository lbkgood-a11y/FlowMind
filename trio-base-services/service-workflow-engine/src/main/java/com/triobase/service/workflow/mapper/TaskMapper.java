package com.triobase.service.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.workflow.entity.Task;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
