package app.aaps.core.interfaces.overview

import android.content.Context

interface OverviewMenus {
    enum class CharType {
        PRE,
        TREAT,
        BAS,
        ABS,
        IOB,
        COB,
        DEV,
        BGI,
        SEN,
        ACT,
        DEVSLOPE,
        HR,
        STEPS
    }

    val setting: List<Array<Boolean>>
    fun loadGraphConfig()
    fun setupChartMenu(context: Context, chartButton: android.view.View)
    fun enabledTypes(graph: Int): String
    fun isEnabledIn(type: CharType): Int
}
