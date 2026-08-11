// Alterado pelo Tarciso
package app.aaps.plugins.main.general.overview

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.Context
import android.view.MotionEvent
import android.content.Intent
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AnimationDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnLongClickListener
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.text.toSpanned
import androidx.recyclerview.widget.LinearLayoutManager
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.extensions.runOnUiThread
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.extensions.toVisibilityKeepSpace
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSSettingsStatus
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAcceptOpenLoopChange
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventMobileToWear
import app.aaps.core.interfaces.rx.events.EventNewOpenLoopNotification
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventScale
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewGraph
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.interfaces.source.XDripSource
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.main.extensions.directionToIcon
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.iob.displayText
import app.aaps.core.main.wizard.QuickWizard
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.Sources
import app.aaps.database.entities.interfaces.end
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.main.R
import app.aaps.plugins.main.databinding.OverviewFragmentBinding
import app.aaps.plugins.main.general.overview.graphData.GraphData
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import app.aaps.plugins.main.general.overview.ui.StatusLightHandler
import app.aaps.plugins.main.skins.SkinProvider
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.BaseSeries
import dagger.android.HasAndroidInjector
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class OverviewFragment : DaggerFragment(), View.OnClickListener, OnLongClickListener {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var statusLightHandler: StatusLightHandler
    @Inject lateinit var processedDeviceStatusData: ProcessedDeviceStatusData
    @Inject lateinit var nsSettingsStatus: NSSettingsStatus
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var dexcomBoyda: DexcomBoyda
    @Inject lateinit var xDripSource: XDripSource
    @Inject lateinit var notificationStore: NotificationStore
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var config: Config
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var overviewMenus: OverviewMenus
    @Inject lateinit var skinProvider: SkinProvider
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var automation: Automation
    @Inject lateinit var bgQualityCheck: BgQualityCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter


    private val disposable = CompositeDisposable()

    private var smallWidth = false
    private var smallHeight = false
    private lateinit var dm: DisplayMetrics
    private var axisWidth: Int = 0
    private lateinit var refreshLoop: Runnable
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val secondaryGraphs = ArrayList<GraphView>()
    private val secondaryGraphsLabel = ArrayList<TextView>()

    private var carbAnimation: AnimationDrawable? = null

    // Variáveis para zoom dinâmico com gesto de pinça
    private var currentTimeRangeHours = 6.0 // Começar com 6 horas por padrão
    private var suppressFullGraphUpdate = false




    private var _binding: OverviewFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // Inflate the correct layout based on active skin
        val layoutId = skinProvider.activeSkin().getOverviewLayoutId()

        //check screen width
        dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            activity?.display?.getRealMetrics(dm)
        else
            activity?.windowManager?.defaultDisplay?.getMetrics(dm)

        return inflater.inflate(layoutId, container, false).also { view ->
            _binding = OverviewFragmentBinding.bind(view)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inicializar o range de tempo atual com o valor salvo ou padrão de 4 horas
        currentTimeRangeHours = overviewData.rangeToDisplay.toDouble()
        if (currentTimeRangeHours == 0.0) {
            currentTimeRangeHours = 4.0
            overviewData.rangeToDisplay = 4
        }

        // pre-process landscape mode
        val screenWidth = dm.widthPixels
        val screenHeight = dm.heightPixels
        smallWidth = screenWidth <= Constants.SMALL_WIDTH
        smallHeight = screenHeight <= Constants.SMALL_HEIGHT
        val landscape = screenHeight < screenWidth


        // Adiciona o OnClickListener ao iobLayout (Botão IOB)
        binding.infoLayout.iobLayout.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runInsulinDialog(childFragmentManager) }
                )
            }
        }

        // Adiciona o OnClickListener ao COB Layout (Botão COB) → Carbs dialog
        /*binding.infoLayout.cobLayout.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runCarbsDialog(childFragmentManager) }
                )
            }
        }*/

        // Adiciona o OnClickListener ao BG (valor de glicose) → Loop dialog
        binding.infoLayout.bg.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 1) }
                )
            }
        }

        // Adiciona o OnClickListener ao Arrow (seta de tendência) → Loop dialog
        binding.infoLayout.arrow.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 1) }
                )
            }
        }

        // Adiciona o OnClickListener ao Arrow (seta de tendência) → Loop dialog
        binding.infoLayout.timeAgo.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 1) }
                )
            }
        }

        // Adiciona o OnClickListener ao Delta → Loop dialog
        binding.infoLayout.delta.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 1) }
                )
            }
        }

        // Adiciona o OnClickListener ao Temp_Target_ICON (Botão TT)
        binding.infoLayout.tempTargetIcon.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runTempTargetDialog(childFragmentManager) }
                )
            }
        }








        skinProvider.activeSkin().preProcessLandscapeOverviewLayout(binding, landscape, rh.gb(app.aaps.core.ui.R.bool.isTablet), smallHeight)
        binding.nsclientCard.visibility = config.NSCLIENT.toVisibility()

        binding.notifications.setHasFixedSize(false)
        binding.notifications.layoutManager = LinearLayoutManager(view.context)
        axisWidth = if (dm.densityDpi <= 120) 3 else if (dm.densityDpi <= 160) 10 else if (dm.densityDpi <= 320) 35 else if (dm.densityDpi <= 420) 50 else if (dm.densityDpi <= 560) 70 else 80

        // Configurar grid do gráfico BG com linhas tracejadas (semelhante à imagem de referência)
        binding.graphsLayout.bgGraph.gridLabelRenderer?.apply {
            gridColor = rh.gac(context, app.aaps.core.ui.R.attr.graphGrid)
            gridStyle = com.jjoe64.graphview.GridLabelRenderer.GridStyle.BOTH
            isHighlightZeroLines = false

            // 🔒 Estabilizar eixo horizontal (evita labels aleatórios)
            // setHumanRounding(false)
            numHorizontalLabels = overviewData.rangeToDisplay + 1

            reloadStyles()
            labelVerticalWidth = axisWidth
        }

        binding.graphsLayout.bgGraph.layoutParams?.height = rh.dpToPx(skinProvider.activeSkin().mainGraphHeight)

        //Tarciso. Removendo o icone de alarme de Carbo necessário (icone de trigo)
        //carbAnimation = binding.infoLayout.carbsIcon.background as AnimationDrawable?
        //carbAnimation?.setEnterFadeDuration(1200)
        //carbAnimation?.setExitFadeDuration(1200)

        // Configurar zoom NATIVO do GraphView (HABILITADO para pinça funcionar)
        binding.graphsLayout.bgGraph.viewport.isScalable = true
        binding.graphsLayout.bgGraph.viewport.isScrollable = false

        // Runnable para sincronização contínua em tempo real
        val syncRunnable = object : Runnable {
            override fun run() {
                _binding?.let {
                    val minX = it.graphsLayout.bgGraph.viewport.getMinX(false)
                    val maxX = it.graphsLayout.bgGraph.viewport.getMaxX(false)

                    secondaryGraphs.forEach { graph ->
                        graph.viewport.setMinX(minX)
                        graph.viewport.setMaxX(maxX)
                        graph.viewport.setXAxisBoundsManual(true)
                        graph.postInvalidate()
                    }
                    it.graphsLayout.bgGraph.postDelayed(this, 16)
                }
            }
        }

        // --- MOVIMENTO DE PINÇA (ZOOM LIVRE) ---
        // Variáveis para rastrear o estado do toque (fora do listener)
        var touchStartX = 0f
        var touchStartY = 0f
        var isTouchClick = true
        val CLICK_THRESHOLD = 10f // pixels

        binding.graphsLayout.bgGraph.setOnTouchListener { v, event ->
            val graph = binding.graphsLayout.bgGraph
            val viewport = graph.viewport

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.parent.requestDisallowInterceptTouchEvent(true)
                    suppressFullGraphUpdate = true
                    v.removeCallbacks(syncRunnable)
                    v.post(syncRunnable)

                    // Guarda posição inicial para detectar click vs arrasto
                    touchStartX = event.x
                    touchStartY = event.y
                    isTouchClick = true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Se houve movimento significativo, não é um click simples
                    if (abs(event.x - touchStartX) > CLICK_THRESHOLD ||
                        abs(event.y - touchStartY) > CLICK_THRESHOLD) {
                        isTouchClick = false
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent.requestDisallowInterceptTouchEvent(false)
                    v.removeCallbacks(syncRunnable)

                    if (isTouchClick) {
                        // 🔥 CLICK SIMPLES: apenas mostrar detalhes, NÃO alterar zoom
                        // O GraphView já tem seu próprio handler para mostrar detalhes dos pontos
                        // Não precisamos fazer nada aqui, deixe o GraphView processar o click

                        // 🔥 IMPORTANTE: Reseta a flag suppression
                        suppressFullGraphUpdate = false
                    } else {
                        // 🔥 ARRASTE/PINÇA: aplicar zoom baseado no viewport REAL
                        v.postDelayed({
                            // Capturar o viewport ATUAL (após o gesto de pinça do GraphView)
                            val currentMinX = viewport.getMinX(false).toLong()
                            val currentMaxX = viewport.getMaxX(false).toLong()

                            // Calcular a duração em milissegundos
                            val durationMs = currentMaxX - currentMinX
                            
                            // Converter para horas (arredondado)
                            val durationHours = (durationMs / (60.0 * 60 * 1000)).roundToInt()

                            // Limitar entre 3 e 24 horas
                            val targetHours = when {
                                durationHours < 3 -> 3
                                durationHours > 24 -> 24
                                else -> durationHours
                            }

                            aapsLogger.debug("🔍 Pinch: viewport=$durationMs ms (~$durationHours h) -> target=$targetHours h")

                            // Atualizar usando EventScale para consistência
                            rxBus.send(EventScale(targetHours, 0))

                        }, 50) // Pequeno delay para garantir que o GraphView terminou o gesto
                    }
                }
            }

            false
        }





        // Remover listener do IOB graph (zoom nativo funciona independente) ou sincronizar
        // Para sincronizar o IOB com o BG, precisaríamos de um listener de viewport change no BG
        // Mas por enquanto vamos focar na performance do BG.
        binding.graphsLayout.iobGraph.setOnTouchListener(null)

        binding.graphsLayout.bgGraph.setOnLongClickListener {
            overviewData.rangeToDisplay += 6
            overviewData.rangeToDisplay = if (overviewData.rangeToDisplay > 24) 6 else overviewData.rangeToDisplay
            sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, overviewData.rangeToDisplay)
            rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.utils.R.string.key_rangetodisplay)))
            sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, true)
            false
        }


        // New graph buttons (Original Skin)
        binding.graphsLayout.statsButton.setOnClickListener {
            // Open Statistics activity (TIR, TDD, Activity Monitor)
            startActivity(Intent(context, uiInteraction.statsActivity))
        }

        /*

        binding.graphsLayout.statsButton?.setOnClickListener{
            // Open Statistics activity (TIR, TDD, Activity Monitor)
            startActivity(Intent(context, uiInteraction.tddStatsActivity))
        }
        binding.graphsLayout.graph6hButton?.setOnClickListener(this)
        binding.graphsLayout.graph12hButton?.setOnClickListener(this)
        binding.graphsLayout.graph18hButton?.setOnClickListener(this)
        binding.graphsLayout.graph24hButton?.setOnClickListener(this)
        binding.graphsLayout.graphMenuButton?.setOnClickListener(this)

        */

        // Botões 6h, 12h, 18h, 24h
        binding.graphsLayout.graph6hButton.setOnClickListener {
            rxBus.send(EventScale(6, 1))
        }
        binding.graphsLayout.graph12hButton.setOnClickListener {
            rxBus.send(EventScale(12, 1))
        }
        binding.graphsLayout.graph18hButton.setOnClickListener {
            rxBus.send(EventScale(18, 1))
        }
        binding.graphsLayout.graph24hButton.setOnClickListener {
            rxBus.send(EventScale(24, 1))
        }




        /*
        binding.graphsLayout.graph6hButton.setOnClickListener { rxBus.send(EventScale(6)) }
        binding.graphsLayout.graph12hButton.setOnClickListener { rxBus.send(EventScale(12)) }
        binding.graphsLayout.graph18hButton.setOnClickListener { rxBus.send(EventScale(18)) }
        binding.graphsLayout.graph24hButton.setOnClickListener { rxBus.send(EventScale(24)) }
        */
// New graph buttons (Original Skin)
        binding.graphsLayout.graphTreatmentButton.setOnClickListener {
            // Open Statistics activity (TIR, TDD, Activity Monitor)
            startActivity(Intent(context, uiInteraction.treatmentsActivity))

        }




// New graph buttons (Original Skin)



        updateTimeRangeButtons(overviewData.rangeToDisplay)



        // binding.activeProfile.setOnClickListener(this) // Tarciso REMOVENDO O PROFILE da tela inicial
        // binding.activeProfile.setOnLongClickListener(this) //Tarciso REMOVENDO O PROFILE da tela inicial
        binding.infoLayout.tempTarget.setOnClickListener(this) // Tarciso Original -> binding.tempTarget.setOnClickListener(this)
        binding.infoLayout.tempTarget.setOnLongClickListener(this) // Tarciso Original -> binding.infoLayout.tempTarget.setOnLongClickListener(this)
        binding.buttonsLayout.acceptTempButton.setOnClickListener(this)
        binding.buttonsLayout.treatmentButton.setOnClickListener(this)
        binding.buttonsLayout.wizardButton.setOnClickListener(this)
        binding.buttonsLayout.calibrationButton.setOnClickListener(this)
        binding.buttonsLayout.cgmButton.setOnClickListener(this)
        // REMOVIDO: binding.buttonsLayout.insulinButton.setOnClickListener(this)
        binding.buttonsLayout.carbsButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnClickListener(this)
        binding.buttonsLayout.quickWizardButton.setOnLongClickListener(this)
        binding.infoLayout.apsMode.setOnClickListener(this)
        binding.infoLayout.apsMode.setOnLongClickListener(this)


        // binding.activeProfile.setOnLongClickListener(this) // Tarciso REMOVENDO O PROFILE da tela inicial





        // New side buttons (Original Skin)
        // Alterado pelo Tarciso - Updated button links per requirements
        binding.infoLayout.leftInsulinReservoirButton?.setOnClickListener(this)  // Now links to COMBOV2
        binding.infoLayout.leftCannulaButton?.setOnClickListener(this)
        binding.infoLayout.leftBatteryButton?.setOnClickListener(this)
        binding.infoLayout.rightSensorButton?.setOnClickListener(this)
        binding.infoLayout.rightSensorBatteryButton?.setOnClickListener(this)  // Now links to LOOP
        binding.infoLayout.rightConfButton?.setOnClickListener(this)



        // Alterado pelo Tarciso - Link to COMBOV2 plugin
        binding.infoLayout.leftInsulinReservoirButton.setOnClickListener {
            // Link to COMBOV2 menu
            startActivity(
                Intent(context, uiInteraction.singleFragmentActivity)
                    .putExtra("plugin", activePlugin.getPluginsList().indexOfFirst { it.javaClass.simpleName == "ComboV2Plugin" })
            )
        }

        /*
        binding.infoLayout.leftInsulinAge.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runFillDialog(childFragmentManager) }
                )
            }
        }*/

        binding.infoLayout.leftCannulaButton.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runFillDialog(childFragmentManager) }
                )
            }
        }

        binding.infoLayout.leftBatteryButton.setOnClickListener {
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.BATTERY_CHANGE, app.aaps.core.ui.R.string.careportal) }
                )
            }
        }
        binding.infoLayout.rightSensorButton.setOnClickListener {
            // This could link to sensor battery info or CGM sensor insert
            activity?.let { activity ->
                protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runCareDialog(childFragmentManager, UiInteraction.EventType.SENSOR_INSERT, app.aaps.core.ui.R.string.careportal_cgmsensorstart) }
                )
            }
        }

        // Alterado pelo Tarciso - Link to LOOP plugin (removed sensor insert link)
        binding.infoLayout.rightSensorBatteryButton.setOnClickListener {
            // Link to LOOP menu
            startActivity(
                Intent(context, uiInteraction.singleFragmentActivity)
                    .putExtra("plugin", activePlugin.getPluginsList().indexOfFirst { it.javaClass.simpleName == "LoopPlugin" })
            )
        }



        binding.infoLayout.rightConfButton.setOnClickListener {
            // Link to Configuration menu
            startActivity(
                Intent(context, uiInteraction.singleFragmentActivity)
                    .putExtra("plugin", activePlugin.getPluginsList().indexOfFirst { it.javaClass.simpleName == "ConfigBuilderPlugin" })
            )
        }



    }




    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewCalcProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateCalcProgress() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewIobCob::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateIobCob() }, fabricPrivacy::logException)
        /*
        // Tarciso Remover a Sensitivity da tela inicial
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewSensitivity::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateSensitivity() }, fabricPrivacy::logException)

         */
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewGraph::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGraph() }, fabricPrivacy::logException)
        disposable += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewNotification::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateNotification() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventScale::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           overviewData.rangeToDisplay = it.hours
                           // sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, it.hours)

                           // PERFORMANCE: Remover notificação global para evitar "pulo" ou reload pesado
                           // rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.utils.R.string.key_rangetodisplay)))
                           sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, true)

                           // Atualizar Botões
                           updateTimeRangeButtons(it.hours)

                           //val hours = event.hours
                           val reset_action = it.resethours
                           val newDuration = it.hours * 60 * 60 * 1000L
                           val newMaxX = nowAligned()
                           val newMinX = newMaxX - newDuration

                           // Atualizar Viewport Instantaneamente (Visual) usando Helper para garantir estabilidade do Eixo
                           //updateGraphViewportOnly(it.hours* 60 * 60 * 1000L)
                           if (reset_action > 0) {
                               updateGraphViewportOnly(newMinX, newMaxX, true, it.hours)
                           } else{
                               updateGraphViewportOnly(newMinX, newMaxX, false, 0)
                           }

                           return@subscribe






                           val graphData = app.aaps.plugins.main.general.overview.graphData.GraphData(injector, binding.graphsLayout.bgGraph, overviewData)
                           graphData.formatAxis(newMinX, newMaxX)

                           // Forçar recálculo de labels/steps (Correção: Eixo não atualiza)
                           binding.graphsLayout.bgGraph.onDataChanged(true, true)

                           androidx.core.view.ViewCompat.postInvalidateOnAnimation(binding.graphsLayout.bgGraph)

                           // Sincronizar Graphs Secundários
                           secondaryGraphs.forEach { graph ->
                               graph.viewport.setMinX(newMinX.toDouble())
                               graph.viewport.setMaxX(newMaxX.toDouble())
                               graph.viewport.setXAxisBoundsManual(true)
                               graph.postInvalidate()
                           }
                           secondaryGraphs.forEach { it.onDataChanged(true, true) }
                           currentTimeRangeHours = it.hours.toDouble()
                       }, fabricPrivacy::logException)
        /* REMOVIDO: EventPinch não é mais usado - o gesto de pinça envia EventScale diretamente
        disposable += rxBus
            .toObservable(EventPinch::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           val minHours = it.minHours
                           val maxHours = it.maxHours

                           // Calcular duração em milissegundos
                           val durationMillis = (maxHours - minHours) * 60 * 60 * 1000L

                           // Verificar limites
                           if (durationMillis <= 0) return@subscribe
                           if (durationMillis > 24 * 60 * 60 * 1000L) return@subscribe // Máximo 24 horas

                           val currentTime = nowAligned()
                           val newMinX = currentTime - (maxHours * 60 * 60 * 1000L) // Ajustar para tempo atual
                           val newMaxX = currentTime - (minHours * 60 * 60 * 1000L)

                           val hoursInt = (durationMillis / (60 * 60 * 1000L)).toInt()

                           overviewData.rangeToDisplay = hoursInt
                           sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, true)

                           // Atualizar Botões - usar a duração calculada
                           updateTimeRangeButtons(hoursInt)

                           // 🔥 CRÍTICO: Evitar recálculos pesados
                           suppressFullGraphUpdate = true

                           // Atualizar Viewport Instantaneamente
                           updateGraphViewportOnly(newMinX, newMaxX)

                           // 🔥 NÃO enviar EventPreferenceChange aqui - isso dispara recálculos
                           // sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, hoursInt)

                           // 🔥 Postar uma tarefa para liberar suppression depois que a UI atualizar
                           binding.graphsLayout.bgGraph.postDelayed({
                                                                        suppressFullGraphUpdate = false
                                                                    }, 300)

                       }, fabricPrivacy::logException)
        */






        disposable += rxBus
            .toObservable(EventBucketedDataCreated::class.java)
            .doOnNext { suppressFullGraphUpdate = false }
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateBg() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           if (it.now) refreshAll()
                           else scheduleUpdateGUI()
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAcceptOpenLoopChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventNewOpenLoopNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .delay(30, TimeUnit.MILLISECONDS, aapsSchedulers.main)
            .subscribe({
                           overviewData.pumpStatus = it.getStatus(requireContext())
                           updatePumpStatus()
                       }, fabricPrivacy::logException)
        /*
        disposable += rxBus
            .toObservable(EventInitializationChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ processButtonsVisibility() }, fabricPrivacy::logException)
            */

        disposable += rxBus
            .toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ scheduleUpdateGUI() }, fabricPrivacy::logException)
        /*disposable += rxBus
            .toObservable(EventScale::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           overviewData.rangeToDisplay = it.hours
                           updateTimeRangeButtons(it.hours)
                           sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, it.hours)
                           rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.utils.R.string.key_rangetodisplay)))
                           sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, true)
                       }, fabricPrivacy::logException) */
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateTemporaryTarget() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateExtendedBolus() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateTemporaryBasal() }, fabricPrivacy::logException)

        refreshLoop = Runnable {
            refreshAll()
            handler.postDelayed(refreshLoop, 60 * 1000L)
        }
        handler.postDelayed(refreshLoop, 60 * 1000L)

        handler.post { refreshAll() }
        updatePumpStatus()
        updateCalcProgress()
    }

    fun refreshAll() {
        if (!config.appInitialized) return
        runOnUiThread {
            _binding ?: return@runOnUiThread
            updateTime()
            // Tarciso remover o sensitivity da tela inicial
            // updateSensitivity()
            updateGraph()
            updateNotification()
        }
        updateBg()
        updateTemporaryBasal()
        updateExtendedBolus()
        updateIobCob()
        //processButtonsVisibility()
        processAps()

        // updateProfile() //Tarciso REMOVENDO O PROFILE da tela inicial
        updateTemporaryTarget()
        // updateTemporaryTarget()
        updateReservoirLevel()

        //updateGraphButtonsState()
    }

    // teste temporario para ver se remove o erro
    override fun onClick(v: View) {
        // try to fix  https://fabric.io/nightscout3/android/apps/info.nightscout.androidaps/issues/5aca7a1536c7b23527eb4be7?time=last-seven-days
        // https://stackoverflow.com/questions/14860239/checking-if-state-is-saved-before-committing-a-fragmenttransaction
        if (childFragmentManager.isStateSaved) return
        activity?.let { activity ->
            when (v.id) {
                // Alterado pelo Tarciso - Link to Treatments menu (TreatmentsActivity)
                R.id.treatment_button    -> {
                    // startActivity(Intent(context, app.aaps.ui.activities.TreatmentsActivity::class.java))
                    startActivity(
                        Intent(context, uiInteraction.singleFragmentActivity)
                            .putExtra("plugin", activePlugin.getPluginsList().indexOfFirst { it.javaClass.simpleName == "TreatmentsActivity" })
                    )



                    // startActivity(Intent(context, app.aaps.plugins.main..TreatmentsActivity::class.java))
                }

                R.id.wizard_button       -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runWizardDialog(childFragmentManager) })
                // Tarciso REMOVIDO:
                //    R.id.insulin_button      -> protectionCheck.queryProtection(
                //    activity,
                //    ProtectionCheck.Protection.BOLUS,
                //    UIRunnable { if (isAdded) uiInteraction.runInsulinDialog(childFragmentManager) })

                // R.id.quick_wizard_button -> protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable { if (isAdded) onClickQuickWizard() })
                R.id.carbs_button        -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runCarbsDialog(childFragmentManager) })

                R.id.temp_target         -> protectionCheck.queryProtection(
                    activity,
                    ProtectionCheck.Protection.BOLUS,
                    UIRunnable { if (isAdded) uiInteraction.runTempTargetDialog(childFragmentManager) })

                /* Tarciso REMOVENDO O PROFILE da tela inicial
                R.id.active_profile      -> {
                    uiInteraction.runProfileViewerDialog(
                        childFragmentManager,
                        dateUtil.now(),
                        UiInteraction.Mode.RUNNING_PROFILE
                    )
                } */

                /* R.id.cgm_button          -> {
                     if (xDripSource.isEnabled()) openCgmApp("com.eveningoutpost.dexdrip")
                     else if (dexcomBoyda.isEnabled()) dexcomBoyda.dexcomPackages().forEach { openCgmApp(it) }
                 }*/

                R.id.calibration_button  -> {
                    if (xDripSource.isEnabled()) {
                        uiInteraction.runCalibrationDialog(childFragmentManager)
                    }
                }

                R.id.accept_temp_button  -> {
                    profileFunction.getProfile() ?: return
                    if ((loop as PluginBase).isEnabled()) {
                        handler.post {
                            val lastRun = loop.lastRun
                            loop.invoke("Accept temp button", false)
                            if (lastRun?.lastAPSRun != null && lastRun.constraintsProcessed?.isChangeRequested == true) {
                                runOnUiThread {
                                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                                        if (isAdded)
                                            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.tempbasal_label), (lastRun.constraintsProcessed?.toString() ?: "").toSpanned()
                                                ?: "".toSpanned(), {
                                                                          uel.log(Action.ACCEPTS_TEMP_BASAL, Sources.Overview)
                                                                          (context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager?)?.cancel(Constants.notificationID)
                                                                          rxBus.send(EventMobileToWear(EventData.CancelNotification(dateUtil.now())))
                                                                          handler.post { loop.acceptChangeRequest() }
                                                                          binding.buttonsLayout.acceptTempButton.visibility = View.GONE
                                                                      })
                                    })
                                }
                            }
                        }
                    }
                }

                R.id.aps_mode            -> {
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        if (isAdded) uiInteraction.runLoopDialog(childFragmentManager, 1)
                    })
                }
            }
        }
    }


    override fun onLongClick(v: View): Boolean {
        when (v.id) {
            R.id.quick_wizard_button -> {
                startActivity(Intent(v.context, uiInteraction.quickWizardListActivity))
                return true
            }

            R.id.aps_mode            -> {
                activity?.let { activity ->
                    protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, UIRunnable {
                        uiInteraction.runLoopDialog(childFragmentManager, 0)
                    })
                }
            }


            // Tarciso REMOVENDO O PROFILE da tela inicial
            /*
            R.id.temp_target         -> v.performClick()
            R.id.active_profile      -> activity?.let { activity ->
                if (loop.isDisconnected) OKDialog.show(activity, rh.gs(R.string.not_available_full), rh.gs(R.string.smscommunicator_pump_disconnected))
                else
                    protectionCheck.queryProtection(
                        activity,
                        ProtectionCheck.Protection.BOLUS,
                        UIRunnable { uiInteraction.runProfileSwitchDialog(childFragmentManager) })
            }*/

        }
        return false
    }

    private fun updateXAxis(graph: GraphView, hours: Int) {
        val renderer = graph.gridLabelRenderer

        // renderer.isHumanRounding = false

        renderer.numHorizontalLabels = when {
            hours <= 2  -> 2
            hours <= 4  -> 3
            hours <= 6  -> 4
            hours <= 12 -> 6
            else        -> 7
        }

        // 🔥 FORÇA RECÁLCULO REAL DO EIXO
        renderer.reloadStyles()

        // Trick conhecido do GraphView
        graph.onDataChanged(true, true)
    }


    private fun applyStandardZoom(hours: Int) {
        overviewData.rangeToDisplay = hours
        sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, hours)
        sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusescale, true)

        // Reseta o eixo para o padrão
        binding.graphsLayout.bgGraph.gridLabelRenderer?.apply {
            numHorizontalLabels = hours + 1
            reloadStyles() // Recalcula as divisões
        }

        secondaryGraphs.forEach {
            it.gridLabelRenderer?.apply {
                numHorizontalLabels = hours + 1
                reloadStyles()
            }
        }

        // Atualiza Viewport
        val newDuration = hours * 60 * 60 * 1000L
        val newMinX = overviewData.toTime - newDuration
        val newMaxX = overviewData.toTime + 1

        binding.graphsLayout.bgGraph.viewport.apply {
            setMinX(newMinX.toDouble())
            setMaxX(newMaxX.toDouble())
            setXAxisBoundsManual(true)
        }

        secondaryGraphs.forEach { g ->
            g.viewport.apply {
                setMinX(newMinX.toDouble())
                setMaxX(newMaxX.toDouble())
                setXAxisBoundsManual(true)
            }
        }

        updateTimeRangeButtons(hours)
        currentTimeRangeHours = hours.toDouble()
    }











    private fun processAps() {
        val pump = activePlugin.activePump

        // aps mode
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        fun apsModeSetA11yLabel(stringRes: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                binding.infoLayout.apsMode.stateDescription = rh.gs(stringRes)
            }
        }

        runOnUiThread {
            _binding ?: return@runOnUiThread
            if (config.APS && pump.pumpDescription.isTempBasalCapable) {
                binding.infoLayout.apsMode.visibility = View.VISIBLE
                // Tarciso REMOVIDO: binding.infoLayout.timeLayout.visibility = View.GONE
                when {
                    (loop as PluginBase).isEnabled() && loop.isSuperBolus                       -> {
                        binding.infoLayout.apsMode.setImageResource(R.drawable.ic_loop_superbolus)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.superbolus)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    loop.isDisconnected                                                         -> {
                        binding.infoLayout.bgCircle.setImageResource(R.drawable.bg_circle_gray)
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_disconnected)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.disconnected)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    (loop as PluginBase).isEnabled() && loop.isSuspended                        -> {
                        binding.infoLayout.bgCircle.setImageResource(R.drawable.bg_circle_gray)
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_paused)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.suspendloop_label)
                        binding.infoLayout.apsModeText.text = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE
                    }

                    pump.isSuspended()                                                          -> {

                        binding.infoLayout.apsMode.setImageResource(
                            if (pump.model() == PumpType.OMNIPOD_EROS || pump.model() == PumpType.OMNIPOD_DASH) {
                                // For Omnipod, indicate the pump as disconnected when it's suspended.
                                // The only way to 'reconnect' it, is through the Omnipod tab
                                apsModeSetA11yLabel(app.aaps.core.ui.R.string.disconnected)
                                app.aaps.core.ui.R.drawable.ic_loop_disconnected
                            } else {
                                apsModeSetA11yLabel(app.aaps.core.ui.R.string.pump_paused)
                                app.aaps.core.ui.R.drawable.ic_loop_paused
                            }
                        )
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE // View.GONE
                    }

                    (loop as PluginBase).isEnabled() && closedLoopEnabled.value() && loop.isLGS -> {
                        binding.infoLayout.bgCircle.setImageResource(R.drawable.bg_circle_gray)
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_lgs)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.uel_lgs_loop_mode)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    (loop as PluginBase).isEnabled() && closedLoopEnabled.value()               -> {
                        binding.infoLayout.bgCircle.setImageResource(R.drawable.bg_circle_green)
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.main.R.drawable.ic_loop_closed)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.closedloop)
                        //binding.infoLayout.apsModeText.text = "Loop Closed"
                        "Loop".also { binding.infoLayout.apsModeText.text = it }
                        binding.infoLayout.apsModeText.visibility = View.VISIBLE

                    }

                    (loop as PluginBase).isEnabled() && !closedLoopEnabled.value()              -> {
                        binding.infoLayout.bgCircle.setImageResource(R.drawable.bg_circle_gray)
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_open)
                        apsModeSetA11yLabel(app.aaps.core.ui.R.string.openloop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }

                    else                                                                        -> {
                        binding.infoLayout.bgCircle.setImageResource(R.drawable.bg_circle_gray)
                        binding.infoLayout.apsMode.setImageResource(app.aaps.core.ui.R.drawable.ic_loop_disabled)
                        apsModeSetA11yLabel(R.string.disabled_loop)
                        binding.infoLayout.apsModeText.visibility = View.GONE
                    }
                }
            } else {
                //nsclient
                binding.infoLayout.apsMode.visibility = View.GONE
                binding.infoLayout.apsModeText.visibility = View.GONE
                // Tarciso REMOVIDO: binding.infoLayout.timeLayout.visibility = View.VISIBLE
            }

            // pump status from ns
            binding.pump.text = processedDeviceStatusData.pumpStatus(nsSettingsStatus)
            binding.pump.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.pump), processedDeviceStatusData.extendedPumpStatus) } }

            // OpenAPS status from ns
            binding.openaps.text = processedDeviceStatusData.openApsStatus
            binding.openaps.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(R.string.openaps), processedDeviceStatusData.extendedOpenApsStatus) } }

            // Uploader status from ns
            binding.uploader.text = processedDeviceStatusData.uploaderStatusSpanned
            binding.uploader.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(R.string.uploader), processedDeviceStatusData.extendedUploaderStatus) } }
        }
    }



















    /* private fun updateGraphButtonsState() {
         runOnUiThread {
             _binding ?: return@runOnUiThread

             val nightMode = rh.isNightMode
             val defaultColor = if (nightMode) android.graphics.Color.DKGRAY else android.graphics.Color.LTGRAY
             val selectedColor = if (nightMode) android.graphics.Color.BLUE else android.graphics.Color.parseColor("#2196F3")

             val defaultDrawable = createTintedDrawable(R.drawable.overview_pill_background, defaultColor)
             val selectedDrawable = createTintedDrawable(R.drawable.overview_pill_background, selectedColor)

             setButtonState(binding.graphsLayout.graph6hButton, 6)
             setButtonState(binding.graphsLayout.graph12hButton, 12)
             setButtonState(binding.graphsLayout.graph18hButton, 18)
             setButtonState(binding.graphsLayout.graph24hButton, 24)
         }
     }

     private fun createTintedDrawable(drawableRes: Int, color: Int): Drawable? {
         val context = requireContext() // ou binding.root.context ou activity ?: return null
         val drawable = ContextCompat.getDrawable(context, drawableRes)?.mutate() ?: return null
         drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
         return drawable
     }

     private fun setButtonState(btn: View?, hours: Int) {
         btn ?: return
         val isSelected = overviewData.rangeToDisplay == hours

         val nightMode = rh.isNightMode
         val defaultColor = if (nightMode) android.graphics.Color.DKGRAY else android.graphics.Color.LTGRAY
         val selectedColor = if (nightMode) android.graphics.Color.BLUE else android.graphics.Color.parseColor("#2196F3")

         val defaultDrawable = createTintedDrawable(R.drawable.overview_pill_background, defaultColor)
         val selectedDrawable = createTintedDrawable(R.drawable.overview_pill_background, selectedColor)

         btn.isSelected = isSelected
         btn.background = if (isSelected) selectedDrawable else defaultDrawable
     } */

    private fun prepareGraphsIfNeeded(numOfGraphs: Int) {
        if (numOfGraphs != secondaryGraphs.size - 1) {
            //aapsLogger.debug("New secondary graph count ${numOfGraphs-1}")
            // rebuild needed
            secondaryGraphs.clear()
            secondaryGraphsLabel.clear()
            binding.graphsLayout.iobGraph.removeAllViews()
            for (i in 1 until numOfGraphs) {
                val relativeLayout = RelativeLayout(context)
                relativeLayout.layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

                val graph = GraphView(context)
                graph.layoutParams =
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, rh.dpToPx(skinProvider.activeSkin().secondaryGraphHeight)).also { it.setMargins(0, rh.dpToPx(15), 0, rh.dpToPx(2)) }

                // Configurar grid tracejado para gráficos secundários (IOB, COB, etc.)
                graph.gridLabelRenderer?.apply {
                    gridColor = rh.gac(context, app.aaps.core.ui.R.attr.graphGrid)
                    gridStyle = com.jjoe64.graphview.GridLabelRenderer.GridStyle.BOTH
                    isHighlightZeroLines = false
                    reloadStyles()
                    isHorizontalLabelsVisible = false
                    labelVerticalWidth = axisWidth
                    numVerticalLabels = 3
                }

                graph.viewport.backgroundColor = rh.gac(context, app.aaps.core.ui.R.attr.viewPortBackgroundColor)
                relativeLayout.addView(graph)

                val label = TextView(context)
                val layoutParams = RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.setMargins(rh.dpToPx(30), rh.dpToPx(25), 0, 0) }
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                layoutParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
                label.layoutParams = layoutParams
                relativeLayout.addView(label)
                secondaryGraphsLabel.add(label)

                binding.graphsLayout.iobGraph.addView(relativeLayout)
                secondaryGraphs.add(graph)
            }
        }
    }

    var task: Runnable? = null

    private fun scheduleUpdateGUI() {
        class UpdateRunnable : Runnable {

            override fun run() {
                refreshAll()
                task = null
            }
        }
        task?.let { handler.removeCallbacks(it) }
        task = UpdateRunnable()
        task?.let { handler.postDelayed(it, 500) }
    }

    @SuppressLint("SetTextI18n")
    fun updateBg() {
        val lastBg = overviewData.lastBg(iobCobCalculator.ads)
        val lastBgColor = overviewData.lastBgColor(context, iobCobCalculator.ads)
        val isActualBg = overviewData.isActualBg(iobCobCalculator.ads)
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val trendDescription = trendCalculator.getTrendDescription(iobCobCalculator.ads)
        val trendArrow = trendCalculator.getTrendArrow(iobCobCalculator.ads)
        val lastBgDescription = overviewData.lastBgDescription(iobCobCalculator.ads)
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.bg.text = profileUtil.fromMgdlToStringInUnits(lastBg?.recalculated)
            binding.infoLayout.bg.setTextColor(lastBgColor)
            trendArrow?.let { binding.infoLayout.arrow.setImageResource(it.directionToIcon()) }
            binding.infoLayout.arrow.visibility = (trendArrow != null).toVisibilityKeepSpace()
            binding.infoLayout.arrow.setColorFilter(lastBgColor)
            binding.infoLayout.arrow.contentDescription = lastBgDescription + " " + rh.gs(app.aaps.core.ui.R.string.and) + " " + trendDescription



            /*
            binding.infoLayout2.bg.text = profileUtil.fromMgdlToStringInUnits(lastBg?.recalculated)
            binding.infoLayout2.bg.setTextColor(lastBgColor)
            trendArrow?.let { binding.infoLayout2.arrow.setImageResource(it.directionToIcon()) }
            binding.infoLayout2.arrow.visibility = (trendArrow != null).toVisibilityKeepSpace()
            binding.infoLayout2.arrow.setColorFilter(lastBgColor)
            binding.infoLayout2.arrow.contentDescription = lastBgDescription + " " + rh.gs(app.aaps.core.ui.R.string.and) + " " + trendDescription
            */

            if (glucoseStatus != null) {
                // Tarciso Removendo DElta
                //binding.infoLayout.deltaLarge.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                //binding.infoLayout.deltaLarge.setTextColor(lastBgColor)
                binding.infoLayout.delta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)

                //binding.infoLayout2.delta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.delta)
                //binding.infoLayout.avgDelta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.shortAvgDelta)
                //binding.infoLayout.longAvgDelta.text = profileUtil.fromMgdlToSignedStringInUnits(glucoseStatus.longAvgDelta)
            } else {
                // Tarciso Removendo DElta
                //binding.infoLayout.deltaLarge.text = ""
                binding.infoLayout.delta.text = "" + rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

                //binding.infoLayout2.delta.text = "" + rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

                //binding.infoLayout.avgDelta.text = ""
                //binding.infoLayout.longAvgDelta.text = ""
            }

            // strike through if BG is old
            binding.infoLayout.bg.paintFlags =
                if (!isActualBg) binding.infoLayout.bg.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                else binding.infoLayout.bg.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()

            val outDate = (if (!isActualBg) rh.gs(R.string.a11y_bg_outdated) else "")
            binding.infoLayout.bg.contentDescription = rh.gs(R.string.a11y_blood_glucose) + " " + binding.infoLayout.bg.text.toString() + " " + lastBgDescription + " " + outDate

            binding.infoLayout.timeAgo.text = dateUtil.minAgo(rh, lastBg?.timestamp)
            binding.infoLayout.timeAgo.contentDescription = dateUtil.minAgoLong(rh, lastBg?.timestamp)
            binding.infoLayout.timeAgoShort.text = "(" + dateUtil.minAgoShort(lastBg?.timestamp) + ")"


            //binding.infoLayout2.timeAgo.text = dateUtil.minAgo(rh, lastBg?.timestamp)
            //binding.infoLayout2.timeAgo.contentDescription = dateUtil.minAgoLong(rh, lastBg?.timestamp)




            //Tarciso REmovendo o ICONE do TRiandgulo Amarelo. Cordigo Original abaixo:
            // val qualityIcon = bgQualityCheck.icon()
            val qualityIcon = 0
            if (qualityIcon != 0) {
                binding.infoLayout.bgQuality.visibility = View.VISIBLE
                binding.infoLayout.bgQuality.setImageResource(qualityIcon)
                binding.infoLayout.bgQuality.contentDescription = rh.gs(R.string.a11y_bg_quality) + " " + bgQualityCheck.stateDescription()
                binding.infoLayout.bgQuality.setOnClickListener {
                    context?.let { context -> OKDialog.show(context, rh.gs(R.string.data_status), bgQualityCheck.message) }
                }
            } else {
                binding.infoLayout.bgQuality.visibility = View.GONE
            }
        }
    }

    /* Tarciso REMOVENDO O PROFILE da tela inicial
    private fun updateProfile() {
    val profile = profileFunction.getProfile()
    runOnUiThread {
        _binding ?: return@runOnUiThread
        val profileBackgroundColor = profile?.let {
            if (it is ProfileSealed.EPS) {
                if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                    app.aaps.core.ui.R.attr.ribbonWarningColor
                else app.aaps.core.ui.R.attr.ribbonDefaultColor
            } else app.aaps.core.ui.R.attr.ribbonDefaultColor
        } ?: app.aaps.core.ui.R.attr.ribbonCriticalColor

        val profileTextColor = profile?.let {
            if (it is ProfileSealed.EPS) {
                if (it.value.originalPercentage != 100 || it.value.originalTimeshift != 0L || it.value.originalDuration != 0L)
                    app.aaps.core.ui.R.attr.ribbonTextWarningColor
                else app.aaps.core.ui.R.attr.ribbonTextDefaultColor
            } else app.aaps.core.ui.R.attr.ribbonTextDefaultColor
        } ?: app.aaps.core.ui.R.attr.ribbonTextDefaultColor
        setRibbon(binding.activeProfile, profileTextColor, profileBackgroundColor, profileFunction.getProfileNameWithRemainingTime())
    }
    }*/

    private fun updateTemporaryBasal() {
        val temporaryBasalText = overviewData.temporaryBasalText(iobCobCalculator)
// val temporaryBasalColor = overviewData.temporaryBasalColor(context, iobCobCalculator)
        val temporaryBasalIcon = overviewData.temporaryBasalIcon(iobCobCalculator)
        val temporaryBasalDialogText = overviewData.temporaryBasalDialogText(iobCobCalculator)
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.baseBasal.text = temporaryBasalText
            // binding.infoLayout.baseBasal.setTextColor(temporaryBasalColor)
            binding.infoLayout.baseBasal
            binding.infoLayout.baseBasalIcon.setImageResource(temporaryBasalIcon)
            binding.infoLayout.basalLayout.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.basal), temporaryBasalDialogText) } }
        }
    }

    private fun updateExtendedBolus() {
        val pump = activePlugin.activePump
        val extendedBolus = iobCobCalculator.getExtendedBolus(dateUtil.now())
        val extendedBolusText = overviewData.extendedBolusText(iobCobCalculator)
        val extendedBolusDialogText = overviewData.extendedBolusDialogText(iobCobCalculator)
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.extendedBolus.text = extendedBolusText
            binding.infoLayout.extendedLayout.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.extended_bolus), extendedBolusDialogText) } }
            binding.infoLayout.extendedLayout.visibility = (extendedBolus != null && !pump.isFakingTempsByExtendedBoluses).toVisibility()
        }
    }

    private fun updateReservoirLevel() {
        val pump = activePlugin.activePump
        val reservoirLevel = pump.reservoirLevel
        val lastConnection = (pump.lastDataTime())/ 1000 / 60
        val levelPercentage = ((reservoirLevel/315) * 100).toInt()


        //binding.infoLayout.reservoir_icon.setImageResource(iconRes)



        runOnUiThread {
            _binding ?: return@runOnUiThread

            val iconRes = when (levelPercentage) {
                in 76..100 -> app.aaps.core.main.R.drawable.ic_reservoir_100
                in 51..75 -> app.aaps.core.main.R.drawable.ic_reservoir_75
                in 26..50 -> app.aaps.core.main.R.drawable.ic_reservoir_50
                in 11..25 -> app.aaps.core.main.R.drawable.ic_reservoir_25
                else -> app.aaps.core.main.R.drawable.ic_reservoir_10
            }
            binding.infoLayout.reservoirIcon.setImageResource(iconRes)


            // Use o ID correto do TextView
            binding.infoLayout.reservoirLevel.text = levelPercentage.toString() + "%"
            // E o ID correto do ProgressBar
            // binding.statusLightsLayout.reservoir_level_bar.progress = levelPercentage
        }
    }










    private fun updateTime() {
        _binding ?: return
        binding.infoLayout.time.text = dateUtil.timeString(dateUtil.now())
// Status lights
        val pump = activePlugin.activePump
        val isPatchPump = pump.pumpDescription.isPatchPump
        binding.statusLightsLayout.apply {
            cannulaOrPatch.setImageResource(if (isPatchPump) app.aaps.core.main.R.drawable.ic_patch_pump_outline else R.drawable.ic_cp_age_cannula)
            cannulaOrPatch.contentDescription = rh.gs(if (isPatchPump) R.string.statuslights_patch_pump_age else R.string.statuslights_cannula_age)
            insulinAge.visibility = isPatchPump.not().toVisibility()
            batteryLayout.visibility = (!isPatchPump || pump.pumpDescription.useHardwareLink).toVisibility()
            pbAge.visibility = (pump.pumpDescription.isBatteryReplaceable || pump.isBatteryChangeLoggingEnabled()).toVisibility()
            val useBatteryLevel = (pump.model() == PumpType.OMNIPOD_EROS)
                || (pump.model() != PumpType.ACCU_CHEK_COMBO && pump.model() != PumpType.OMNIPOD_DASH)
            pbLevel.visibility = useBatteryLevel.toVisibility()
            statusLightsLayout.visibility = (sp.getBoolean(R.string.key_show_statuslights, true) || config.NSCLIENT).toVisibility()
        }
// This code change the color of statuslight variables (Low resolution screen)
        statusLightHandler.updateStatusLights(
            binding.statusLightsLayout.cannulaAge,
            null,
            binding.statusLightsLayout.insulinAge,
            binding.statusLightsLayout.reservoirLevel,
            binding.statusLightsLayout.sensorAge,
            binding.infoLayout.rightSensorBattery,
            binding.statusLightsLayout.pbAge,
            binding.statusLightsLayout.pbLevel
        )

// This code change the color of statuslight variables (Original screen)
        statusLightHandler.updateStatusLights(
            binding.infoLayout.leftCannulaAge,
            null,
            binding.infoLayout.leftInsulinAge,
            binding.infoLayout.leftInsulinReservoir,
            binding.infoLayout.rightSensorAge,
            binding.infoLayout.rightSensorBattery,
            binding.infoLayout.leftBatteryLevel,
            null,
        )

// Update side buttons in Original Skin with the same data
        /*binding.infoLayout.leftInsulinAge?.text = binding.statusLightsLayout.insulinAge.text.toString() // + " " + binding.statusLightsLayout.reservoirLevel.text.toString()
        binding.infoLayout.leftInsulinReservoir?.text = binding.statusLightsLayout.reservoirLevel.text.toString()
        binding.infoLayout.leftCannulaAge?.text = binding.statusLightsLayout.cannulaAge.text
        binding.infoLayout.leftBatteryLevel?.text = binding.statusLightsLayout.pbAge.text.toString() // + " " + binding.statusLightsLayout.pbLevel.text.toString()
        binding.infoLayout.rightSensorAge?.text = binding.statusLightsLayout.sensorAge.text
        //binding.infoLayout.rightSensorBattery?.text = binding.statusLightsLayout.sensorAge.text // Sensor battery uses same as sensor age for now
        */
    }

    private fun updateIobCob() {
        val iobText = overviewData.iobText(iobCobCalculator)
// val iobDialogText = overviewData.iobDialogText(iobCobCalculator)
        val displayText = overviewData.cobInfo(iobCobCalculator).displayText(rh, decimalFormatter)
        val lastCarbsTime = overviewData.lastCarbsTime
        runOnUiThread {
            _binding ?: return@runOnUiThread
            binding.infoLayout.iob.text = iobText
            //binding.infoLayout2.iob.text = iobText
            // Tarciso REMOVIDO: O click listener para IOB foi movido para o onViewCreated
            // binding.infoLayout.iobLayout.setOnClickListener { activity?.let { OKDialog.show(it, rh.gs(app.aaps.core.ui.R.string.iob), iobDialogText) } }

            // cob
            var cobText = displayText ?: rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)

            val constraintsProcessed = loop.lastRun?.constraintsProcessed
            val lastRun = loop.lastRun
            if (config.APS && constraintsProcessed != null && lastRun != null) {
                if (constraintsProcessed.carbsReq > 0) {
                    //only display carbsreq when carbs have not been entered recently
                    if (lastCarbsTime < lastRun.lastAPSRun) {
                        cobText += "\n" + constraintsProcessed.carbsReq + " " + rh.gs(app.aaps.core.ui.R.string.required)
                    }
                    if (carbAnimation?.isRunning == false)
                        carbAnimation?.start()
                } else {
                    carbAnimation?.stop()
                    carbAnimation?.selectDrawable(0)
                }
            }
            //Tarciso. Removendo o texto do alarme de Carbo necessario o valor proximo do icone de trigo
            //binding.infoLayout.cob.text = cobText
        }
    }
    // INICIO DOS TESTES DE TEMP TARGET
    @SuppressLint("SetTextI18n")
    fun updateTemporaryTarget() {
        val units = profileFunction.getUnits()
        val tempTarget = overviewData.temporaryTarget
        val targetProtection = loop.lastRun?.constraintsProcessed?.targetProtection ?: 0.0
        val targetScreen = loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0

        runOnUiThread {
            _binding ?: return@runOnUiThread

            // Configurar ícone
            if (targetProtection > 0.0 && targetProtection < targetScreen) {
                binding.infoLayout.tempTargetIcon.setImageResource(app.aaps.core.main.R.drawable.ic_shield)
            } else {
                binding.infoLayout.tempTargetIcon.setImageResource(app.aaps.core.main.R.drawable.ic_target_with_arrow)
            }

            // === CRÍTICO: SEMPRE restaurar o drawable original primeiro ===
            binding.infoLayout.tempTarget.setBackgroundResource(R.drawable.overview_pill_temptarget)

            if (tempTarget != null) {
                // === TEMP TARGET ATIVADO ===
                // 1. Criar NOVA instância do drawable (não usar mutate na instância atual)
                val pillDrawable = ContextCompat.getDrawable(requireContext(), R.drawable.overview_pill_temptarget)?.mutate()

                // 2. Aplicar cor amarela
                val yellowColor = rh.gac(requireContext(), app.aaps.core.ui.R.attr.ribbonWarningColor)
                pillDrawable?.colorFilter = PorterDuffColorFilter(yellowColor, PorterDuff.Mode.SRC_IN)

                // 3. Aplicar ao TextView
                binding.infoLayout.tempTarget.background = pillDrawable

                // 4. Configurar texto
                binding.infoLayout.tempTarget.text = profileUtil.toTargetRangeString(
                    tempTarget.lowTarget,
                    tempTarget.highTarget,
                    GlucoseUnit.MGDL,
                    units
                ) + " " + dateUtil.untilString(tempTarget.end, rh)

                // 5. Cor do texto
                if (rh.isNightMode) {
                    binding.infoLayout.tempTarget.setTextColor(app.aaps.core.ui.R.attr.ribbonTextWarningColor)
                } else {
                    binding.infoLayout.tempTarget.setTextColor(app.aaps.core.ui.R.attr.ribbonTextWarningColor)

                }

            } else {
                // === TEMP TARGET DESATIVADO ===
                // REMOVER QUALQUER COLORFILTER/TINT ANTERIOR
                binding.infoLayout.tempTarget.background?.clearColorFilter()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    binding.infoLayout.tempTarget.backgroundTintList = null
                }

                // Restaurar drawable original do XML
                binding.infoLayout.tempTarget.setBackgroundResource(R.drawable.overview_pill_temptarget)

                // Lógica existente para outros casos
                profileFunction.getProfile()?.let { profile ->
                    val targetUsed = loop.lastRun?.constraintsProcessed?.targetBG ?: 0.0

                    if (targetUsed != 0.0 && abs(profile.getTargetMgdl() - targetUsed) > 0.01) {
                        aapsLogger.debug("Adjusted target. Profile: ${profile.getTargetMgdl()} APS: $targetUsed")

                        // IMPORTANTE: A função setRibbon deve usar o drawable de pilula
                        setRibbonWithPillBackground(
                            binding.infoLayout.tempTarget,
                            app.aaps.core.ui.R.attr.ribbonTextDefaultColor2,
                            app.aaps.core.ui.R.attr.ribbonDefaultColor2,
                            profileUtil.toTargetRangeString(targetUsed, targetUsed, GlucoseUnit.MGDL, units),
                            R.drawable.overview_pill_temptarget // Passar o drawable de pilula
                        )
                    } else {
                        if (targetScreen != 0.0) {
                            setRibbonWithPillBackground(
                                binding.infoLayout.tempTarget,
                                app.aaps.core.ui.R.attr.ribbonTextDefaultColor2,
                                app.aaps.core.ui.R.attr.ribbonDefaultColor2,
                                targetScreen.toString(),
                                R.drawable.overview_pill_temptarget
                            )
                        } else {
                            setRibbonWithPillBackground(
                                binding.infoLayout.tempTarget,
                                app.aaps.core.ui.R.attr.ribbonTextDefaultColor2,
                                app.aaps.core.ui.R.attr.ribbonDefaultColor2,
                                profileUtil.toTargetRangeString(profile.getTargetLowMgdl(), profile.getTargetHighMgdl(), GlucoseUnit.MGDL, units),
                                R.drawable.overview_pill_temptarget
                            )
                        }
                    }
                }
            }
        }
    }
    private fun setRibbonWithPillBackground(
        textView: TextView,
        textColorAttr: Int,
        backgroundColorAttr: Int,
        text: String,
        pillDrawableRes: Int = R.drawable.overview_pill_temptarget
    ) {
        // 1. Sempre usar o drawable de pilula
        textView.setBackgroundResource(pillDrawableRes)

        // 2. Aplicar cor de fundo mantendo o shape
        val backgroundColor = rh.gac(textView.context, backgroundColorAttr)
        val drawable = textView.background.mutate()
        drawable.colorFilter = PorterDuffColorFilter(backgroundColor, PorterDuff.Mode.SRC_IN)

        // 3. Configurar texto
        textView.text = text

        // 4. Cor do texto
        textView.setTextColor(rh.gac(textView.context, textColorAttr))
    }
// END TEMP TARGET


    private fun setRibbon(view: TextView, attrResText: Int, attrResBack: Int, text: String) {
        with(view) {
            setText(text)

            // Cor do texto
            setTextColor(rh.gac(context, attrResText))

            // 🔥 1) Cor do fundo (atrás da pílula)
            setBackgroundColor(rh.gac(context, attrResBack))

            // 🔥 2) Tint da pílula (preserva o formato)
            background?.mutate()?.setTint(rh.gac(context, attrResBack))

            // Tint do ícone
            compoundDrawables[0]?.mutate()?.setTint(rh.gac(context, attrResText))
        }
    }

    /*
    private fun setRibbon(view: TextView, attrResText: Int, attrResBack: Int, text: String) {
    with(view) {
        setText(text)
        // setBackgroundColor(rh.gac(context, attrResBack))
        setTextColor(rh.gac(context, attrResText))
        compoundDrawables[0]?.setTint(rh.gac(context, attrResText))
    }
    }
    */








    private fun updateGraph() {
        if (suppressFullGraphUpdate) return
        _binding ?: return
        val pump = activePlugin.activePump
        val graphData = GraphData(injector, binding.graphsLayout.bgGraph, overviewData)
        graphData.reset()
        val menuChartSettings = overviewMenus.setting
        if (menuChartSettings.isEmpty()) return
        // 6 * 60 * 60 * 1000L
        // graphData.addInRangeArea(overviewData.fromTime, nowAligned(), defaultValueHelper.determineLowLine(), defaultValueHelper.determineHighLine())
        graphData.addInRangeArea((6 * 60 * 60 * 1000L), nowAligned(), defaultValueHelper.determineLowLine(), defaultValueHelper.determineHighLine())
        graphData.addBgReadings(menuChartSettings[0][OverviewMenus.CharType.PRE.ordinal], context)
        graphData.addBucketedData()
        graphData.addTreatments(context)
        graphData.addEps(context, 0.95)
        if (menuChartSettings[0][OverviewMenus.CharType.TREAT.ordinal])
            graphData.addTherapyEvents()
        if (menuChartSettings[0][OverviewMenus.CharType.ACT.ordinal])
            graphData.addActivity(0.8)
        if ((pump.pumpDescription.isTempBasalCapable || config.NSCLIENT) && menuChartSettings[0][OverviewMenus.CharType.BAS.ordinal])
            graphData.addBasals()
        graphData.addTargetLine()
        graphData.addNowLine(dateUtil.now())

        // set manual x bounds to have nice steps
        graphData.setNumVerticalLabels()
        // Tarciso: Use rangeToDisplay for the visible viewport, even if we loaded more data

        // val displayStartTime = nowAligned() - (overviewData.rangeToDisplay * 60 * 60 * 1000L)
        val displayStartTime =  if (overviewData.rangeToDisplay > 23) {
            nowAligned() - ((overviewData.rangeToDisplay - 2) * 60 * 60 * 1000L)
        } else {
            nowAligned() - (overviewData.rangeToDisplay * 60 * 60 * 1000L)
        }
        graphData.formatAxis(displayStartTime, nowAligned())

        graphData.performUpdate()

// 2nd graphs
        prepareGraphsIfNeeded(menuChartSettings.size)
        val secondaryGraphsData: ArrayList<GraphData> = ArrayList()

        val now = System.currentTimeMillis()
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
            val secondGraphData = GraphData(injector, secondaryGraphs[g], overviewData)
            // tarciso add line secondGraphData.reset()
            secondGraphData.reset()
            var useABSForScale = false
            var useIobForScale = false
            var useCobForScale = false
            var useDevForScale = false
            var useRatioForScale = false
            var useDSForScale = false
            var useBGIForScale = false
            var useHRForScale = false
            var useSTEPSForScale = false
            when {
                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]      -> useABSForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]      -> useIobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]      -> useCobForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]      -> useDevForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]      -> useBGIForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]      -> useRatioForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] -> useDSForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal]       -> useHRForScale = true
                menuChartSettings[g + 1][OverviewMenus.CharType.STEPS.ordinal]    -> useSTEPSForScale = true
            }
            val alignDevBgiScale = menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] && menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]

            if (menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal]) secondGraphData.addAbsIob(useABSForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal]) secondGraphData.addIob(useIobForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal]) secondGraphData.addCob(useCobForScale, if (useCobForScale) 1.0 else 0.5)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal]) secondGraphData.addDeviations(useDevForScale, 1.0)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal]) secondGraphData.addMinusBGI(useBGIForScale, if (alignDevBgiScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal]) secondGraphData.addRatio(useRatioForScale, if (useRatioForScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] && config.isDev()) secondGraphData.addDeviationSlope(
                useDSForScale,
                if (useDSForScale) 1.0 else 0.8,
                useRatioForScale
            )
            if (menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal]) secondGraphData.addHeartRate(useHRForScale, if (useHRForScale) 1.0 else 0.8)
            if (menuChartSettings[g + 1][OverviewMenus.CharType.STEPS.ordinal]) secondGraphData.addSteps(useSTEPSForScale, if (useSTEPSForScale) 1.0 else 0.8)

            // set manual x bounds to have nice steps
             secondGraphData.formatAxis(displayStartTime, nowAligned())
            //tarciso added
            secondGraphData.addNowLine(dateUtil.now())
            secondaryGraphsData.add(secondGraphData)
        }
        for (g in 0 until min(secondaryGraphs.size, menuChartSettings.size + 1)) {
            secondaryGraphsLabel[g].text = overviewMenus.enabledTypes(g + 1)
            secondaryGraphs[g].visibility = (
                menuChartSettings[g + 1][OverviewMenus.CharType.ABS.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.IOB.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.COB.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.DEV.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.BGI.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.SEN.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.DEVSLOPE.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.HR.ordinal] ||
                    menuChartSettings[g + 1][OverviewMenus.CharType.STEPS.ordinal]
                ).toVisibility()
            secondaryGraphsData[g].performUpdate()
        }
        // updateTimeRangeButtons(it.hours)
    }


    private fun nowAligned(): Long {
        return System.currentTimeMillis()
    }




    // ⚡ Atualização LEVE: apenas viewport (sem recálculo de dados)
    // 🔥 Mudar a assinatura da função para receber apenas o necessário
    private fun updateGraphViewportOnly(startTimeMillis: Long, endTimeMillis: Long, resetTime: Boolean = false, durationHours: Int = 0) {
        suppressFullGraphUpdate = true
        val now = nowAligned()
        val buffer = 1 * 60 * 60 * 1000L

        var durationMillis = endTimeMillis - startTimeMillis
        var hoursInt = (durationMillis.toDouble() / (60 * 60 * 1000)).roundToInt()


        /*if (resetTime && durationHours > 0) {
            durationMillis = durationHours.toLong() * 60 * 60 * 1000L
            hoursInt = durationHours
        }*/
        var minX: Long = 0
        var maxX: Long = 0


        if (resetTime && durationHours > 0) {

            durationMillis = durationHours.toLong() * 60 * 60 * 1000L
            hoursInt = durationHours

            // Reset do gráfico
            val graphData = GraphData(injector, binding.graphsLayout.bgGraph, overviewData)
            graphData.reset()
            binding.graphsLayout.bgGraph.onDataChanged(true, true)
            binding.graphsLayout.bgGraph.invalidate()

            maxX = now + buffer
            minX = if (hoursInt == 24) {
                (now - durationMillis) + (2 * 60 * 60 * 1000L)
            } else {
                now - durationMillis
            }
        } else {
            maxX = endTimeMillis + buffer
            minX = if (hoursInt == 24) {
                startTimeMillis + (2 * 60 * 60 * 1000L)
            } else {
                startTimeMillis
            }
        }

        overviewData.rangeToDisplay = hoursInt
        aapsLogger.debug("🔄 updateGraphViewportOnly: hours=$hoursInt, reset=$resetTime, minX=${dateUtil.timeString(minX)}, maxX=${dateUtil.timeString(maxX)}")

        // 🔥 FUNÇÃO para configurar viewport
        fun configureGraphViewport(graph: GraphView, minX: Long, maxX: Long, hoursInt: Int) {
            // 1. Configurar viewport
            graph.viewport.setXAxisBoundsManual(true)
            graph.viewport.setMinX(minX.toDouble())
            graph.viewport.setMaxX(maxX.toDouble())

            // 2. Configurar número de labels do eixo X
            val renderer = graph.gridLabelRenderer
            renderer?.apply {
                numHorizontalLabels = when {
                    hoursInt <= 2 -> 2
                    hoursInt <= 4 -> 3
                    hoursInt <= 6 -> 4
                    hoursInt <= 12 -> 6
                    else -> 7
                }
                // graph.removeSeries()
                // graph.series.clear()
                reloadStyles()
            }

            // 3. Forçar atualização
            graph.onDataChanged(false, false)
            graph.invalidate()

        }

        // 🔥 4. Configurar todos os gráficos
        configureGraphViewport(binding.graphsLayout.bgGraph, minX, maxX, hoursInt)
        secondaryGraphs.forEach { graph ->
            configureGraphViewport(graph, minX, maxX, hoursInt)
        }

        // 🔥 5. Atualizar botões e salvar
        updateTimeRangeButtons(hoursInt)
        sp.putInt(app.aaps.core.utils.R.string.key_rangetodisplay, hoursInt)

        // 🔥 6. Liberar suppression
        binding.graphsLayout.bgGraph.postDelayed({
                                                     suppressFullGraphUpdate = false
                                                     aapsLogger.debug("✅ updateGraphViewportOnly concluído para $hoursInt horas")
                                                 }, 300)
    }



    private fun updateCalcProgress() {
        _binding ?: return
        binding.progressBar.visibility = (overviewData.calcProgressPct != 100).toVisibility()
        binding.progressBar.progress = overviewData.calcProgressPct
    }
// Tarciso Para remover o Sensitivity da tela inicial
    /*
    private fun updateSensitivity() {
    _binding ?: return
    val lastAutosensData = overviewData.lastAutosensData(iobCobCalculator)
    if (config.NSCLIENT && sp.getBoolean(app.aaps.core.utils.R.string.key_used_autosens_on_main_phone, false) ||
    !config.NSCLIENT && constraintChecker.isAutosensModeEnabled().value()
    ) {
    binding.infoLayout.sensitivityIcon.setImageResource(app.aaps.core.main.R.drawable.ic_swap_vert_black_48dp_green)
    } else {
    binding.infoLayout.sensitivityIcon.setImageResource(app.aaps.core.main.R.drawable.ic_x_swap_vert)
    }

    binding.infoLayout.sensitivity.text =
    lastAutosensData?.let {
        String.format(Locale.ENGLISH, "%.0f%%", it.autosensResult.ratio * 100)
    } ?: ""
    // Show variable sensitivity
    val profile = profileFunction.getProfile()
    val request = loop.lastRun?.request
    val isfMgdl = profile?.getIsfMgdl()
    val variableSens =
    if (config.APS && request is VariableSensitivityResult) request.variableSens ?: 0.0
    else if (config.NSCLIENT) JsonHelper.safeGetDouble(processedDeviceStatusData.getAPSResult(injector).json, "variable_sens")
    else 0.0

    if (variableSens != isfMgdl && variableSens != 0.0 && isfMgdl != null) {
    binding.infoLayout.variableSensitivity.text =
        String.format(
            Locale.getDefault(), "%1$.1f→%2$.1f",
            profileUtil.fromMgdlToUnits(isfMgdl, profileFunction.getUnits()),
            profileUtil.fromMgdlToUnits(variableSens, profileFunction.getUnits())
        )
    binding.infoLayout.variableSensitivity.visibility = View.VISIBLE
    } else binding.infoLayout.variableSensitivity.visibility = View.GONE
    }
    */

    private fun updateTimeRangeButtons(hours: Int) {
        _binding?.let { binding ->
            binding.graphsLayout.graph6hButton.isSelected = (hours == 6)
            binding.graphsLayout.graph12hButton.isSelected = (hours == 12)
            binding.graphsLayout.graph18hButton.isSelected = (hours == 18)
            binding.graphsLayout.graph24hButton.isSelected = (hours == 24)
        }

    }




    private fun updatePumpStatus() {
        _binding ?: return
        val status = overviewData.pumpStatus

        binding.pumpStatus.text = status
        binding.pumpStatusLayout.visibility = (status != "").toVisibility()
// binding.infoLayout.reservoirTxt.
    }

    private fun updateNotification() {
        _binding ?: return
        binding.notifications.let { notificationStore.updateNotifications(it) }
    }
}
