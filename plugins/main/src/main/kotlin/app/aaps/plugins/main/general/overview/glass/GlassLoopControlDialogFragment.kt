package app.aaps.plugins.main.general.overview.glass

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.aaps.core.interfaces.aps.ApsMode
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.Objectives
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginType
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.database.entities.OfflineEvent
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentOfflineEventIfAnyTransaction
import app.aaps.database.impl.transactions.InsertAndCancelCurrentOfflineEventTransaction
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class GlassLoopControlDialogFragment : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var ctx: Context
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var loop: Loop
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var configBuilder: ConfigBuilder
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private val disposable = CompositeDisposable()
    private var queryingProtection = false
    private var pendingDesc by mutableStateOf<String?>(null)
    private var pendingAction by mutableStateOf<(() -> Unit)?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val isDark = resolveIsDarkMode(sp)
                GlassOverviewTheme(isDarkMode = isDark) {
                    Box {
                        GlassLoopControlDialogScreen(
                            isDark = isDark,
                            state = buildState(),
                            onModeSelected = { mode -> onModeSelected(mode) },
                            onToggleLoop = { onToggleLoop() },
                            onSuspend = { hours -> onSuspend(hours) },
                            onDisconnect = { minutes -> onDisconnect(minutes) },
                            onResume = { onResumeLoop() },
                            onDismiss = { dismiss() }
                        )
                        if (pendingDesc != null) {
                            Dialog(
                                onDismissRequest = { pendingDesc = null; pendingAction = null },
                                properties = DialogProperties(usePlatformDefaultWidth = false)
                            ) {
                                val confirmBg = if (isDark) Color(0xFF1E293B) else Color.White
                                val confirmBorder = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.8f)
                                val confirmTextPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
                                val confirmTextSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                                val confirmCloseBg = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                                val confirmDivider = if (isDark) Color(0xFF334155) else Color(0xFFF1F5F9)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .shadow(20.dp, RoundedCornerShape(24.dp))
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(confirmBg)
                                        .border(1.dp, confirmBorder, RoundedCornerShape(24.dp))
                                ) {
                                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text("Confirm", color = confirmTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                            Box(
                                                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(12.dp)).background(confirmCloseBg).clickable { pendingDesc = null; pendingAction = null },
                                                contentAlignment = Alignment.Center
                                            ) { Icon(Icons.Default.Close, null, tint = confirmTextSecondary, modifier = Modifier.size(14.dp)) }
                                        }
                                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(confirmDivider))
                                        Text(pendingDesc ?: "", color = confirmTextSecondary, fontSize = 13.sp, lineHeight = 17.sp)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Box(
                                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(confirmCloseBg).border(1.dp, if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0), RoundedCornerShape(12.dp)).clickable { pendingDesc = null; pendingAction = null }.padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) { Text("Cancel", color = confirmTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                                            Box(
                                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF0284C7), Color(0xFF0369A1)))).clickable {
                                                    val act = pendingAction; pendingDesc = null; pendingAction = null; act?.invoke()
                                                }.padding(vertical = 12.dp),
                                                contentAlignment = Alignment.Center
                                            ) { Text("Confirm", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                                        }
                                    }
                                }
                            }
                        }
                    }
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

    private fun buildState(): GlassLoopControlState {
        val pump = activePlugin.activePump
        val pumpDescription = pump.pumpDescription
        val apsMode = ApsMode.fromString(sp.getString(app.aaps.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name))
        val closedLoopAllowed = constraintChecker.isClosedLoopAllowed(ConstraintObject(true, aapsLogger))
        val closedLoopAllowed2 = activePlugin.activeObjectives?.isAccomplished(Objectives.MAXIOB_OBJECTIVE) ?: false
        val lgsEnabled = constraintChecker.isLgsAllowed(ConstraintObject(true, aapsLogger))
        val profileValid = try {
            profileFunction.isProfileValid("GlassLoopControl")
        } catch (e: Exception) {
            false
        }
        val pumpOk = try {
            !pump.isSuspended() && profileValid
        } catch (e: Exception) {
            false
        }

        val currentMode = when {
            !loop.isEnabled() -> GlassLoopMode.DISABLED
            apsMode == ApsMode.CLOSED -> GlassLoopMode.CLOSED
            apsMode == ApsMode.LGS -> GlassLoopMode.LGS
            else -> GlassLoopMode.OPEN
        }

        return GlassLoopControlState(
            currentMode = currentMode,
            showClosed = (apsMode == ApsMode.LGS && closedLoopAllowed.value()) ||
                (apsMode == ApsMode.OPEN && closedLoopAllowed2),
            showLgs = apsMode == ApsMode.CLOSED ||
                (apsMode == ApsMode.OPEN && lgsEnabled.value()),
            showOpen = apsMode == ApsMode.CLOSED || apsMode == ApsMode.LGS,
            loopEnabled = loop.isEnabled(),
            showResume = try {
                loop.isSuspended || loop.isDisconnected
            } catch (e: Exception) {
                false
            },
            resumeLabel = try {
                if (loop.isDisconnected) "Reconnect" else "Resume"
            } catch (e: Exception) {
                "Resume"
            },
            showSuspendSection = pumpOk && loop.isEnabled(),
            showDisconnectSection = pumpOk && loop.isEnabled(),
            allow15m = pumpDescription.tempDurationStep15mAllowed,
            allow30m = pumpDescription.tempDurationStep30mAllowed
        )
    }

    private fun confirmAndRun(description: String, action: () -> Unit) {
        pendingDesc = description
        pendingAction = {
            action()
            try {
                dismiss()
            } catch (e: Exception) {
                aapsLogger.error(LTag.UI, "Error dismissing loop control dialog", e)
            }
        }
    }

    private fun onModeSelected(mode: GlassLoopMode) {
        when (mode) {
            GlassLoopMode.CLOSED -> confirmAndRun(rh.gs(app.aaps.core.ui.R.string.closedloop)) {
                uel.log(UserEntry.Action.CLOSED_LOOP_MODE, UserEntry.Sources.LoopDialog)
                sp.putString(app.aaps.core.utils.R.string.key_aps_mode, ApsMode.CLOSED.name)
                rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.closedloop)))
            }
            GlassLoopMode.LGS -> confirmAndRun(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)) {
                uel.log(UserEntry.Action.LGS_LOOP_MODE, UserEntry.Sources.LoopDialog)
                sp.putString(app.aaps.core.utils.R.string.key_aps_mode, ApsMode.LGS.name)
                rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)))
            }
            GlassLoopMode.OPEN -> confirmAndRun(rh.gs(app.aaps.core.ui.R.string.openloop)) {
                uel.log(UserEntry.Action.OPEN_LOOP_MODE, UserEntry.Sources.LoopDialog)
                sp.putString(app.aaps.core.utils.R.string.key_aps_mode, ApsMode.OPEN.name)
                rxBus.send(EventPreferenceChange(rh.gs(app.aaps.core.ui.R.string.lowglucosesuspend)))
            }
            GlassLoopMode.DISABLED -> onToggleLoop()
        }
    }

    private fun onToggleLoop() {
        if (loop.isEnabled()) {
            confirmAndRun(rh.gs(app.aaps.core.ui.R.string.disableloop)) {
                uel.log(UserEntry.Action.LOOP_DISABLED, UserEntry.Sources.LoopDialog)
                (loop as PluginBase).setPluginEnabled(PluginType.LOOP, false)
                (loop as PluginBase).setFragmentVisible(PluginType.LOOP, false)
                configBuilder.storeSettings("DisablingLoop")
                rxBus.send(EventRefreshOverview("suspend_menu"))
                commandQueue.cancelTempBasal(true, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            aapsLogger.error(LTag.PUMP, "Temp basal cancel failed: ${result.comment}")
                        }
                    }
                })
                disposable += repository.runTransactionForResult(
                    InsertAndCancelCurrentOfflineEventTransaction(dateUtil.now(), T.days(365).msecs(), OfflineEvent.Reason.DISABLE_LOOP)
                ).subscribe({ result ->
                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted OfflineEvent $it") }
                }, {
                    aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                })
            }
        } else {
            confirmAndRun(rh.gs(app.aaps.core.ui.R.string.enableloop)) {
                uel.log(UserEntry.Action.LOOP_ENABLED, UserEntry.Sources.LoopDialog)
                (loop as PluginBase).setPluginEnabled(PluginType.LOOP, true)
                (loop as PluginBase).setFragmentVisible(PluginType.LOOP, true)
                configBuilder.storeSettings("EnablingLoop")
                rxBus.send(EventRefreshOverview("suspend_menu"))
                disposable += repository.runTransactionForResult(CancelCurrentOfflineEventIfAnyTransaction(dateUtil.now()))
                    .subscribe({ result ->
                        result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                    }, {
                        aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                    })
            }
        }
    }

    private fun onSuspend(hours: Int) {
        val description = if (hours == 1) "suspend loop for 1 hour ?"
        else "suspend loop for $hours hours ?"
        confirmAndRun(description) {
            uel.log(UserEntry.Action.SUSPEND, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(hours))
            loop.suspendLoop(T.hours(hours.toLong()).mins().toInt())
            rxBus.send(EventRefreshOverview("suspend_menu"))
        }
    }

    private fun onDisconnect(minutes: Int) {
        val description = if (minutes < 60) "disconnect pump for $minutes minutes ?"
        else if (minutes == 60) "disconnect pump for 1 hour ?"
        else "disconnect pump for ${minutes / 60} hours ?"
        confirmAndRun(description) {
            profileFunction.getProfile()?.let { profile ->
                if (minutes < 60) {
                    uel.log(UserEntry.Action.DISCONNECT, UserEntry.Sources.LoopDialog, ValueWithUnit.Minute(minutes))
                } else {
                    uel.log(UserEntry.Action.DISCONNECT, UserEntry.Sources.LoopDialog, ValueWithUnit.Hour(minutes / 60))
                }
                loop.goToZeroTemp(T.mins(minutes.toLong()).mins().toInt(), profile, OfflineEvent.Reason.DISCONNECT_PUMP)
                rxBus.send(EventRefreshOverview("suspend_menu"))
            }
            if (minutes == 60) {
                sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusedisconnect, true)
            }
        }
    }

    private fun onResumeLoop() {
        val isDisconnect = try {
            loop.isDisconnected
        } catch (e: Exception) {
            false
        }
        confirmAndRun(if (isDisconnect) "reconnect pump ?" else "resume loop ?") {
            uel.log(
                if (isDisconnect) UserEntry.Action.RECONNECT else UserEntry.Action.RESUME,
                UserEntry.Sources.LoopDialog
            )
            disposable += repository.runTransactionForResult(CancelCurrentOfflineEventIfAnyTransaction(dateUtil.now()))
                .subscribe({ result ->
                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated OfflineEvent $it") }
                }, {
                    aapsLogger.error(LTag.DATABASE, "Error while saving OfflineEvent", it)
                })
            rxBus.send(EventRefreshOverview("suspend_menu"))
            commandQueue.cancelTempBasal(true, object : Callback() {
                override fun run() {
                    if (!result.success) {
                        uiInteraction.runAlarm(
                            result.comment,
                            rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error),
                            app.aaps.core.ui.R.raw.boluserror
                        )
                    }
                }
            })
            sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusereconnect, true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
    }
}
