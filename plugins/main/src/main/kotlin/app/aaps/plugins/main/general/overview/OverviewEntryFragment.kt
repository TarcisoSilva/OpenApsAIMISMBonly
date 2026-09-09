// MODIFICAÇÃO EM:
// plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewEntryFragment.kt
//
// No método showSelectedOverview(), substitua o fragmento carregado pelo GlassOverviewFragment:

package app.aaps.plugins.main.general.overview

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.commit
import app.aaps.plugins.main.databinding.FragmentOverviewEntryBinding
import app.aaps.plugins.main.general.overview.glass.GlassOverviewFragment // <--- ADICIONE ESTA IMPORTAÇÃO
import dagger.android.support.DaggerFragment

class OverviewEntryFragment : DaggerFragment() {

    private var _binding: FragmentOverviewEntryBinding? = null
    private val binding get() = _binding!!
    private var currentTag: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOverviewEntryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        showGlassOverview()
    }

    /**
     * Carrega a nova interface Glassmorphism moderna (compatível com Light e Midnight Glass)
     */
    private fun showGlassOverview() {
        val binding = _binding ?: return
        val newTag = GLASS_OVERVIEW_TAG
        if (newTag == currentTag && childFragmentManager.findFragmentByTag(newTag) != null) return

        val fragment = GlassOverviewFragment()
        childFragmentManager.commit {
            setReorderingAllowed(true)
            replace(binding.overviewEntryContainer.id, fragment, newTag)
        }
        currentTag = newTag
    }

    companion object {
        private const val GLASS_OVERVIEW_TAG = "overview_glass"
    }
}
