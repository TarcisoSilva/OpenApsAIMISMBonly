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
import app.aaps.database.impl.AppRepository
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class GlassTreatmentsFragment : DaggerFragment() {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction

    private val viewModel: GlassTreatmentsViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val profile = profileFunction.getProfile()
        if (profile != null) {
            viewModel.loadData(repository, dateUtil, uel, activePlugin, profile)
        }

        val isDark = resolveIsDarkMode(sp)

        viewModel.setTheme(isDark)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                GlassOverviewTheme(isDarkMode = isDark) {
                    GlassTreatmentsScreen(
                        uiState = uiState,
                        onSelectTab = { viewModel.selectTab(it) },
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onItemLongClick = { viewModel.promptDelete(it) },
                        onDeleteConfirm = { viewModel.confirmDelete() },
                        onDeleteDismiss = { viewModel.dismissDeleteDialog() },
                        onInspectDismiss = { viewModel.inspectItem(null) },
                        isDark = isDark
                    )
                }
            }
        }
    }
}
