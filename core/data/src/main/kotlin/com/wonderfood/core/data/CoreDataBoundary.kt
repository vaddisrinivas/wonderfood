package com.wonderfood.core.data

import com.wonderfood.core.engine.CoreEngineBoundary
import com.wonderfood.core.model.CoreModelBoundary

public object CoreDataBoundary {
    public const val MODULE_PATH: String = ":core:data"

    public val upstreamModules: Set<String> = setOf(
        CoreModelBoundary.MODULE_PATH,
        CoreEngineBoundary.MODULE_PATH,
    )
}
