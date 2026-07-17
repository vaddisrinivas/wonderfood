package com.wonderfood.core.engine

import com.wonderfood.core.model.CoreModelBoundary

public object CoreEngineBoundary {
    public const val MODULE_PATH: String = ":core:engine"

    public val upstreamModules: Set<String> = setOf(CoreModelBoundary.MODULE_PATH)
}
