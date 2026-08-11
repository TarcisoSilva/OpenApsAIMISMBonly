# Análise de Bugs e Melhorias — DetermineBasalAdapterAIMI.kt

**Data:** 30/Jul/2026  
**Arquivo:** `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAdapterAIMI.kt`  
**Linhas totais:** 2315

---

## 🔴 BUGS CRÍTICOS

### Bug #1 — Guarda IOB incompleta no Override 1 (predictFutureBg)
**Localização:** Linha 1217  
**Severidade:** ALTA — Já corrigido conforme memória (Correção #6)

```kotlin
// ATUAL (corrigido):
if (bg <= 155 && delta > 0 && iob < 2.0) {
    futureBg = targetBg + 2
}
```

**Status:** ✅ Corrigido em 30/Jul/2026

---

### Bug #2 — Override 2 sem guarda IOB (predictFutureBg)
**Localização:** Linha 1221-1223  
**Severidade:** ALTA — Pode mascarar quedas com IOB alto

```kotlin
// PROBLEMA:
if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
}
```

**Análise:**  
- Condição `iob <= 0.8` é muito restritiva — só ativa com IOB baixíssimo
- Mas **não protege o caso contrário**: se `iob > 0.8` E `delta > 0`, o override não aplica, mas pode haver IOB alto residual
- Similar ao Bug #6 corrigido: override desliga proteção quando BG está subindo COM IOB alto

**Impacto:**  
- BG 130, delta +2.0, IOB 2.5 → `futureBg` calculado via fórmula padrão (linha 1187) pode subestimar risco
- Sistema pode dar SMB mesmo com IOB alto + BG subindo

**Correção sugerida:**
```kotlin
// Adicionar guarda IOB alta para evitar override quando há risco:
if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
} else if (iob > 2.0 && delta > 0 && bg < 155) {
    // Não sobrescrever: usar fórmula padrão para capturar risco de queda
    // (deixa o cálculo da linha 1187 intacto)
}
```

**Alternativa mais simples (defesa em profundidade):**
```kotlin
// Adicionar guarda superior no override:
if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
}
// Adicionar nova condição APÓS a linha 1223:
if (iob > 2.0 && delta > 0 && bg <= 155) {
    // Força proteção: não deixar futureBg mascarar IOB alto
    futureBg = (bg + (delta * 1.5)) - ((iob / 2) * variableSensitivity)
}
```

---

### Bug #3 — Fórmula DIA adaptativo com `insulin.peak > 45` jamais ativa
**Localização:** Linhas 1545-1549  
**Severidade:** MÉDIA — Lógica condicional quebrada

```kotlin
val baseDivisor = when {
    insulin.peak >= 35 -> 55  // ✅ Sempre ativa se peak >= 35
    insulin.peak > 45  -> 65  // ❌ NUNCA ATIVA (peak >= 35 já capturou)
    else               -> 75
}
```

**Análise:**  
- Primeira condição `peak >= 35` captura **todos** os valores ≥ 35, incluindo > 45
- Segunda condição `peak > 45` é **código morto** — nunca será avaliada
- Provavelmente a intenção era:
  - `peak < 35` → 75 (ultra-rápido)
  - `peak 35-45` → 55 (rápido)
  - `peak > 45` → 65 (lento)

**Correção:**
```kotlin
val baseDivisor = when {
    insulin.peak > 45  -> 65  // Lento (ex: Humalog, Novolog)
    insulin.peak >= 35 -> 55  // Rápido (ex: Fiasp)
    else               -> 75  // Ultra-rápido (ex: Lyumjev)
}
```

**Impacto clínico:**  
- Insulinas lentas (peak > 45) estão sendo tratadas como rápidas (divisor 55 em vez de 65)
- DIA calculado é ~15% mais curto que deveria
- IOB subestimado → risco de stacking de SMBs

---

### Bug #4 — `iobArray` nunca populado (usado pela Fase 1 PK/PD)
**Localização:** Linha 190 (declaração) + Linha 308 (uso)  
**Severidade:** ALTA — Fase 1 PK/PD recebe lista vazia

```kotlin
// Linha 190:
private var iobArray: List<IobTotal>? = null

// Linha 308 (invoke):
val iobValues: List<Double> = iobArray?.map { it.iob.toDouble() } ?: emptyList()
```

**Análise:**  
- `iobArray` é declarado mas **nunca preenchido** em `setData()`
- `setData()` recebe `iobArray: Array<IobTotal>` como parâmetro (linha 1399) mas não atribui a `this.iobArray`
- PkPdIntegration.computeRuntime() recebe `iobValues = emptyList()` → cálculo de DIA adaptativo falha

**Correção:**
```kotlin
// Adicionar em setData(), logo após linha 1433:
this.iobArray = iobArray.toList()  // Converte Array → List
```

**Impacto:**  
- DIA estimation na Fase 1 não funciona corretamente
- `fusedIsf` calculado sem histórico de IOB → menos preciso

---

## 🟡 BUGS MENORES

### Bug #5 — Variável `stable2` usada mas nunca modificada no ciclo
**Localização:** Linha 113 (declaração) + Linha 1479 (cálculo) + Linha 2013 (uso)  
**Severidade:** BAIXA — Comportamento correto mas ineficiente

```kotlin
// Linha 113: declaração de classe
private var stable2: Int = 0

// Linha 1479: calculado em setData()
this.stable2 = if (delta>-1.5 && delta<1.5 && shortAvgDelta>-1.0 && shortAvgDelta<1.0 && longAvgDelta>-0.8 && longAvgDelta<0.8) 1 else 0

// Linha 2013: usado em invoke() → applyIncrementAdjustment()
stable2 == 1 && bg > 145 && delta >= 0.5 -> aggressiveFactor
```

**Análise:**  
- `stable2` é calculado uma vez em `setData()` e **não muda durante invoke()**
- Mas é tratado como variável de classe (mutable)
- Não é um bug funcional, mas é inconsistente com a documentação (AGENTS.md diz "todas as variáveis são recalculadas do zero")

**Melhoria sugerida:**  
- Mover cálculo de `stable2` para uma função auxiliar chamada em `invoke()` se precisar refletir mudanças
- OU documentar que `stable2` é calculado apenas em `setData()` e permanece fixo no ciclo

**Impacto:** Nenhum — comportamento atual está correto, apenas clareza de código

---

### Bug #6 — Cálculo de `nightMultiplier` pode ter lógica de overlap
**Localização:** Linhas 2208-2220 (determineMaxIOBandMaxSMB)  
**Severidade:** BAIXA — Possível sobreposição de condições

```kotlin
val nightMultiplier = when {
    bgAltoUltimas4h && iob < 1.0 && bg in 90.0..130.0 && delta < 1.5 -> 0.4  // Cond1
    delta > 1.5 && bg > 130.0 -> 1.0                                          // Cond2
    bg in 90.0..130.0 && delta in -1.0..1.0 -> 0.7                            // Cond3
    else -> 1.0
}
```

**Análise:**  
- **Cond1** e **Cond3** têm overlap: `bg in 90.0..130.0` aparece em ambas
- Se `bgAltoUltimas4h=true`, `iob=0.8`, `bg=110`, `delta=0.5`:
  - Cond1: ✅ todas condições atendem → 0.4
- Se `bgAltoUltimas4h=false`, `iob=0.8`, `bg=110`, `delta=0.5`:
  - Cond3: ✅ todas condições atendem → 0.7
- **Cond2** vs **Cond3**: se `bg=125`, `delta=0.5` → Cond3 ativa (0.7), mas se `delta=1.6` → Cond2 ativa (1.0)

**Verificação:**  
- Ordem das condições está correta (mais específica → mais genérica)
- Não há bug, mas pode ser confuso

**Melhoria (opcional):**  
Adicionar comentário explicando a precedência:
```kotlin
val nightMultiplier = when {
    // Prioridade 1: Pós-hiper noturno com IOB baixo (mais restritivo)
    bgAltoUltimas4h && iob < 1.0 && bg in 90.0..130.0 && delta < 1.5 -> 0.4
    // Prioridade 2: Noturno subindo (sem corte, precisa insulina)
    delta > 1.5 && bg > 130.0 -> 1.0
    // Prioridade 3: Noturno estável (redução moderada)
    bg in 90.0..130.0 && delta in -1.0..1.0 -> 0.7
    else -> 1.0
}
```

---

## 🟢 MELHORIAS DE CÓDIGO

### Melhoria #1 — Extração de funções auxiliares para guards complexos
**Localização:** Linhas 786-846 (isCriticalSafetyCondition)  
**Prioridade:** MÉDIA — Melhora legibilidade

**Problema:**  
- Função `isCriticalSafetyCondition()` tem 10+ condições booleanas inline
- Difícil debugar qual guard ativou
- Nomes das variáveis são descritivos, mas não há logging

**Sugestão:**
```kotlin
private fun isCriticalSafetyCondition(): Boolean {
    val guards = listOf(
        "belowMinThreshold" to (bg < 80),
        "belowTargetAndDropping" to (bg < targetBg && delta < -2),
        "droppingFast" to (bg < 150 && delta < -5),
        "nightTrigger" to (LocalTime.now().run { (hour in NIGHT_HOUR_START..23 || hour in 0..NIGHT_HOUR_END) } && delta > 15 && cob == 0.0),
        "slowDeclineNoturno" to (delta < 0 && shortAvgDelta < 0 && iob < 1.0 && bgAltoUltimas4h && isNight),
        "hypoRecovery" to (lowestBgUltimaHora <= 70.0 && bg < 96 && delta > 0 && iob <= 0.9 && isNight),
        "smbExhaustion" to (smbTotalUltimas4h >= 4.0 && delta < 0 && shortAvgDelta <= 0 && isNight)
        // ... adicionar todas
    )
    
    val activeGuards = guards.filter { it.second }
    if (activeGuards.isNotEmpty()) {
        val guardNames = activeGuards.joinToString(", ") { it.first }
        aapsLogger.debug(LTag.APS, "🛑 Safety guards active: $guardNames")
        return true
    }
    return false
}
```

**Benefício:**  
- Log mostra **qual** guard bloqueou o SMB
- Facilita análise de eventos via Nightscout

---

### Melhoria #2 — Consolidação de magic numbers em constantes
**Localização:** Diversas (ex: linhas 679, 689, 699, 820, 905, 938, etc.)  
**Prioridade:** BAIXA — Manutenibilidade

**Problema:**  
- Thresholds hardcoded espalhados pelo código:
  - `3.0` (threshold SMB Cumulative Brake, linha 679)
  - `0.3` (threshold hora ativa, linha 697)
  - `4.0` (threshold SMB Exhaustion, linha 820)
  - `0.5f`, `0.7f`, `1.2f` (fatores Rate Limiter, linhas 905-973)

**Sugestão:**  
Adicionar ao companion object (linha 2230+):
```kotlin
// SMB Cumulative Brake thresholds
private const val SMB_CUMULATIVE_THRESHOLD = 3.0      // U em 4h
private const val SMB_ACTIVE_HOUR_THRESHOLD = 0.3     // U/hora para considerar ativa
private const val SMB_EXHAUSTION_THRESHOLD = 4.0      // U em 4h para exhaustion guard

// Rate Limiter Noturno tiers
private const val NIGHT_BG_LOW_TIER = 95.0
private const val NIGHT_BG_MID_TIER = 120.0
private const val NIGHT_BG_HIGH_TIER = 140.0
private const val NIGHT_DELTA_MODEST = 1.0
private const val NIGHT_DELTA_MODERATE = 2.5

private const val NIGHT_SMB_FLOOR = 0.1f              // BG baixo plano
private const val NIGHT_SMB_STABLE = 0.7f             // Estável
private const val NIGHT_SMB_MODEST = 0.5f             // Subida modesta
private const val NIGHT_SMB_STRONG = 1.2f             // Subida forte
```

**Benefício:**  
- Centraliza configuração
- Facilita ajustes futuros (ex: usuário pedindo "noturno mais/menos agressivo")

**⚠️ NOTA:** Você mencionou na memória que **NÃO quer** refatoração de magic numbers por considerar cosmético e arriscado. Esta sugestão é **opcional** e só deve ser feita se houver necessidade clara de ajuste frequente desses valores.

---

### Melhoria #3 — Usar `require()` / `check()` para invariantes
**Localização:** Linhas 231-233 (invoke), 1426-1431 (setData)  
**Prioridade:** BAIXA — Fail-fast em vez de fallback silencioso

**Problema atual:**
```kotlin
// invoke() linha 231-233:
if (this.variableSensitivity <= 0.0) {
    this.variableSensitivity = profileFunction.getProfile()?.getIsfMgdl()?.toDouble() ?: 45.0
}

// setData() linha 1426-1431:
if (this.variableSensitivity <= 0.0) {
    this.variableSensitivity = profile.getIsfMgdl().toDouble()
}
```

**Análise:**  
- Guards silenciosos ocultam bugs de estado
- Se `variableSensitivity` é 0.0, significa que `setData()` não foi chamada ou falhou
- Fallback para ISF de perfil pode mascarar problema real

**Sugestão (modo DEBUG apenas):**
```kotlin
// invoke():
if (BuildConfig.DEBUG) {
    check(this.variableSensitivity > 0.0) {
        "variableSensitivity not initialized — setData() was not called or failed"
    }
} else {
    // Fallback em produção
    if (this.variableSensitivity <= 0.0) {
        this.variableSensitivity = profileFunction.getProfile()?.getIsfMgdl()?.toDouble() ?: 45.0
        aapsLogger.warn(LTag.APS, "variableSensitivity was 0.0, using profile ISF fallback")
    }
}
```

**Benefício:**  
- Desenvolvimento: falha rápido se há bug de inicialização
- Produção: mantém fallback seguro mas loga o problema

---

### Melhoria #4 — Adicionar timestamp ao DigestionDetector para prevenir state leak
**Localização:** Linha 194 (digestionDetector) + Linha 329-332 (uso)  
**Prioridade:** MÉDIA — Prevenir state leak entre sessões

**Problema:**  
- `DigestionDetector` é instância de classe (linha 194: `private val digestionDetector = DigestionDetector()`)
- Se a instância de `DetermineBasalAdapterAIMI` for reutilizada pelo DI (Dagger), o `digestionDetector` mantém estado entre ciclos
- Documentação (AGENTS.md) diz "não há persistência de estado entre ciclos"

**Verificação necessária:**  
Checar no `DigestionDetector.kt` se ele tem:
- Timestamp de última atualização
- Lógica de timeout para resetar estado

**Sugestão:**
```kotlin
// Em DigestionDetector.kt (não vimos o código, mas sugerimos):
class DigestionDetector {
    private var lastUpdateMillis = 0L
    private val STALE_THRESHOLD_MS = 10 * 60 * 1000L  // 10 min
    
    fun update(bg: Double, delta: Double, shortAvgDelta: Double, 
               lastSmbMinutes: Int, now: Long) {
        // Resetar estado se passou muito tempo desde última atualização
        if (now - lastUpdateMillis > STALE_THRESHOLD_MS) {
            reset()
        }
        lastUpdateMillis = now
        // ... resto da lógica
    }
    
    fun isActive(): Boolean {
        // Verificar se estado é recente
        if (System.currentTimeMillis() - lastUpdateMillis > STALE_THRESHOLD_MS) {
            return false  // Estado stale, não confiar
        }
        return _isActive
    }
}
```

**Impacto se não corrigir:**  
- Detecção de digestão pode "vazar" entre sessões separadas por horas
- Pouco provável em uso real (ciclos APS são a cada 5 min), mas possível em testes

---

### Melhoria #5 — Adicionar telemetria de performance para bottlenecks
**Localização:** invoke() e setData()  
**Prioridade:** BAIXA — Debugging / Otimização futura

**Sugestão:**
```kotlin
override operator fun invoke(): APSResultObject {
    val startTime = System.nanoTime()
    aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal <<<")
    
    // ... todo o código existente ...
    
    val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
    aapsLogger.debug(LTag.APS, "<<< determine_basal completed in ${elapsedMs}ms >>>")
    return determineBasalResultAIMISMB
}
```

**Benefício:**  
- Identificar se alguma query ao repository está lenta
- Detectar regressões de performance em versões futuras

---

## 📊 ANÁLISE DE RISCO — GUARDS DEFENSIVOS

### ✅ Guards bem implementados

1. **SMB Cumulative Brake (linhas 657-718)**  
   - ✅ Extended Morning Window (4-10h)
   - ✅ Proportional Distribution Brake
   - ✅ Try-catch para segurança

2. **Rate Limiter Contextual Noturno (linhas 899-978)**  
   - ✅ Severity Tiers por BG
   - ✅ bgAltoRatio para prolonged elevation
   - ✅ Else catch-all conservador (linha 976)

3. **Hypo Recovery Guard (linhas 823-837)**  
   - ✅ Usa `lowestBgUltimaHora` (query direta) em vez de alarmes
   - ✅ Threshold ≤70 bem calibrado

4. **P1 Near-Miss + Bounce (linhas 864-893)**  
   - ✅ Soft guards com fator combinado (0.5×)
   - ✅ Evita double-penalty

---

### ⚠️ Guards que podem precisar validação

1. **Falling Trajectory Guard (linhas 808-810)**  
   ```kotlin
   val slowDeclineNoturno = delta < 0 && shortAvgDelta < 0 &&
       iob < 1.0 && bgAltoUltimas4h && isNight
   ```
   - **Questão:** IOB < 1.0 pode ser muito restritivo?
   - **Validar:** Checar eventos de hipo noturna onde `iob` estava entre 1.0-2.0
   - **Simulação necessária:** Dados NS de Jul/2026

2. **SMB Load Brake (linhas 989-1002)**  
   ```kotlin
   if (smbTotalUltimas4h >= 5.0) {
       val trajectoryConfiavel = delta > 0 && shortAvgDelta > 0 && longAvgDelta > 0
       if (!trajectoryConfiavel) { /* aplica brake */ }
   }
   ```
   - **Questão:** Threshold 5.0U pode ser baixo para perfil SMB-puro?
   - **Validar:** TDD diário do usuário (se TDD=35U/dia → 5U em 4h = 34% do TDD diário — alto)

---

## 🎯 PRIORIZAÇÃO DE CORREÇÕES

### Implementar AGORA (Risco Alto)

1. **Bug #2** — Override 2 sem guarda IOB  
   **Risco:** Hipo por SMB com IOB alto + BG subindo  
   **Esforço:** 5 linhas

2. **Bug #3** — DIA adaptativo com lógica quebrada  
   **Risco:** IOB subestimado → stacking  
   **Esforço:** 3 linhas

3. **Bug #4** — `iobArray` nunca populado  
   **Risco:** Fase 1 PK/PD não funciona corretamente  
   **Esforço:** 1 linha

---

### Implementar PRÓXIMO LOTE (Risco Médio)

4. **Melhoria #1** — Logging de guards ativos  
   **Benefício:** Debugging mais rápido  
   **Esforço:** 20 linhas

5. **Melhoria #4** — Timestamp no DigestionDetector  
   **Benefício:** Prevenir state leak  
   **Esforço:** Depende do código do `DigestionDetector.kt`

---

### Considerar FUTURO (Baixa Prioridade)

6. **Melhoria #2** — Constantes para magic numbers  
   **Nota:** Você mencionou que NÃO quer isso — manter como está

7. **Melhoria #3** — `require()` / `check()`  
   **Benefício:** Fail-fast em dev  
   **Esforço:** 10 linhas

8. **Melhoria #5** — Telemetria de performance  
   **Benefício:** Otimização futura  
   **Esforço:** 5 linhas

---

## 📝 NOTAS FINAIS

### Pontos fortes do código atual

1. ✅ **Documentação inline excelente** — Comentários explicam o "porquê" das decisões
2. ✅ **Guards defensivos bem pensados** — Múltiplas camadas de proteção contra hipo
3. ✅ **Logging abundante** — `aapsLogger.debug()` em pontos-chave
4. ✅ **Try-catch em I/O** — Repository queries protegidas
5. ✅ **Companion object com constantes** — Valores-chave já centralizados

### Áreas de atenção

1. ⚠️ **Complexidade ciclomática alta** — Função `invoke()` tem 100+ linhas, `setData()` tem 300+ linhas
2. ⚠️ **Estado mutável de classe** — Todas variáveis são `private var` (correto para este design, mas dificulta testes unitários)
3. ⚠️ **Dependência de timing** — Assume que `setData()` é sempre chamado antes de `invoke()` (correto pelo contrato da interface `DetermineBasalAdapter`, mas não há enforcement)

### Recomendações de teste

Antes de implementar correções, **simular contra dados NS reais**:

1. **Bug #2 (Override 2):** Buscar eventos onde:
   - `iob > 1.5`
   - `delta > 0`
   - `bg < 155`
   - `hour in 4..22`
   - Resultado: hipo nas 2h seguintes

2. **Bug #3 (DIA adaptativo):** Comparar IOB calculado vs IOB esperado para insulinas lentas

3. **Bug #4 (iobArray):** Verificar logs de `PkPdIntegration` — deve mostrar `iobValues=[]` (vazio)

---

## 🔧 PRÓXIMOS PASSOS

1. ✅ **Revisar este relatório** — Confirmar se a análise está alinhada com a sua experiência
2. ⚙️ **Priorizar correções** — Definir ordem de implementação (sugestão: Bugs #2, #3, #4)
3. 🧪 **Simular contra NS** — Validar correções em dados históricos antes de deploy
4. 📦 **Implementar em lote** — Correções 2-4 juntas (padrão que você prefere)
5. 📝 **Atualizar AGENTS.md** — Documentar mudanças

---

**FIM DO RELATÓRIO**
