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
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import dagger.android.support.DaggerFragment
import javax.inject.Inject

class GlassSensorInsertFragment : DaggerFragment() {

    @Inject lateinit var repository: AppRepository
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction

    private val viewModel: GlassSensorInsertViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val isDark = try {
            sp.getString(app.aaps.core.utils.R.string.key_use_dark_mode, "dark") == "dark"
        } catch (e: Exception) { true }

        viewModel.init(repository, dateUtil, uel, aapsLogger, profileFunction, isDark)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                GlassOverviewTheme(isDarkMode = isDark) {
                    GlassSensorInsertScreen(
                        uiState = uiState,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                        onDateSet = { y, m, d -> viewModel.updateDate(y, m, d) },
                        onTimeSet = { h, min -> viewModel.updateTime(h, min) },
                        onNotesChange = { viewModel.updateNotes(it) },
                        onSave = { viewModel.save() },
                        isDark = isDark
                    )
                }
            }
        }
    }
}
