package app.aaps.plugins.aps.openAPSAIMI.pkpd

/**
 * PkPdRuntime — Estado consolidado do modelo PK/PD para um ciclo.
 *
 * Instância única, recriada a cada ciclo APS (~5min) via PkPdIntegration.
 * Contém o ISF fundido (perfil + TDD), DIA aprendido do histórico real,
 * tempo de pico, e o estado atual da ação da insulina.
 */

/** Estados possíveis da ação da insulina no momento atual */
enum class InsulinActionState {
    /** Insulina ainda não atingiu o pico (<30% do tempo até peak) */
    PRE_ACTIVATION,
    /** Insulina próxima ao pico (30–70% do caminho) */
    PEAK,
    /** Insulina na cauda da ação (>70% do caminho) */
    TAIL,
    /** Apenas residual (<10% da atividade máxima) */
    RESIDUAL,
    /** Nenhuma insulina ativa relevante */
    INACTIVE
}

/** Parâmetros internos do modelo PK/PD */
data class PkPdParams(
    /** Taxa de absorção estimada (U/h) */
    val absorptionRate: Double,
    /** Duração restante estimada (minutos) */
    val remainingDuration: Double,
    /** Progresso em relação ao pico (0.0 = início, 1.0 = pico, >1.0 = pós-pico) */
    val peakProgress: Double
)

/**
 * Estado completo do runtime PK/PD.
 *
 * @property fusedIsf ISF fundido (perfil × TDD), em mg/dL/U
 * @property diaHrs Duração da insulina ativa (DIA) aprendida, em horas
 * @property peakMin Tempo de pico aprendido, em minutos
 * @property iobActivityNow Atividade IOB atual (mg/dL/min)
 * @property insulinActionState Estado da ação da insulina
 * @property params Parâmetros internos do modelo
 * @property computedAt Timestamp do cálculo (epoch ms)
 */
data class PkPdRuntime(
    val fusedIsf: Double,
    val diaHrs: Double,
    val peakMin: Double,
    val iobActivityNow: Double,
    val insulinActionState: InsulinActionState,
    val params: PkPdParams,
    val computedAt: Long
)
