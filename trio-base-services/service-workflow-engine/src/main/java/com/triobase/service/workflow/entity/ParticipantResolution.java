package com.triobase.service.workflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.triobase.common.core.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_participant_resolution")
public class ParticipantResolution extends BaseEntity {
    private String resolutionKey;
    private String processInstanceId;
    private String nodeId;
    private String assignmentType;
    private String assignmentRef;
    private String participantVersion;
    private String participantsJson;
}
