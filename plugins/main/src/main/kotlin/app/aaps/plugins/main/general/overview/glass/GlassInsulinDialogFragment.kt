package app.aaps.plugins.main.general.overview.glass

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class GlassInsulinDialogFragment : DaggerDialogFragment() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var automation: Automation
    @Inject lateinit var sp: SP
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val disposable = CompositeDisposable()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isSaving by mutableStateOf(false)
    private var delivering by mutableStateOf(false)
    private var progressPercent by mutableStateOf(-1)
    private var progressStatus by mutableStateOf("")
    private var delivered by mutableStateOf(false)
    private var deliveredAmount by mutableStateOf(0.0)
    private var deliverError by mutableStateOf<String?>(null)
    private var userStopped = false
    private var deliveringBolusId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val isDark = resolveIsDarkMode(sp)

        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep

        // Get current IOB, BG and ISF values
        val currentIOB = try {
            iobCobCalculator.calculateIobFromBolus().iob
        } catch (e: Exception) { 0.0 }
        val currentBG = try {
            glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0
        } catch (e: Exception) { 0.0 }
        val isMmol = try {
            profileFunction.getUnits() == GlucoseUnit.MMOL
        } catch (e: Exception) { false }
        val isf = try {
            val profile = profileFunction.getProfile()
            val isfMgdl = profile?.getIsfMgdl() ?: 0.0
            if (isMmol) profileUtil.fromMgdlToUnits(isfMgdl) else isfMgdl
        } catch (e: Exception) { 0.0 }

        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                GlassOverviewTheme(isDarkMode = isDark) {
                    GlassInsulinDialogScreen(
                        isDark = isDark,
                        maxInsulin = maxInsulin,
                        bolusStep = bolusStep,
                        currentIOB = currentIOB,
                        currentBG = if (isMmol) profileUtil.fromMgdlToUnits(currentBG) else currentBG,
                        isf = isf,
                        isMmol = isMmol,
                        onDismiss = { dismiss() },
                        onConfirmDelivery = { amount, recordOnly, eatingSoon, notes ->
                            submitInsulin(amount, recordOnly, eatingSoon, notes)
                        },
                        delivering = delivering,
                        progressPercent = progressPercent,
                        progressStatus = progressStatus,
                        delivered = delivered,
                        deliveredAmount = deliveredAmount,
                        deliverError = deliverError,
                        onStopDelivery = { stopDelivery() },
                        onClearError = { deliverError = null }
                    )
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            window.setLayout(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT
            )
            window.setGravity(Gravity.CENTER)
            window.attributes.windowAnimations = android.R.style.Animation_Dialog
        }
        return dialog
    }

    private fun submitInsulin(amount: Double, recordOnly: Boolean, eatingSoon: Boolean, notes: String) {
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(amount, aapsLogger)).value()

        val eatingSoonTTDuration = defaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = defaultValueHelper.determineEatingSoonTT()

        val notesStr = notes.trim()

        if (insulinAfterConstraints <= 0 && !eatingSoon) {
            deliverError = "Select a dose or option first"
            return
        }
        if (isSaving) return
        isSaving = true
        deliveredAmount = insulinAfterConstraints
        userStopped = false

        if (eatingSoon) {
            uel.log(
                UserEntry.Action.TT, UserEntry.Sources.InsulinDialog,
                notesStr,
                ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.EATING_SOON),
                ValueWithUnit.fromGlucoseUnit(eatingSoonTT, profileFunction.getUnits().asText),
                ValueWithUnit.Minute(eatingSoonTTDuration)
            )
            disposable += repository.runTransactionForResult(
                InsertAndCancelCurrentTemporaryTargetTransaction(
                    timestamp = System.currentTimeMillis(),
                    duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                    reason = TemporaryTarget.Reason.EATING_SOON,
                    lowTarget = profileUtil.convertToMgdl(eatingSoonTT, profileFunction.getUnits()),
                    highTarget = profileUtil.convertToMgdl(eatingSoonTT, profileFunction.getUnits())
                )
            ).subscribe({ result ->
                result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
            }, {
                aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
            })
        }
        if (insulinAfterConstraints > 0) {
            val detailedBolusInfo = DetailedBolusInfo()
            detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
            detailedBolusInfo.insulin = insulinAfterConstraints
            detailedBolusInfo.context = requireContext()
            detailedBolusInfo.notes = notesStr
            detailedBolusInfo.timestamp = dateUtil.now()
            if (recordOnly) {
                uel.log(
                    UserEntry.Action.BOLUS, UserEntry.Sources.InsulinDialog,
                    "Record" + if (notesStr.isNotEmpty()) ": $notesStr" else "",
                    ValueWithUnit.SimpleString("Record"),
                    ValueWithUnit.Insulin(insulinAfterConstraints)
                )
                try {
                    persistenceLayer.insertOrUpdateBolus(detailedBolusInfo.createBolus())
                    automation.removeAutomationEventBolusReminder()
                    finishDelivered()
                } catch (e: Exception) {
                    aapsLogger.error(LTag.DATABASE, "Error recording bolus", e)
                    isSaving = false
                    deliverError = "Error saving: ${e.message}"
                }
            } else {
                uel.log(
                    UserEntry.Action.BOLUS, UserEntry.Sources.InsulinDialog,
                    notesStr,
                    ValueWithUnit.Insulin(insulinAfterConstraints)
                )
                delivering = true
                deliveringBolusId = detailedBolusInfo.id
                progressPercent = -1
                progressStatus = ""
                subscribeProgress()
                commandQueue.bolus(detailedBolusInfo, object : Callback() {
                    override fun run() {
                        mainHandler.post {
                            delivering = false
                            isSaving = false
                            if (result.success) {
                                automation.removeAutomationEventBolusReminder()
                                delivered = true
                                rxBus.send(EventRefreshOverview("bolus_delivered"))
                                scheduleAutoDismiss()
                            } else {
                                if (userStopped) {
                                    deliverError = "Cancelled"
                                } else {
                                    uiInteraction.runAlarm(result.comment, "Treatment delivery error", app.aaps.core.ui.R.raw.boluserror)
                                    deliverError = result.comment
                                }
                            }
                        }
                    }
                })
                suppressOldProgressDialog()
            }
        } else {
            // Eating Soon only (no insulin)
            finishDelivered()
        }
    }

    private fun finishDelivered() {
        isSaving = false
        delivered = true
        rxBus.send(EventRefreshOverview("bolus_delivered"))
        scheduleAutoDismiss()
    }

    private fun scheduleAutoDismiss() {
        mainHandler.postDelayed({
            try {
                dismiss()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Error dismissing insulin dialog", e)
            }
        }, 1400)
    }

    private fun subscribeProgress() {
        disposable += rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event ->
                if (!event.isSMB() && delivering) {
                    if (event.percent in 0..100) progressPercent = event.percent
                    if (event.status.isNotEmpty()) progressStatus = event.status
                    suppressOldProgressDialog()
                }
            }, {
                aapsLogger.error(LTag.PUMP, "Error subscribing bolus progress", it)
            })
    }

    /**
     * The shared queue shows the legacy BolusProgressDialog for every queued bolus.
     * Dismiss it (with retries, its show transaction is async) so only the new UI is visible.
     */
    private fun suppressOldProgressDialog() {
        var attempts = 0
        val check = object : Runnable {
            override fun run() {
                try {
                    (requireActivity().supportFragmentManager.findFragmentByTag("BolusProgress") as? DialogFragment)?.dismiss()
                } catch (e: Exception) {
                    aapsLogger.error(LTag.UI, "Error dismissing legacy progress dialog", e)
                }
                if (++attempts < 10) mainHandler.postDelayed(this, 300)
            }
        }
        mainHandler.post(check)
    }

    private fun stopDelivery() {
        userStopped = true
        BolusProgressData.stopPressed = true
        try {
            commandQueue.cancelAllBoluses(deliveringBolusId)
        } catch (e: Exception) {
            aapsLogger.error(LTag.PUMP, "Error cancelling bolus", e)
        }
        uel.log(UserEntry.Action.CANCEL_BOLUS, UserEntry.Sources.InsulinDialog, "Cancelled by user")
    }

    override fun onResume() {
        super.onResume()
        activity?.let { activity ->
            protectionCheck.queryProtection(
                activity,
                ProtectionCheck.Protection.BOLUS,
                {},
                {
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    dismiss()
                },
                {
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    dismiss()
                }
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacksAndMessages(null)
        disposable.clear()
    }
}
