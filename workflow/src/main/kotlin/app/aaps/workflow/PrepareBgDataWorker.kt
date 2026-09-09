package app.aaps.workflow

import android.content.Context
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.main.graph.OverviewData
import app.aaps.core.main.graph.data.DataPointWithLabelInterface
import app.aaps.core.main.graph.data.GlucoseValueDataPoint
import app.aaps.core.main.graph.data.PointsWithLabelGraphSeries
import app.aaps.core.main.utils.worker.LoggingWorker
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.database.impl.AppRepository
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject

class PrepareBgDataWorker(
    context: Context,
    params: WorkerParameters
) : LoggingWorker(context, params, Dispatchers.Default) {

    @Inject lateinit var dataWorkerStorage: DataWorkerStorage
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var repository: AppRepository

    class PrepareBgData(
        val iobCobCalculator: IobCobCalculator,
        val overviewData: OverviewData
    )

    override suspend fun doWorkAndLog(): Result {

        val data = dataWorkerStorage.pickupObject(inputData.getLong(DataWorkerStorage.STORE_KEY, -1)) as PrepareBgData?
            ?: return Result.failure(workDataOf("Error" to "missing input data"))

        val toTime = data.overviewData.toTime
        val fromTime = data.overviewData.fromTime

        // ✅ Inicialização correta Tarciso
        data.overviewData.maxBgValue = -Double.MAX_VALUE
        data.overviewData.minBgValue = Double.MAX_VALUE  // ← Inicializar a NOVA propriedade

        data.overviewData.bgReadingsArray = repository.compatGetBgReadingsDataFromTime(fromTime, toTime, false).blockingGet()

        val bgListArray: MutableList<DataPointWithLabelInterface> = ArrayList()
        var maxBgValueMgdl = -Double.MAX_VALUE
        var minBgValueMgdl = Double.MAX_VALUE  // ← Variável temporária para mínimo

        for (bg in data.overviewData.bgReadingsArray) {
            if (bg.timestamp < fromTime || bg.timestamp > toTime) continue

            // ✅ Calcular máximo e mínimo em mg/dL
            if (bg.value > maxBgValueMgdl) maxBgValueMgdl = bg.value
            if (bg.value < minBgValueMgdl) minBgValueMgdl = bg.value

            bgListArray.add(GlucoseValueDataPoint(bg, profileUtil, rh))
        }

        bgListArray.sortWith { o1: DataPointWithLabelInterface, o2: DataPointWithLabelInterface -> o1.x.compareTo(o2.x) }
        data.overviewData.bgReadingGraphSeries = PointsWithLabelGraphSeries(Array(bgListArray.size) { i -> bgListArray[i] })

        // ✅ Converter para unidades do usuário
        data.overviewData.maxBgValue = profileUtil.fromMgdlToUnits(maxBgValueMgdl)
        data.overviewData.minBgValue = profileUtil.fromMgdlToUnits(minBgValueMgdl)  // ← Armazenar mínimo

        // ✅ Garantir valores razoáveis
        if (data.overviewData.maxBgValue.isNaN() || data.overviewData.maxBgValue < defaultValueHelper.determineHighLine()) {
            data.overviewData.maxBgValue = defaultValueHelper.determineHighLine()
        }

        if (data.overviewData.minBgValue.isNaN() || data.overviewData.minBgValue > defaultValueHelper.determineLowLine()) {
            data.overviewData.minBgValue = defaultValueHelper.determineLowLine()
        }

        // ✅ Aplicar margens
        data.overviewData.maxBgValue = addUpperChartMargin(data.overviewData.maxBgValue)
        data.overviewData.minBgValue = addLowerChartMargin(data.overviewData.minBgValue)

        return Result.success()
    }

    private fun addUpperChartMargin(maxBgValue: Double): Double {
        return if (profileUtil.units == GlucoseUnit.MGDL) {
            val margin = maxOf(20.0, maxBgValue * 0.1)
            Round.roundTo(maxBgValue + margin, 20.0)
        } else {
            val margin = maxOf(1.0, maxBgValue * 0.1)
            Round.roundTo(maxBgValue + margin, 1.0)
        }
    }

    private fun addLowerChartMargin(minBgValue: Double): Double {
        return if (profileUtil.units == GlucoseUnit.MGDL) {
            val margin = maxOf(20.0, minBgValue * 0.2)
            val calculatedMin = minBgValue - margin
            val safeMinimum = 40.0

            if (calculatedMin < safeMinimum) {
                if (minBgValue > 80) minBgValue - 30.0 else safeMinimum
            } else {
                Round.roundTo(calculatedMin, 20.0)
            }
        } else {
            val margin = maxOf(1.0, minBgValue * 0.2)
            val calculatedMin = minBgValue - margin
            val safeMinimum = 2.2

            if (calculatedMin < safeMinimum) {
                if (minBgValue > 4.5) minBgValue - 1.7 else safeMinimum
            } else {
                Round.roundTo(calculatedMin, 1.0)
            }
        }
    }
}