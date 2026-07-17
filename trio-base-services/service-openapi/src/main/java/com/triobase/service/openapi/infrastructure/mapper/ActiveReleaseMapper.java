package com.triobase.service.openapi.infrastructure.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.triobase.service.openapi.domain.entity.ActiveRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ActiveReleaseMapper extends BaseMapper<ActiveRelease> {

    @Select("""
            SELECT route_definition_id, environment, release_snapshot_id, policy_version,
                   activated_by, activated_at, row_version
            FROM oa_active_release
            WHERE route_definition_id = #{routeDefinitionId} AND environment = #{environment}
            """)
    ActiveRelease find(@Param("routeDefinitionId") String routeDefinitionId,
                       @Param("environment") String environment);

    @Insert("""
            INSERT INTO oa_active_release(
                route_definition_id, environment, release_snapshot_id, policy_version,
                activated_by, activated_at, row_version)
            VALUES(#{routeDefinitionId}, #{environment}, #{releaseSnapshotId}, #{policyVersion},
                   #{activatedBy}, #{activatedAt}, 0)
            ON CONFLICT (route_definition_id, environment) DO NOTHING
            """)
    int insertIfAbsent(ActiveRelease activeRelease);

    @Update("""
            UPDATE oa_active_release
            SET release_snapshot_id = #{releaseSnapshotId},
                policy_version = #{policyVersion},
                activated_by = #{activatedBy},
                activated_at = #{activatedAt},
                row_version = row_version + 1
            WHERE route_definition_id = #{routeDefinitionId}
              AND environment = #{environment}
              AND row_version = #{expectedRowVersion}
            """)
    int compareAndSet(@Param("routeDefinitionId") String routeDefinitionId,
                      @Param("environment") String environment,
                      @Param("releaseSnapshotId") String releaseSnapshotId,
                      @Param("policyVersion") Long policyVersion,
                      @Param("activatedBy") String activatedBy,
                      @Param("activatedAt") java.time.LocalDateTime activatedAt,
                      @Param("expectedRowVersion") Long expectedRowVersion);
}
