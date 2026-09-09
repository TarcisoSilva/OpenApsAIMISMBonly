package app.aaps.plugins.main.general.tools.glass

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment

class GlassToolsFragment : Fragment() {

    var onOpenActions: (() -> Unit)? = null
    var onOpenOref: (() -> Unit)? = null
    var onOpenProfile: (() -> Unit)? = null
    var onOpenAutomation: (() -> Unit)? = null
    var onOpenNsClient: (() -> Unit)? = null
    var onOpenTidepool: (() -> Unit)? = null
    var onOpenXdrip: (() -> Unit)? = null
    var onOpenMaintenance: (() -> Unit)? = null
    var onOpenXdripBg: (() -> Unit)? = null
    var isDark: Boolean = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GlassToolsScreen(
                    isDark = isDark,
                    onOpenActions = { onOpenActions?.invoke() },
                    onOpenOref = { onOpenOref?.invoke() },
                    onOpenProfile = { onOpenProfile?.invoke() },
                    onOpenAutomation = { onOpenAutomation?.invoke() },
                    onOpenNsClient = { onOpenNsClient?.invoke() },
                    onOpenTidepool = { onOpenTidepool?.invoke() },
                    onOpenXdrip = { onOpenXdrip?.invoke() },
                    onOpenMaintenance = { onOpenMaintenance?.invoke() },
                    onOpenXdripBg = { onOpenXdripBg?.invoke() }
                )
            }
        }
    }
}
