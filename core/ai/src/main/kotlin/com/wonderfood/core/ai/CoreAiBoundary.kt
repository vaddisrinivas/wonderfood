package com.wonderfood.core.ai

import com.wonderfood.core.engine.CoreEngineBoundary
import com.wonderfood.core.model.CoreModelBoundary

public object CoreAiBoundary {
    public const val MODULE_PATH: String = ":core:ai"

    public val upstreamModules: Set<String> = setOf(
        CoreModelBoundary.MODULE_PATH,
        CoreEngineBoundary.MODULE_PATH,
    )
}
