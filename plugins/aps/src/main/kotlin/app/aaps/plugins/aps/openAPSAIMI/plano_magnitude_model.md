# Plano: Modelo de Magnitude para Auto-Adjust Hourly Factors

## Problema

Hoje o `HourlyAdjustWorker` usa ajuste fixo de **±10%** por evento. Um evento de hipo branda (BG=64) recebe o mesmo ajuste que um evento grave (BG=40). Um evento em horário de alta TDD (basal mais agressivo) também. Isso é subótimo.

## Ideia

Treinar um **regressor linear online** que aprende a magnitude do ajuste baseado em features do contexto. Não substitui a regra de *direção* (sobe se hipo, desce se hiper) — só substitui o `ADJUST_STEP` fixo por um valor aprendido.

## Arquitetura

```
features → [regressor linear SGD] → magnitude_multiplier (ex: 0.6, 1.2, 1.8)
                                            ↓
                                    base_step (±10) × multiplier
                                            ↓
                                    adjusted_step (ex: ±6, ±12, ±18)
```

O modelo é armazenado como **6-8 floats** em SP (pesos + bias + contador). Nada de TF Lite, nenhuma dependência externa. Treinamento via **SGD online** (uma atualização por evento).

---

## Fase 1 — Coleta de dados (só logging, sem modelo)

**Objetivo:** Instrumentar o `HourlyAdjustWorker` para registrar cada ajuste com contexto, permitindo análise posterior.

### O que fazer

1. Criar estrutura `MagnitudeRecord` no `HourlyAdjustWorker.kt`:
```kotlin
data class MagnitudeRecord(
    val timestamp: Long,
    val hourOfDay: Int,
    val bgDeviation: Double,     // |BG - target| / target
    val delta: Double,            // BG rate of change
    val eventType: String,        // "HYPO" ou "HYPER"
    val eventCount: Int,          // total de eventos para esta hora
    val tddPerHour: Double,       // TDD/hora no momento
    val rawDelta: Double,         // soma dos ±10 antes do confidence
    val effectiveDelta: Double,   // após confidence factor
    val cappedDelta: Double,      // após limite ±30
    val newValue: Int,            // valor final escrito na SP
    val outcome: Double = 0.0     // placeholder — preenchido na Fase 2
)
```

2. No `applyDecayAndAdjustments()`, após calcular `cappedDelta`, criar um `MagnitudeRecord` e adicionar a uma fila em memória (`mutableListOf`).

3. Ao final de `runAnalysis()`, **persistir os registros** em um formato consultável:
   - Opção A: CSV no diretório externo (mesmo padrão do `oapsaimi_records.csv`)
   - Opção B: SP JSON string (limitado a ~100 registros rotativos)

   Recomendo **Opção A** (CSV) — já existe infraestrutura de logging no projeto.

4. Adicionar chave SP `key_aimi_magnitude_collect` (toggle booleano, default `false`) para ativar/desativar a coleta.

### Arquivos a modificar

| Arquivo | Mudança |
|---|---|
| `HourlyAdjustWorker.kt` | Coleta + persistência CSV |
| `strings.xml` | `key_aimi_magnitude_collect` |
| `pref_openapsaimi.xml` | SwitchPreference (opcional, debug) |

### Critério de conclusão

Após 3-4 dias de coleta com toggle ON, o CSV contém registros de ajustes com contexto — base para análise da Fase 2.

---

## Fase 2 — Análise e definição do alvo (ground truth)

**Objetivo:** Determinar, para cada ajuste, se ele foi "bom" ou "ruim" — e qual seria a magnitude ideal.

### O problema do ground truth

Diferente do SMB (feedback em minutos), o hourly factor tem feedback **lento e confundido**:
- Um ajuste na hora 10h pode afetar BG às 12h
- Mas refeição, exercício e IOB também afetam

### Abordagem proposta: janela de observação de 24h

Para cada `MagnitudeRecord`:
1. Aguardar 24h (próximo ciclo da mesma hora)
2. Comparar número de eventos hipo/hiper na mesma hora **antes vs. depois** do ajuste
3. Calcular `outcome`:
   ```
   se eventos_diminuíram → magnitude foi boa (+)
   se eventos_aumentaram → magnitude foi exagerada/insuficiente (-)
   ```

**Sinal de treino:**
```kotlin
val eventDelta = eventsBefore - eventsAfter
// Se eventDelta > 0 (eventos diminuíram): treinar para manter ou aumentar magnitude
// Se eventDelta < 0 (eventos aumentaram): treinar para reduzir magnitude
```

### Feature engineering

Features normalizadas para entrada do regressor:

| Feature | Fonte | Range | Descrição |
|---|---|---|---|
| `bgDeviation` | BG atual - target | [0, 3] | |BG − target| / target |
| `delta` | BG rate of change | [−2, 2] | clampado |
| `eventType` | hipo ou hiper | {−1, 1} | 1 = hipo, −1 = hiper |
| `confidence` | eventCount / 5 | [0.2, 1] | quanto mais eventos, mais confiança |
| `tddNorm` | TDD/h / 2.0 | [0, 2] | TDD por hora normalizado |
| `hourSin` | hora do dia | [−1, 1] | sin(hour × 2π / 24) |
| `hourCos` | hora do dia | [−1, 1] | cos(hour × 2π / 24) |

### Critério de conclusão

Dataset de ~50-100 registros com `outcome` preenchido em CSV, permitindo inspeção manual e validação da consistência do sinal.

---

## Fase 3 — Implementação do regressor

**Objetivo:** Substituir o `ADJUST_STEP` fixo por um valor predito pelo modelo.

### Arquitetura do modelo

```kotlin
class MagnitudeRegressor(
    private val sp: SP
) {
    // Persistência: 7 pesos + 1 bias + 1 contador = 9 floats em SP
    // Chaves: key_aimi_mag_w0..w6, key_aimi_mag_bias, key_aimi_mag_count
    
    companion object {
        const val MIN_SAMPLES = 15   // mínimo para ativar predição
        const val LR = 0.01          // learning rate SGD
        const val FALLBACK = 1.0     // multiplier = 1.0 = ADJUST_STEP puro
    }
    
    data class Features(
        val bgDeviation: Double,
        val delta: Double,
        val eventType: Double,
        val confidence: Double,
        val tddNorm: Double,
        val hourSin: Double,
        val hourCos: Double
    )
    
    fun predict(features: Features): Double {
        if (sampleCount < MIN_SAMPLES) return FALLBACK
        val raw = weights.zip(features.toList())
            .sumOf { (w, f) -> w * f } + bias
        return raw.coerceIn(0.3, 2.5)  // clamp seguro
    }
    
    fun train(features: Features, targetMagnitude: Double) {
        val prediction = predictUnclamped(features)
        val error = prediction - targetMagnitude
        // SGD update: w = w - lr * error * feature
        weights = weights.mapIndexed { i, w ->
            w - LR * error * features.toList()[i]
        }
        bias = bias - LR * error
        saveToSp()
    }
}
```

### Fluxo de treino

```
Ciclo N (execução):
  1. Ajuste ocorre com magnitude = predict(features) * ADJUST_STEP
  2. Salva features + magnitude como "pendente de outcome"

24h depois (mesma hora, ciclo N+6):
  3. Compara eventos antes/depois
  4. Calcula targetMagnitude
  5. train(featuresPendentes, targetMagnitude)
  6. Libera registro pendente
```

### Integração no HourlyAdjustWorker

Substituir trecho atual:

```kotlin
// ANTIGO: fixo
val rawDelta = hypoDelta + hyperDelta
val effectiveDelta = rawDelta * confidenceFactor
val cappedDelta = effectiveDelta.coerceIn(-MAX_ADJUST_PER_HOUR, MAX_ADJUST_PER_HOUR)
```

```kotlin
// NOVO: magnitude aprendida
val magnitude = magnitudeRegressor.predict(features)  // fallback = 1.0
val rawDelta = (hypoDelta + hyperDelta) * magnitude
val effectiveDelta = rawDelta * confidenceFactor
val cappedDelta = effectiveDelta.coerceIn(-MAX_ADJUST_PER_HOUR, MAX_ADJUST_PER_HOUR)
```

### Segurança

- **Fallback:** se `sampleCount < MIN_SAMPLES`, `magnitude = 1.0`
- **Clamp:** saída sempre em `[0.3, 2.5]` — nunca menos de 30% nem mais de 250% do passo base
- **Circuit breaker:** se 3 predições consecutivas resultarem em piora do BG, resetar pesos
- **Toggle:** `key_aimi_magnitude_model` (SwitchPreference, default `false`)

### Arquivos a modificar/criar

| Arquivo | Tipo | Mudança |
|---|---|---|
| `HourlyMagnitudeRegressor.kt` | **CRIAR** | Modelo + persistência (120 linhas) |
| `HourlyAdjustWorker.kt` | MODIFICAR | Injetar regressor, substituir cálculo |
| `OpenAPSAIMIPlugin.kt` | MODIFICAR | Injetar regressor no worker |
| `strings.xml` | MODIFICAR | 12 novas chaves (pesos + toggle) |
| `pref_openapsaimi.xml` | MODIFICAR | SwitchPreference |

### Critério de conclusão

- Regressor treinado com 15+ amostras
- `magnitude` varia entre 0.3 e 2.5 baseado em contexto
- Fallback silencioso quando dados insuficientes
- Build limpo, verificação 15/15 checks

---

## Diagrama de fluxo completo

```
┌─────────────────────────────┐
│  Fase 1: Coleta (3-4 dias)  │
│  CSV com contexto           │
└──────────┬──────────────────┘
           ↓
┌─────────────────────────────┐
│  Fase 2: Análise (1-2 dias) │
│  Ground truth via outcome   │
│  Validação do sinal         │
└──────────┬──────────────────┘
           ↓
┌─────────────────────────────┐
│  Fase 3: Implementação      │
│  MagnitudeRegressor.kt      │
│  Integração no worker       │
│  Toggle + fallback          │
└─────────────────────────────┘
           ↓
      Operação normal
      (melhoria contínua)
```

---

## Riscos e mitigação

| Risco | Impacto | Mitigação |
|---|---|---|
| Sinal de treino muito ruidoso | Modelo não converge | Feature engineering mais cuidadosa; validação manual na Fase 2 |
| Overfitting com poucos dados | Predições ruins | Clamp [0.3, 2.5]; MIN_SAMPLES=15; fallback 1.0 |
| Regressor linear é limitado | Performance subótima | Se dados mostrarem padrão não-linear, trocar para regressor polinomial (mesma infra, +1 feature) |
| CSV ocupa muito espaço | Armazenamento | Rotação automática (manter últimos 100 registros) |
