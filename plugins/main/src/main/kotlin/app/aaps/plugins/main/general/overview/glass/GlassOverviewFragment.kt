package app.aaps.plugins.main.general.overview.glass

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.viewModels
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.TrendCalculator
import app.aaps.core.main.extensions.directionToIcon
import app.aaps.core.main.R as CoreMainR
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventBucketedDataCreated
import app.aaps.core.interfaces.rx.events.EventEffectiveProfileSwitchChanged
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.rx.events.EventScale
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewGraph
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewIobCob
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.main.graph.OverviewData
import app.aaps.database.entities.Bolus
import app.aaps.database.entities.Carbs
import app.aaps.database.entities.TherapyEvent
import app.aaps.database.ValueWrapper
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import dagger.android.support.DaggerFragment
import androidx.appcompat.app.AppCompatDelegate
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.schedulers.Schedulers
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * GlassOverviewFragment
 * 
 * Fragmento Dagger adaptado para a branch dev-2 do OpenApsAIMI / AndroidAPS.
 * Conecta diretamente ao Dagger 2, RxBus e APIs de domínio do dev-2, renderizando
 * a interface moderna Glassmorphism em Jetpack Compose.
 */
class GlassOverviewFragment : DaggerFragment() {

    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var config: Config
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var overviewData: OverviewData
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var trendCalculator: TrendCalculator
    @Inject lateinit var notificationStore: NotificationStore

    private var selectedRangeHours: Int = 6

    private val viewModel: GlassOverviewViewModel by viewModels()
    private val disposables = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inicializa o tema do ViewModel baseado no SP (mesma chave das Preferências)
        val isDarkFromSP = try {
            sp.getString(app.aaps.core.utils.R.string.key_use_dark_mode, "dark") == "dark"
        } catch (e: Exception) { true }
        viewModel.initTheme(isDarkFromSP)

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val uiState by viewModel.uiState.collectAsState()

                SideEffect { applyActivityBackground(uiState.isDarkMode) }

                GlassOverviewTheme(isDarkMode = uiState.isDarkMode) {
                    GlassOverviewScreen(
                        state = uiState,
                        onToggleTheme = {
                            viewModel.toggleTheme()
                            val newIsDark = viewModel.uiState.value.isDarkMode
                            // Salva no SP para manter consistência com a tela de Preferências
                            sp.putString(app.aaps.core.utils.R.string.key_use_dark_mode, if (newIsDark) "dark" else "light")
                            AppCompatDelegate.setDefaultNightMode(
                                if (newIsDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                            )
                        },
                        onSelectRangeHours = { hours ->
                            selectedRangeHours = hours
                            overviewData.rangeToDisplay = hours
                            viewModel.setTimeRange(hours)
                            rxBus.send(EventScale(hours, 1))
                            refreshGraphData()
                        },
                        onOpenWizard = {
                            try {
                                uiInteraction.runWizardDialog(childFragmentManager)
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Erro ao abrir Wizard Dialog", e)
                            }
                        },
                        onOpenStats = {
                            try {
                                // Abre o GlycoStatsFragment no container usando supportFragmentManager
                                val containerId = requireContext().resources.getIdentifier(
                                    "glass_tools_container", "id", requireContext().packageName
                                )
                                if (containerId != 0) {
                                    val fragment = GlycoStatsFragment()
                                    requireActivity().supportFragmentManager.beginTransaction()
                                        .replace(containerId, fragment, "GlycoStatsFragment")
                                        .addToBackStack(null)
                                        .commit()

                                    // Mostra o container
                                    val container = requireActivity().findViewById<View>(containerId)
                                    container?.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Erro ao abrir Stats", e)
                            }
                        },
                        onOpenTreatments = {
                            try {
                                // Abre o GlassTreatmentsFragment no container usando supportFragmentManager
                                val containerId = requireContext().resources.getIdentifier(
                                    "glass_tools_container", "id", requireContext().packageName
                                )
                                if (containerId != 0) {
                                    val fragment = GlassTreatmentsFragment()
                                    requireActivity().supportFragmentManager.beginTransaction()
                                        .replace(containerId, fragment, "GlassTreatmentsFragment")
                                        .addToBackStack(null)
                                        .commit()

                                    // Mostra o container
                                    val container = requireActivity().findViewById<View>(containerId)
                                    container?.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Erro ao abrir Treatments", e)
                            }
                        },
                        onOpenProfile = {
                            try {
                                uiInteraction.runProfileSwitchDialog(childFragmentManager)
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Erro ao abrir Profile Dialog", e)
                            }
                        },
                        onOpenLoopAction = {
                            try {
                                val containerId = requireContext().resources.getIdentifier(
                                    "glass_tools_container", "id", requireContext().packageName
                                )
                                if (containerId != 0) {
                                    val fragment = GlassLoopDashboardFragment()
                                    requireActivity().supportFragmentManager.beginTransaction()
                                        .replace(containerId, fragment, "GlassLoopDashboardFragment")
                                        .addToBackStack(null)
                                        .commit()
                                    val container = requireActivity().findViewById<View>(containerId)
                                    container?.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Error opening Loop Dashboard", e)
                            }
                        },
                        onOpenInsulin = {
                            try {
                                val dialog = GlassInsulinDialogFragment()
                                dialog.show(childFragmentManager, "GlassInsulinDialog")
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Error opening Insulin Dialog", e)
                            }
                        },
                        onOpenTempTarget = {
                            try {
                                uiInteraction.runTempTargetDialog(childFragmentManager)
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Erro ao abrir TempTarget Dialog", e)
                            }
                        },
                        onOpenTempBasal = {
                            try {
                                uiInteraction.runTempBasalDialog(childFragmentManager)
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Erro ao abrir TempBasal Dialog", e)
                            }
                        },
                        onOpenSensorInsert = {
                            try {
                                val containerId = requireContext().resources.getIdentifier(
                                    "glass_tools_container", "id", requireContext().packageName
                                )
                                if (containerId != 0) {
                                    val fragment = GlassSensorInsertFragment()
                                    requireActivity().supportFragmentManager.beginTransaction()
                                        .replace(containerId, fragment, "GlassSensorInsertFragment")
                                        .addToBackStack(null)
                                        .commit()
                                    val container = requireActivity().findViewById<View>(containerId)
                                    container?.visibility = View.VISIBLE
                                }
                            } catch (e: Exception) {
                                aapsLogger.error(LTag.UI, "Erro ao abrir CGM Sensor Insert", e)
                            }
                        },
                        onRefreshData = { refreshAllData() },
                        onDismissNotification = { notificationId ->
                            notificationStore.remove(notificationId)
                            refreshNotifications()
                        }
                    )
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Refresh imediato
        refreshAllData()
        // Re-tentativas atrasadas para capturar os dados assim que o app terminar de
        // inicializar (ADS, perfil, loop), já que o primeiro refresh roda cedo demais.
        view.postDelayed({ refreshAllData() }, 1000)
        view.postDelayed({ refreshAllData() }, 3000)
        view.postDelayed({ refreshAllData() }, 6000)
    }

    override fun onStart() {
        super.onStart()
        setupRxSubscriptions()
    }

    override fun onStop() {
        disposables.clear()
        super.onStop()
    }

    private fun setupRxSubscriptions() {
        // Timer periódico para atualizar timeAgo a cada 30 segundos
        disposables += io.reactivex.rxjava3.core.Observable.interval(30, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshLastBg() }, fabricPrivacy::logException)

        disposables += rxBus.toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshAllData() }, fabricPrivacy::logException)

        disposables += rxBus.toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshPumpStatus() }, fabricPrivacy::logException)

        // Novo dado de glicemia gravado (alimenta BG/delta e gráficos)
        disposables += rxBus.toObservable(EventBucketedDataCreated::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshLastBg(); refreshGraphData() }, fabricPrivacy::logException)

        // Target temporário criado/removido (atualiza cor do botão target)
        disposables += rxBus.toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshBasal() }, fabricPrivacy::logException)

        // Loop status atualizado (atualiza ícone e texto do loop)
        disposables += rxBus.toObservable(EventRefreshOverview::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshBasal() }, fabricPrivacy::logException)

        // IOB/COB recalculado pelo loop (alimenta os valores de insulina/carbo)
        disposables += rxBus.toObservable(EventUpdateOverviewIobCob::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshPumpStatus() }, fabricPrivacy::logException)

        // Listen on overviewBus (sent by UpdateGraphWorker after IOB calculation)
        disposables += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewGraph::class.java)
            .debounce(1L, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshGraphData() }, fabricPrivacy::logException)

        // Also listen on rxBus (sent by other sources)
        disposables += rxBus.toObservable(EventUpdateOverviewGraph::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshGraphData() }, fabricPrivacy::logException)

        // After pump delivers bolus/SMB, refresh graph to show new treatment markers
        disposables += rxBus.toObservable(app.aaps.core.interfaces.rx.events.EventUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshGraphData() }, fabricPrivacy::logException)

        disposables += rxBus.toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshBasal() }, fabricPrivacy::logException)

        disposables += rxBus.toObservable(EventEffectiveProfileSwitchChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshProfile() }, fabricPrivacy::logException)

        disposables += rxBus.toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshAllData() }, fabricPrivacy::logException)

        // Notification updates - usa overviewBus (não rxBus)
        disposables += activePlugin.activeOverview.overviewBus
            .toObservable(EventUpdateOverviewNotification::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ refreshNotifications() }, fabricPrivacy::logException)

        // Initial notification load
        refreshNotifications()
    }

    private fun refreshAllData() {
        refreshRange()
        refreshLastBg()
        refreshPumpStatus()
        refreshGraphData()
        refreshBasal()
        refreshProfile()
        refreshNotifications()
    }

    private fun refreshRange() {
        val low = try { profileUtil.convertToMgdlDetect(defaultValueHelper.determineLowLine()).toFloat() } catch (e: Throwable) { 70f }
        val high = try { profileUtil.convertToMgdlDetect(defaultValueHelper.determineHighLine()).toFloat() } catch (e: Throwable) { 180f }
        viewModel.updateRange(low.coerceAtLeast(20f), high.coerceAtMost(400f).coerceAtLeast(low + 1f))
    }

    private fun refreshLastBg() {
        val lastBg = try {
            iobCobCalculator.ads.actualBg() ?: iobCobCalculator.ads.lastBg()
        } catch (e: Throwable) {
            null
        }

        if (lastBg == null) {
            viewModel.updateBg(
                bg = "--",
                rawBg = 0f,
                delta = 0,
                trend = "→",
                trendArrowRes = 0,
                timeAgo = "--",
                unit = "mg/dL"
            )
            return
        }

        val bgValue = lastBg.value
        val delta = try {
            glucoseStatusProvider.getGlucoseStatusData(true)?.delta ?: 0.0
        } catch (e: Throwable) {
            0.0
        }
        
        // Usa TrendCalculator para obter a seta correta (mesmo cálculo do OverviewFragment original)
        val trendArrowEnum = try {
            trendCalculator.getTrendArrow(iobCobCalculator.ads)
        } catch (e: Throwable) {
            null
        }
        val trendArrowRes = try {
            trendArrowEnum?.directionToIcon() ?: CoreMainR.drawable.ic_invalid
        } catch (e: Throwable) {
            CoreMainR.drawable.ic_invalid
        }
        val trendArrowText = trendArrowEnum?.text ?: "UNKNOWN"

        val timeAgo = formatTimeAgo(lastBg.timestamp)
        val isMmol = try {
            profileFunction.getUnits() == GlucoseUnit.MMOL
        } catch (e: Throwable) {
            false
        }

        val formattedBg = if (isMmol) {
            String.format(Locale.US, "%.1f", bgValue / 18.01559)
        } else {
            bgValue.toInt().toString()
        }

        viewModel.updateBg(
            bg = formattedBg,
            rawBg = bgValue.toFloat(),
            delta = delta.toInt(),
            trend = trendArrowText,
            trendArrowRes = trendArrowRes,
            timeAgo = timeAgo,
            unit = if (isMmol) "mmol/L" else "mg/dL"
        )
    }

    private fun refreshPumpStatus() {
        val bolusIob = try { iobCobCalculator.calculateIobFromBolus().iob } catch (e: Throwable) { 0.0 }
        val basalIob = try { iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().basaliob } catch (e: Throwable) { 0.0 }
        val totalIob = (bolusIob + basalIob).toFloat().coerceAtLeast(0f)

        val cob = try { (iobCobCalculator.getCobInfo("GlassOverview").displayCob ?: 0.0).toFloat().coerceAtLeast(0f) } catch (e: Throwable) { 0f }

        val isClosed = try {
            loop.isEnabled() && loop.closedLoopEnabled?.value() == true
        } catch (e: Throwable) {
            false
        }

        // Status do loop - sempre mostra "Loop"
        val loopStatusText = "Loop"

        val pump = try { activePlugin.activePump } catch (e: Throwable) { null }
        val reservoirText = if (pump != null && pump.isInitialized()) {
            val res = try { pump.reservoirLevel } catch (e: Throwable) { -1.0 }
            if (res >= 0.0) "${res}U" else "--"
        } else "--"

        val batteryText = if (pump != null) {
            val bat = try { pump.batteryLevel } catch (e: Throwable) { -1 }
            if (bat in 0..100) "${bat}%" else "--"
        } else "--"

        val pumpStatusText = try { overviewData.pumpStatus ?: "" } catch (e: Throwable) { "" }

        val basalPercent = try {
            val tbr = overviewData.temporaryBasalText(iobCobCalculator)
            tbr.filter { it.isDigit() || it == '.' || it == ' ' }.trim().toIntOrNull() ?: 0
        } catch (e: Throwable) {
            0
        }

        viewModel.updatePumpAndSensors(
            iob = totalIob,
            cob = cob,
            reservoir = reservoirText,
            battery = batteryText,
            sensorLife = pumpStatusText,
            isLoopActive = isClosed,
            loopStatusText = loopStatusText
        )

        // Atualiza notificação de status da pump (SMB, Temp Basal, etc.)
        viewModel.updatePumpStatus(pumpStatusText)

        // Calcula idade do sensor e cor baseada nos thresholds do usuário
        val sensorAgeResult = try {
            val event = repository.getLastTherapyRecordUpToNow(TherapyEvent.Type.SENSOR_CHANGE).blockingGet()
            if (event is ValueWrapper.Existing) {
                val diffMs = System.currentTimeMillis() - event.value.timestamp
                val diffHours = java.util.concurrent.TimeUnit.MILLISECONDS.toHours(diffMs)
                val days = diffHours / 24
                val hours = diffHours % 24
                val text = "${days}d ${hours}h"
                // Cores: vermelho se > urgentThreshold, amarelo se > warnThreshold, senão branco
                val warnThreshold = sp.getDouble(app.aaps.core.utils.R.string.key_statuslights_sage_warning, 216.0)
                val urgentThreshold = sp.getDouble(app.aaps.core.utils.R.string.key_statuslights_sage_critical, 240.0)
                val color = when {
                    diffHours >= urgentThreshold -> 0xFFEF4444.toInt() // vermelho
                    diffHours >= warnThreshold   -> 0xFFF59E0B.toInt() // amarelo
                    else                         -> 0xFF22C55E.toInt() // verde
                }
                Pair(text, color)
            } else Pair("--", 0xFF94A3B8.toInt())
        } catch (e: Throwable) { Pair("--", 0xFF94A3B8.toInt()) }
        viewModel.updateSensorAge(sensorAgeResult.first, sensorAgeResult.second)

        viewModel.updateBasalAndTarget(
            basalPercent = basalPercent,
            targetBg = refreshTargetOnly(),
            targetText = getTargetText(),
            isTempTargetActive = overviewData.temporaryTarget != null
        )
    }

    private fun getTargetText(): String {
        val tempTarget = overviewData.temporaryTarget
        if (tempTarget != null) {
            val target = tempTarget.lowTarget.toInt()
            val until = dateUtil.untilString(tempTarget.timestamp + tempTarget.duration, rh)
            return "$target - $until"
        }
        return ""
    }

    private fun refreshTargetOnly(): Int {
        val target = try {
            val profile = profileFunction.getProfile()
            if (profile != null) {
                val cal = Calendar.getInstance()
                val sec = cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60
                profile.getTargetLowMgdlTimeFromMidnight(sec).toInt()
            } else 100
        } catch (e: Throwable) {
            100
        }
        return target
    }

    private fun refreshBasal() {
        viewModel.updateBasalAndTarget(
            basalPercent = try {
                val tbr = overviewData.temporaryBasalText(iobCobCalculator)
                tbr.filter { it.isDigit() || it == '.' || it == ' ' }.trim().toIntOrNull() ?: 0
            } catch (e: Throwable) {
                0
            },
            targetBg = refreshTargetOnly(),
            targetText = getTargetText(),
            isTempTargetActive = overviewData.temporaryTarget != null
        )
        processAps()
    }

    private fun processAps() {
        val pump = activePlugin.activePump
        val closedLoopEnabled = constraintChecker.isClosedLoopAllowed()

        var loopIconRes = 0
        var loopStatusText = "Loop"
        var loopTimeRemaining = ""

        when {
            // SuperBolus
            loop.isSuperBolus -> {
                loopIconRes = app.aaps.plugins.main.R.drawable.ic_loop_superbolus
                loopStatusText = "SuperBolus"
                loopTimeRemaining = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
            }
            // Disconnected
            loop.isDisconnected -> {
                loopIconRes = app.aaps.core.ui.R.drawable.ic_loop_disconnected
                loopStatusText = "Desconectado"
                loopTimeRemaining = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
            }
            // Loop Suspended
            loop.isSuspended -> {
                loopIconRes = app.aaps.core.ui.R.drawable.ic_loop_paused
                loopStatusText = "Pausado"
                loopTimeRemaining = dateUtil.age(loop.minutesToEndOfSuspend() * 60000L, true, rh)
            }
            // Pump Suspended
            pump.isSuspended() -> {
                loopIconRes = if (pump.model() == PumpType.OMNIPOD_EROS || pump.model() == PumpType.OMNIPOD_DASH) {
                    app.aaps.core.ui.R.drawable.ic_loop_disconnected
                } else {
                    app.aaps.core.ui.R.drawable.ic_loop_paused
                }
                loopStatusText = "Bomba Pausada"
                loopTimeRemaining = ""
            }
            // LGS Mode
            loop.isLGS -> {
                loopIconRes = app.aaps.core.ui.R.drawable.ic_loop_lgs
                loopStatusText = "LGS"
                loopTimeRemaining = ""
            }
            // Closed Loop enabled
            config.APS && pump.pumpDescription.isTempBasalCapable && loop.isEnabled() && closedLoopEnabled.value() -> {
                loopIconRes = 0 // Use pulsing circle
                loopStatusText = "Loop"
                loopTimeRemaining = ""
            }
            // Open Loop
            loop.isEnabled() && !closedLoopEnabled.value() -> {
                loopIconRes = app.aaps.core.ui.R.drawable.ic_loop_open
                loopStatusText = "Open"
                loopTimeRemaining = ""
            }
            // Loop disabled
            else -> {
                loopIconRes = app.aaps.core.ui.R.drawable.ic_loop_disabled
                loopStatusText = "Desativado"
                loopTimeRemaining = ""
            }
        }

        viewModel.updateLoopStatus(loopIconRes, loopStatusText, loopTimeRemaining)
    }

    private fun refreshProfile() {
        // Atualizações de perfil
    }

    private fun refreshNotifications() {
        val notifications = notificationStore.getNotifications().map { n ->
            GlassNotificationItem(
                id = n.id,
                text = n.text,
                level = n.level,
                date = n.date
            )
        }
        viewModel.updateNotifications(notifications)
    }

    private fun refreshGraphData() {
        try {
            val hours = selectedRangeHours
            val toTime = System.currentTimeMillis()
            val fromTime = toTime - hours * 60 * 60 * 1000L
            val range = (toTime - fromTime).toDouble().coerceAtLeast(1.0)

            val profile = profileFunction.getProfile()

            // IOB sampling (on background)
            val iobPoints = try {
                if (profile != null) {
                    val step = 5 * 60 * 1000L
                    val samples = mutableListOf<IobReadingPoint>()
                    var t = fromTime
                    while (t <= toTime) {
                        val iobVal = iobCobCalculator.calculateFromTreatmentsAndTemps(t, profile).iob
                        val progress = ((t - fromTime) / range).toFloat().coerceIn(0f, 1f)
                        samples.add(IobReadingPoint(progress, iobVal.toFloat().coerceAtLeast(0f)))
                        t += step
                    }
                    samples
                } else emptyList()
            } catch (e: Throwable) {
                emptyList()
            }

            // Treatment markers (on background)
            val treatmentPoints = try {
                val boluses = repository.getBolusesDataFromTimeToTime(fromTime, toTime, true).blockingGet()
                val carbList = repository.getCarbsDataFromTimeToTimeExpanded(fromTime, toTime, true).blockingGet()
                val points = mutableListOf<TreatmentPoint>()
                boluses
                    .filter { it.type == Bolus.Type.NORMAL || it.type == Bolus.Type.SMB }
                    .forEach { b ->
                        val label = if (b.type == Bolus.Type.SMB) {
                            String.format(Locale.US, "SMB %.1fU", b.amount)
                        } else {
                            String.format(Locale.US, "Bolus %.1fU", b.amount)
                        }
                        points.add(TreatmentPoint(((b.timestamp - fromTime) / range).toFloat().coerceIn(0f, 1f), false, label, b.timestamp))
                    }
                carbList.forEach { c ->
                    points.add(TreatmentPoint(((c.timestamp - fromTime) / range).toFloat().coerceIn(0f, 1f), true, "${c.amount.toInt()} g", c.timestamp))
                }
                points
            } catch (e: Throwable) {
                emptyList()
            }

            // BG readings: query DB on IO thread, fallback to overviewData
            disposables += repository.compatGetBgReadingsDataFromTime(fromTime, toTime, false)
                .subscribeOn(Schedulers.io())
                .observeOn(aapsSchedulers.main)
                .subscribe({ bgArray ->
                    applyGraphData(bgArray, fromTime, range, iobPoints, treatmentPoints)
                }, {
                    applyGraphData(overviewData.bgReadingsArray, fromTime, range, iobPoints, treatmentPoints)
                })
        } catch (e: Throwable) {
            // mantém dados existentes
        }
    }

    private fun applyGraphData(
        bgArray: List<app.aaps.database.entities.GlucoseValue>,
        fromTime: Long,
        range: Double,
        iobPoints: List<IobReadingPoint>,
        treatmentPoints: List<TreatmentPoint>
    ) {
        if (bgArray.isEmpty()) return
        val bgPoints = bgArray
            .filter { it.timestamp >= fromTime }
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
            .map { gv ->
                val progress = ((gv.timestamp - fromTime) / range).toFloat().coerceIn(0f, 1f)
                BgReadingPoint(progress, gv.value.toFloat())
            }

        viewModel.updateGraphData(
            bgReadings = bgPoints,
            iobReadings = iobPoints,
            treatments = treatmentPoints
        )
    }

    private fun applyActivityBackground(isDark: Boolean) {
        val activity = activity ?: return
        val pkg = activity.packageName
        val root = activity.window.decorView.rootView

        val bgId = activity.resources.getIdentifier(
            if (isDark) "bg_overview_activity_gradient_dark" else "bg_overview_activity_gradient",
            "drawable",
            pkg
        )
        if (bgId != 0) root.setBackgroundResource(bgId)

        // Status bar combina com o topo do gradiente glass
        activity.window.statusBarColor = if (isDark) 0xFF070E1B.toInt() else 0xFFF1F5F9.toInt()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val decorView = activity.window.decorView
            if (isDark) {
                decorView.systemUiVisibility = decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
            } else {
                decorView.systemUiVisibility = decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        val resolved = mutableMapOf<String, Int>()
        fun rid(name: String): Int {
            if (resolved.containsKey(name)) return resolved[name] ?: 0
            val id = activity.resources.getIdentifier(name, "id", pkg)
            resolved[name] = id
            return id
        }

        val cardId = rid("main_buttons_card")
        if (cardId != 0) {
            val card = root.findViewById<com.google.android.material.card.MaterialCardView>(cardId)
            if (card != null) {
                val glassColor = if (isDark) android.graphics.Color.argb(217, 15, 23, 42) else android.graphics.Color.argb(242, 255, 255, 255)
                card.setCardBackgroundColor(glassColor)
                card.strokeColor = if (isDark) android.graphics.Color.argb(51, 255, 255, 255) else android.graphics.Color.argb(128, 203, 213, 225)
            }
        }

        val barId = rid("main_buttons_layout")
        if (barId != 0) {
            val raw = root.findViewById<View>(barId)
            raw?.setBackgroundResource(0)
        }

        val iconActive = if (isDark) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
        val iconInactive = if (isDark) 0xFF64748B.toInt() else 0xFF94A3B8.toInt()
        val textActive = iconActive
        val textInactive = if (isDark) 0xFF94A3B8.toInt() else 0xFF64748B.toInt()

        val icons = mapOf(
            "home_icon" to textActive,
            "tools_icon" to textInactive,
            "bolus_icon" to textInactive,
            "override_icon" to textInactive,
            "settings_icon" to textInactive
        )
        for ((name, color) in icons) {
            val id = rid(name)
            if (id != 0) (root.findViewById<View>(id) as? ImageView)?.setColorFilter(color)
        }

        val texts = mapOf(
            "home_text" to textActive,
            "tools_text" to textInactive,
            "bolus_text" to textInactive,
            "override_text" to textInactive,
            "settings_text" to textInactive
        )
        for ((name, color) in texts) {
            val id = rid(name)
            if (id != 0) (root.findViewById<View>(id) as? TextView)?.setTextColor(color)
        }
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val diffMillis = System.currentTimeMillis() - timestamp
        val diffSec = diffMillis / 1000
        return when {
            diffSec < 60 -> "${diffSec}s atrás"
            diffSec < 3600 -> "${diffSec / 60}m atrás"
            else -> "${diffSec / 3600}h atrás"
        }
    }
}
