package com.triobase.service.action.service;

import com.triobase.common.action.definition.ActionDefinition;

import java.util.Collection;

public interface ActionDefinitionProvider {

    Collection<ActionDefinition> definitions();
}
