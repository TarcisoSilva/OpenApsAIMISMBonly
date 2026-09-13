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
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.stats.TirCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.sharedPreferences.SP
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class GlycoStatsFragment : DaggerFragment() {

    @Inject lateinit var tddCalculator: TddCalculator
    @Inject lateinit var tirCalculator: TirCalculator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP

    private val viewModel: GlycoStatsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Carrega os dados reais
        viewModel.loadData(tddCalculator, tirCalculator, dateUtil)

        // Lê o tema do SP
        val isDark = resolveIsDarkMode(sp)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                GlassOverviewTheme(isDarkMode = isDark) {
                    GlycoStatsScreen(
                        uiState = uiState,
                        isDark = isDark,
                        onSelectDay = { viewModel.selectDay(it) },
                        onSelectTirTarget = { viewModel.setTirTarget(it) },
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() }
                    )
                }
            }
        }
    }
}
