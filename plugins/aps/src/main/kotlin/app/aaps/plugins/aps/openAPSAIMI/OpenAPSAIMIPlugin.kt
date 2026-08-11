package app.aaps.plugins.aps.openAPSAIMI
import android.content.Context
import app.aaps.annotations.OpenForTesting
import app.aaps.core.interfaces.aps.DetermineBasalAdapter
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profiling.Profiler
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.stats.TddCalculator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.notifications.Notification
import app.aaps.core.main.events.EventNewNotification
import app.aaps.database.impl.AppRepository
import app.aaps.plugins.aps.R
import app.aaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Singleton
@OpenForTesting
@Singleton
class OpenAPSAIMIPlugin  @Inject constructor(

    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    constraintChecker: ConstraintsChecker,
    rh: ResourceHelper,
    profileFunction: ProfileFunction,
    context: Context,
    activePlugin: ActivePlugin,
    iobCobCalculator: IobCobCalculator,
    hardLimits: HardLimits,
    profiler: Profiler,
    private val sp: SP,
    dateUtil: DateUtil,
    repository: AppRepository,
    glucoseStatusProvider: GlucoseStatusProvider,
    bgQualityCheck: BgQualityCheck,
    tddCalculator: TddCalculator,
    private val hourlyAdjustWorker: HourlyAdjustWorker
    ) : OpenAPSSMBPlugin(
    injector,
    aapsLogger,
    rxBus,
    constraintChecker,
    rh,
    profileFunction,
    context,
    activePlugin,
    iobCobCalculator,
    hardLimits,
    profiler,
    sp,
    dateUtil,
    repository,
    glucoseStatusProvider,
    bgQualityCheck,
    tddCalculator
    ) {

        companion object {
            private const val SP_LAST_AUTO_ADJUST_TIME = "key_aimi_last_auto_adjust_time"
        }

        /** Timestamp (ms) da última execução do auto-adjust — persistido em SharedPreferences. */
        private var lastAutoAdjustTime: Long
            get() = sp.getLong(SP_LAST_AUTO_ADJUST_TIME, 0L)
            set(value) = sp.putLong(SP_LAST_AUTO_ADJUST_TIME, value)

        init {
            pluginDescription
                .pluginName(R.string.openapsaimi)
                .description(R.string.description_openapsaimi)
                .shortName(R.string.oaps_aimi_shortname)
                .preferencesId(R.xml.pref_openapsaimi)
                .setDefault(false)
        }

        override fun provideDetermineBasalAdapter(): DetermineBasalAdapter = DetermineBasalAdapterAIMI(injector)

        override fun invoke(initiator: String, tempBasalFallback: Boolean) {
            super.invoke(initiator, tempBasalFallback)

            // Inicializa CSV de magnitude se toggle ON (idempotente, ~5 min/ciclo)
            hourlyAdjustWorker.initMagnitudeLog()

            // Auto-Adjust: guard único de tempo com persistência em SharedPreferences.
            // O timestamp da última execução sobrevive a restarts de processo,
            // garantindo o intervalo de ~4h mesmo se o app for morto e recriado.
            if (hourlyAdjustWorker.isEnabled() &&
                System.currentTimeMillis() - lastAutoAdjustTime >= hourlyAdjustWorker.getAnalysisInterval()) {
                val notificationText = hourlyAdjustWorker.runAnalysis()
                if (notificationText != null) {
                    rxBus.send(EventNewNotification(
                        Notification(
                            id = 90,
                            text = notificationText,
                            level = Notification.INFO,
                            validMinutes = 120
                        )))
                }
                lastAutoAdjustTime = System.currentTimeMillis()
            }
        }

        override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
            super.preprocessPreferences(preferenceFragment)
            val logPref = preferenceFragment.findPreference<Preference>("key_aimi_auto_adjust_log")
            logPref?.summary = hourlyAdjustWorker.getLastReportSummary()
        }
    }
