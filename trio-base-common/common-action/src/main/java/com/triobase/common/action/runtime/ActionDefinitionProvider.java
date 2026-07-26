package com.triobase.common.action.runtime;

import com.triobase.common.action.definition.ActionDefinition;

import java.util.Collection;

public interface ActionDefinitionProvider {

    Collection<ActionDefinition> definitions();
}
