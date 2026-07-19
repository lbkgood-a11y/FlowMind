package com.triobase.service.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.auth.entity.SysAuthVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AuthVersionMapper extends BaseMapper<SysAuthVersion> {

    @Update("""
            UPDATE sys_auth_version
            SET version_value = version_value + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE version_key = #{versionKey}
            """)
    int bump(@Param("versionKey") String versionKey);
}
