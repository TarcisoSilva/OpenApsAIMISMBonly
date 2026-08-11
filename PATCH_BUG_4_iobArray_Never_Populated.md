# PATCH — Bug #4: iobArray nunca populado (Fase 1 PK/PD recebe lista vazia)

**Data:** 30/Jul/2026  
**Arquivo:** `DetermineBasalAdapterAIMI.kt`  
**Localização:** Linha 190 (declaração) + Linha 1399 (parâmetro) + Linha 308 (uso)  
**Severidade:** ALTA

---

## Problema

A variável `iobArray` é declarada mas **nunca populada**, causando falha silenciosa na Fase 1 PK/PD:

```kotlin
// Linha 190 (declaração de classe):
private var iobArray: List<IobTotal>? = null

// Linha 1399 (setData recebe como parâmetro):
override fun setData(
    profile: Profile,
    // ... outros parâmetros ...
    iobArray: Array<IobTotal>,  // ← Recebe array
    // ... outros parâmetros ...
) {
    // ❌ NUNCA ATRIBUI A this.iobArray
}

// Linha 308 (invoke usa):
val iobValues: List<Double> = iobArray?.map { it.iob.toDouble() } ?: emptyList()
// ❌ Sempre retorna emptyList()
```

**Fluxo:**
1. `setData()` recebe `iobArray: Array<IobTotal>` (populado pelo caller)
2. Mas **não atribui** a `this.iobArray`
3. `invoke()` usa `this.iobArray` → sempre `null` → fallback para `emptyList()`
4. `PkPdIntegration.computeRuntime()` recebe `iobValues = []`
5. DIA estimation falha → `fusedIsf` calculado sem histórico de IOB

---

## Impacto na Fase 1 PK/PD

### PkPdIntegration.computeRuntime() (linha 316-323)

```kotlin
val pkpdRuntimeTemp = PkPdIntegration.computeRuntime(
    epochMillis = dateUtil.now(), bg = bg,
    deltaMgDlPer5 = delta, iobU = iob,
    windowSinceLastDoseMin = lastBolusAgeMinutes,
    exerciseFlag = exerciseDetected,
    profileIsf = profileIsf, tdd24h = tdd24hTotal,
    iobValues = iobValues  // ← emptyList() sempre
)
```

**Dentro de PkPdIntegration.computeRuntime():**
```kotlin
// Esperado: calcular DIA adaptativo baseado em decaimento de IOB
val estimatedDia = if (iobValues.size >= 3) {
    // Ajusta exponencial aos pontos de IOB
    InsulinActionProfiler.estimateDia(iobValues)
} else {
    // Fallback: DIA fixo do perfil
    5.0  // ou outro valor padrão
}
```

**Com `iobValues = []`:**
- Sempre cai no fallback
- DIA estimation nunca funciona
- `fusedIsf` calculado com DIA fixo (menos preciso)

---

## Correção

Adicionar **uma linha** em `setData()` logo após a inicialização de `iob`:

```kotlin
// Linha 1433 (ATUAL):
this.iob = iobCalcs.iob + iobCalcs.basaliob.toFloat()

// ADICIONAR imediatamente após (nova linha 1434):
this.iobArray = iobArray.toList()  // Converte Array<IobTotal> → List<IobTotal>
```

**Por que `.toList()`?**
- Parâmetro é `Array<IobTotal>` (Java-style array mutável)
- Variável de classe é `List<IobTotal>?` (Kotlin immutable list)
- Conversão explícita necessária

---

## Validação

### Teste 1 — Verificar que array é populado

Adicionar log temporário em `invoke()` após linha 308:

```kotlin
val iobValues: List<Double> = iobArray?.map { it.iob.toDouble() } ?: emptyList()
aapsLogger.debug(LTag.APS, "🔍 iobArray size: ${iobArray?.size ?: 0}, iobValues: $iobValues")
```

**Antes da correção:**
```
🔍 iobArray size: 0, iobValues: []
```

**Depois da correção:**
```
🔍 iobArray size: 12, iobValues: [2.3, 2.1, 1.9, 1.7, 1.5, 1.3, 1.1, 0.9, 0.7, 0.5, 0.3, 0.1]
```

---

### Teste 2 — Verificar que PkPdIntegration recebe dados

Adicionar log em `PkPdIntegration.computeRuntime()`:

```kotlin
fun computeRuntime(
    // ... parâmetros ...
    iobValues: List<Double>
): PkPdRuntime? {
    aapsLogger.debug(LTag.APS, "PKPD: iobValues received: ${iobValues.size} points")
    // ... resto do código
}
```

**Antes da correção:**
```
PKPD: iobValues received: 0 points
```

**Depois da correção:**
```
PKPD: iobValues received: 12 points
```

---

## Impacto esperado

### Positivo

1. **DIA estimation funciona**
   - `InsulinActionProfiler.estimateDia()` recebe dados reais
   - DIA calculado reflete decaimento real de IOB
   - Mais preciso que DIA fixo do perfil

2. **fusedIsf mais preciso**
   - Combina ISF do perfil com DIA dinâmico
   - Adapta a IOB real vs esperado

3. **Fase 1 completa**
   - PkpdAbsorptionGuard usa `pkpdRuntime` correto
   - SmbDampingUsecase usa `peakTimeMinutes` correto

### Negativo (trade-offs)

**Nenhum** — correção só ativa funcionalidade que estava inativa.

**Possível mudança de comportamento:**
- Se DIA estimation produzir DIA **mais longo** que o fixo:
  - IOB decai mais devagar
  - Sistema pode ser menos agressivo
- Se DIA estimation produzir DIA **mais curto**:
  - IOB decai mais rápido
  - Sistema pode ser mais agressivo

**Mitigação:**
- Monitorar logs de `PKPD:` para ver DIA estimado
- Comparar com DIA do perfil (ex: 5.0h)
- Se diferença > 1h, investigar

---

## Código final

```kotlin
// setData() linha 1433-1434:
val iobCalcs = iobCobCalculator.calculateIobFromBolus()
this.iob = iobCalcs.iob + iobCalcs.basaliob.toFloat()
this.iobArray = iobArray.toList()  // ← ADICIONAR ESTA LINHA (Bug #4, Jul/2026)
```

---

## Logs esperados após correção

### Em invoke() (linhas 308-332):

```
🔍 iobArray size: 12, iobValues: [2.3, 2.1, 1.9, ...]
PKPD: computeRuntime called with iobValues: 12 points
PKPD: Estimated DIA: 4.8h (vs profile: 5.0h)
PKPD: fusedIsf: 42.3 (profile: 45.0)
PKPD: peakMin: 68.0
```

### Em applySafetyPrecautions() (linhas 582-596):

```
PKPD Guard (peakWindow): 0.80 -> 0.72 U
SMB Damping: 0.72 -> 0.65 U
```

---

## Dependências

### DigestionDetector
- Já implementado (linha 194)
- **Independente** de `iobArray`

### PkPdIntegration
- Depende de `iobValues`
- **Requer esta correção** para funcionar

### PkpdAbsorptionGuard
- Depende de `pkpdRuntime`
- **Requer esta correção** para usar DIA correto

### SmbDampingUsecase
- Depende de `peakTimeMinutes` do `pkpdRuntime`
- **Requer esta correção** para usar peak correto

---

## Risco de regressão

**MUITO BAIXO**
- Mudança de **1 linha**
- Não altera lógica existente
- Apenas ativa funcionalidade já implementada

**Efeito colateral possível:**
- Se Fase 1 estava "desligada" silenciosamente, SMBs podem mudar
- Mas Fase 1 foi implementada em 27/Jul (3 dias atrás) — usuário já esperava que funcionasse

---

## Verificação pós-implementação

### 1. Confirmar população do array

```bash
# Filtrar logs do AAPS:
adb logcat | grep "iobArray size"
```

Esperado: `iobArray size: 10-15` (varia conforme histórico de IOB)

### 2. Confirmar DIA estimation

```bash
adb logcat | grep "PKPD: Estimated DIA"
```

Esperado: Valor entre 3.0h e 6.0h (coerced em `InsulinActionProfiler`)

### 3. Confirmar que guards PK/PD ativam

```bash
adb logcat | grep "PKPD Guard\|SMB Damping"
```

Esperado: Logs aparecem quando condições se aplicam

---

## Perguntas para o usuário

1. **Fase 1 está ativa?**
   - Foi implementada em 27/Jul/2026 mas pode ter ficado inativa por este bug
   - Verificar se logs de `PKPD:` aparecem atualmente

2. **SMBs mudaram após 27/Jul?**
   - Se não, confirma que Fase 1 estava inativa
   - Se sim, pode ser por outros guards (não por PK/PD)

---

## Aprovação necessária

- [ ] Confirmar que Fase 1 PK/PD está esperada estar ativa
- [ ] Implementar junto com Bugs #2 e #3 (Lote 1)?
- [ ] Adicionar logs de debug temporários para validação?

---

**Status:** Pronto para implementação (risco muito baixo)
