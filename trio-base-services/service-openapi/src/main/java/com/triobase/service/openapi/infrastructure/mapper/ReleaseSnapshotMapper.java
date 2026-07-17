package com.triobase.service.openapi.infrastructure.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.ReleaseSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ReleaseSnapshotMapper extends BaseMapper<ReleaseSnapshot> {

    @Select("SELECT 1 FROM (SELECT pg_advisory_xact_lock(hashtextextended(#{seriesKey}, 0))) locked")
    Integer lockReleaseSeries(@Param("seriesKey") String seriesKey);

    @Select("""
            SELECT COALESCE(MAX(release_number), 0) + 1
            FROM oa_release_snapshot
            WHERE route_definition_id = #{routeDefinitionId} AND environment = #{environment}
            """)
    Integer nextReleaseNumber(@Param("routeDefinitionId") String routeDefinitionId,
                              @Param("environment") String environment);
}
