package app.aaps.plugins.main.general.overview.glass

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class GlassLoopDashboardFragment : DaggerFragment() {

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var rxBus: RxBus

    private val viewModel: GlassLoopDashboardViewModel by viewModels()
    private val disposables = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val isDark = try {
            sp.getString(app.aaps.core.utils.R.string.key_use_dark_mode, "dark") == "dark"
        } catch (e: Exception) { true }

        viewModel.init(activePlugin, dateUtil, sp, aapsLogger, iobCobCalculator, glucoseStatusProvider, tddCalculator, isDark)
        viewModel.refreshData()

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                GlassOverviewTheme(isDarkMode = isDark) {
                    GlassLoopDashboardScreen(
                        uiState = uiState,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onRefresh = { viewModel.refreshData() },
                        isDark = isDark
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Auto-refresh when loop completes
        disposables += rxBus.toObservable(EventRefreshOverview::class.java)
            .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe({ viewModel.refreshData() }, { e -> aapsLogger.error("GlassLoopDashboard: Error on EventRefreshOverview", e) })

        // Also refresh on IOB calculation progress (fires during loop cycle)
        disposables += rxBus.toObservable(EventIobCalculationProgress::class.java)
            .observeOn(io.reactivex.rxjava3.android.schedulers.AndroidSchedulers.mainThread())
            .subscribe({ viewModel.refreshData() }, { e -> aapsLogger.error("GlassLoopDashboard: Error on EventIobCalculationProgress", e) })
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }
}
