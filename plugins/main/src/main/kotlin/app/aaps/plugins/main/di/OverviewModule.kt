package app.aaps.plugins.main.di

import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.plugins.main.general.overview.OverviewFragment
import app.aaps.plugins.main.general.overview.OverviewMenusImpl
import app.aaps.plugins.main.general.overview.glass.GlassOverviewFragment
import app.aaps.plugins.main.general.overview.glass.GlassTreatmentsFragment
import app.aaps.plugins.main.general.overview.glass.GlassSensorInsertFragment
import app.aaps.plugins.main.general.overview.glass.GlassLoopDashboardFragment
import app.aaps.plugins.main.general.overview.glass.GlassInsulinDialogFragment
import app.aaps.plugins.main.general.overview.glass.GlassBatteryChangeDialogFragment
import app.aaps.plugins.main.general.overview.glass.GlassPrimeFillDialogFragment
import app.aaps.plugins.main.general.overview.glass.GlassSensorInsertDialogFragment
import app.aaps.plugins.main.general.overview.glass.GlassLoopControlDialogFragment
import app.aaps.plugins.main.general.overview.glass.GlassTempTargetDialogFragment
import app.aaps.plugins.main.general.overview.glass.GlycoStatsFragment
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.DismissNotificationService
import app.aaps.plugins.main.general.overview.notifications.NotificationWithAction
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module(
    includes = [
        OverviewModule.Bindings::class
    ]
)
@Suppress("unused")
abstract class OverviewModule {

    @ContributesAndroidInjector abstract fun contributesDismissNotificationService(): DismissNotificationService
    @ContributesAndroidInjector abstract fun contributesOverviewFragment(): OverviewFragment
    @ContributesAndroidInjector abstract fun contributesGlassOverviewFragment(): GlassOverviewFragment
    @ContributesAndroidInjector abstract fun contributesGlassTreatmentsFragment(): GlassTreatmentsFragment
    @ContributesAndroidInjector abstract fun contributesGlassSensorInsertFragment(): GlassSensorInsertFragment
    @ContributesAndroidInjector abstract fun contributesGlassLoopDashboardFragment(): GlassLoopDashboardFragment
    @ContributesAndroidInjector abstract fun contributesGlassInsulinDialogFragment(): GlassInsulinDialogFragment
    @ContributesAndroidInjector abstract fun contributesGlassBatteryChangeDialogFragment(): GlassBatteryChangeDialogFragment
    @ContributesAndroidInjector abstract fun contributesGlassPrimeFillDialogFragment(): GlassPrimeFillDialogFragment
    @ContributesAndroidInjector abstract fun contributesGlassSensorInsertDialogFragment(): GlassSensorInsertDialogFragment
    @ContributesAndroidInjector abstract fun contributesGlassLoopControlDialogFragment(): GlassLoopControlDialogFragment
    @ContributesAndroidInjector abstract fun contributesGlassTempTargetDialogFragment(): GlassTempTargetDialogFragment
    @ContributesAndroidInjector abstract fun contributesGlycoStatsFragment(): GlycoStatsFragment
    @ContributesAndroidInjector abstract fun notificationWithActionInjector(): NotificationWithAction
    @ContributesAndroidInjector abstract fun graphDataInjector(): GraphData

    @Module
    interface Bindings {

        @Binds fun bindOverviewMenus(overviewMenusImpl: OverviewMenusImpl): OverviewMenus
    }
}