// Alterado pelo Tarciso
package app.aaps.plugins.main.skins

import android.util.TypedValue.COMPLEX_UNIT_PX
import android.view.View
import android.widget.LinearLayout
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.ActionsFragmentBinding
import app.aaps.plugins.main.databinding.OverviewFragmentBinding

interface SkinInterface {

    @get:StringRes val description: Int

    val mainGraphHeight: Int // in dp
    val secondaryGraphHeight: Int // in dp
    
    // Layout resource ID for overview fragment
    fun getOverviewLayoutId(): Int = R.layout.overview_fragment

    // no pre processing by default
    fun preProcessLandscapeActionsLayout(isLandscape: Boolean, binding: ActionsFragmentBinding) {
    }

    fun preProcessLandscapeOverviewLayout(binding: OverviewFragmentBinding, isLandscape: Boolean, isTablet: Boolean, isSmallHeight: Boolean) {
        if (isLandscape) {
            val iobLayout = binding.infoLayout.iobLayout
            // Alterado pelo Tarciso - Fix ClassCastException - Add type check before casting
            val iobLayoutParams = iobLayout.layoutParams
            if (iobLayoutParams !is ConstraintLayout.LayoutParams) return
            
            val timeLayout = binding.infoLayout.timeLayout
            //iobLayoutParams.startToStart = ConstraintLayout.LayoutParams.UNSET
            //iobLayoutParams.startToEnd = timeLayout.id
            //iobLayoutParams.topToBottom = ConstraintLayout.LayoutParams.UNSET
            //iobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            
            // Alterado pelo Tarciso - Fix ClassCastException - Add type check before casting
            val timeLayoutParams = timeLayout.layoutParams
            if (timeLayoutParams !is ConstraintLayout.LayoutParams) return
            timeLayoutParams.endToEnd = ConstraintLayout.LayoutParams.UNSET
            timeLayoutParams.endToStart = iobLayout.id
            
            //Tarciso. Tarciso removido a area do alarme de CARBO necessário
            //val cobLayoutParams = binding.infoLayout.cobLayout.layoutParams as ConstraintLayout.LayoutParams
            //cobLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            
            // Alterado pelo Tarciso - Fix ClassCastException - Add type check before casting
            val basalLayoutParams = binding.infoLayout.basalLayout.layoutParams
            if (basalLayoutParams !is ConstraintLayout.LayoutParams) return
            basalLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            
            // Alterado pelo Tarciso - Fix ClassCastException - Add type check before casting
            val extendedLayoutParams = binding.infoLayout.extendedLayout.layoutParams
            if (extendedLayoutParams !is ConstraintLayout.LayoutParams) return
            extendedLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            // Remove sensitivy e extended layout
            //val asLayoutParams = binding.infoLayout.asLayout.layoutParams as ConstraintLayout.LayoutParams
            //asLayoutParams.topToTop = ConstraintLayout.LayoutParams.PARENT_ID


            if (isTablet) {
                binding.infoLayout.apply {
                    // Removido 'bg' desta lista, pois agora é um ComposeView
                    // Tarciso removido a area do alarme de CARBO necessário e Sensitivity
                    // Texto original abaixo comentado:
                    //val texts = listOf(iob, cob, baseBasal, extendedBolus, sensitivity)
                    val texts = listOf(iob, baseBasal, extendedBolus)
                    for (v in texts) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.5f)
                    val textsTime = listOf(time, timeAgoShort)
                    for (v in textsTime) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 2.25f)
                }
                binding.apply {
                    val texts = listOf(pump, openaps, uploader)
                    for (v in texts) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                }
                binding.statusLightsLayout.apply {
                    val texts = listOf(cannulaAge, insulinAge, reservoirLevel, sensorAge, pbAge, pbLevel)
                    for (v in texts) v.setTextSize(COMPLEX_UNIT_PX, v.textSize * 1.3f)
                }
                timeLayout.orientation = LinearLayout.HORIZONTAL
                binding.infoLayout.timeAgoShort.setTextSize(COMPLEX_UNIT_PX, binding.infoLayout.time.textSize)
                // Tarciso Removido
                // binding.infoLayout.deltaLarge.visibility = View.VISIBLE
            } else {
                // Tarciso Removido
                //binding.infoLayout.deltaLarge.visibility = View.GONE
            }
        }
    }

    fun moveButtonsLayout(root: LinearLayout) {
        val buttonsLayout = root.findViewById<LinearLayout>(R.id.buttons_layout)
        root.removeView(buttonsLayout)
        val innerLayout = root.findViewById<LinearLayout>(R.id.inner_layout)
        innerLayout.addView(buttonsLayout)
    }

}
