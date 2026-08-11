# PATCH — Bug #3: DIA adaptativo com lógica when quebrada

**Data:** 30/Jul/2026  
**Arquivo:** `DetermineBasalAdapterAIMI.kt`  
**Localização:** Linhas 1545-1549  
**Severidade:** ALTA

---

## Problema

A lógica `when` para calcular `baseDivisor` do DIA adaptativo está quebrada:

```kotlin
// ATUAL (linhas 1545-1549):
val baseDivisor = when {
    insulin.peak >= 35 -> 55  // ✅ Ativa se peak >= 35
    insulin.peak > 45  -> 65  // ❌ NUNCA ATIVA (código morto)
    else               -> 75
}
```

**Por que é um bug?**
- Primeira condição `peak >= 35` captura **todos** os valores ≥ 35, incluindo > 45
- Segunda condição `peak > 45` **nunca será avaliada** (código morto)
- Insulinas lentas (peak > 45) estão sendo tratadas como rápidas (divisor 55 em vez de 65)

**Exemplo:**
- Humalog/Novolog: peak = 55 min
- **Comportamento atual:** `55 >= 35` ✓ → `baseDivisor = 55`
- **Comportamento esperado:** `55 > 45` ✓ → `baseDivisor = 65`

---

## Impacto clínico

### DIA calculation (linha 1578-1580)

```kotlin
val insulinDivisor = (baseDivisor * timeAdjustment * iobAdjustment * activityAdjustment)
    .roundToInt()
    .coerceIn(35, 95)
```

**Com bug (baseDivisor = 55):**
- Sem ajustes: `insulinDivisor = 55`
- DIA estimado: mais curto
- IOB decai mais rápido → subestimado

**Corrigido (baseDivisor = 65):**
- Sem ajustes: `insulinDivisor = 65`
- DIA estimado: ~18% mais longo
- IOB decai mais devagar → mais preciso

### Consequência

**Com IOB subestimado:**
1. Sistema pensa que há menos insulina ativa
2. Dá SMB adicional
3. IOB real (não calculado) acumula
4. Risco de hipo por stacking

**Magnitude do erro:**
- `55 / 65 = 0.846` → DIA é ~15% mais curto
- Para DIA real de 5h → calculado como 4.25h
- Diferença de IOB aos 4h: ~10-15% subestimado

---

## Correção

Inverter ordem das condições (mais específica → mais genérica):

```kotlin
// CORRIGIDO:
val baseDivisor = when {
    insulin.peak > 45  -> 65  // Lento (Humalog, Novolog, Apidra)
    insulin.peak >= 35 -> 55  // Rápido (Fiasp)
    else               -> 75  // Ultra-rápido (Lyumjev)
}
```

**Justificativa:**
- `peak > 45` avalia primeiro → captura insulinas lentas
- `peak >= 35` avalia segundo → captura insulinas rápidas (35-45)
- `else` captura ultra-rápidas (< 35)

---

## Validação

### Teste 1 — Insulina lenta (Humalog, peak = 55)
**Input:** `insulin.peak = 55`

**Antes (bug):**
- `55 >= 35` ✓ → `baseDivisor = 55`

**Depois (corrigido):**
- `55 > 45` ✓ → `baseDivisor = 65`

**Resultado:** ✅ Corrigido

---

### Teste 2 — Insulina rápida (Fiasp, peak = 40)
**Input:** `insulin.peak = 40`

**Antes (bug):**
- `40 >= 35` ✓ → `baseDivisor = 55`

**Depois (corrigido):**
- `40 > 45` ✗
- `40 >= 35` ✓ → `baseDivisor = 55`

**Resultado:** ✅ Comportamento mantido (correto)

---

### Teste 3 — Insulina ultra-rápida (Lyumjev, peak = 30)
**Input:** `insulin.peak = 30`

**Antes (bug):**
- `30 >= 35` ✗
- `30 > 45` ✗
- else → `baseDivisor = 75`

**Depois (corrigido):**
- `30 > 45` ✗
- `30 >= 35` ✗
- else → `baseDivisor = 75`

**Resultado:** ✅ Comportamento mantido (correto)

---

## Tipos de insulina e seus peaks

| Insulina | Peak (min) | Categoria | baseDivisor esperado |
|----------|-----------|-----------|---------------------|
| Lyumjev  | 30        | Ultra-rápida | 75 |
| Fiasp    | 40        | Rápida       | 55 |
| Humalog  | 55        | Lenta        | 65 |
| Novolog  | 55        | Lenta        | 65 |
| Apidra   | 60        | Lenta        | 65 |

**Fonte:** `activePlugin.activeInsulin.peak` vem do perfil de insulina configurado no AAPS.

---

## Verificação necessária

### Confirmar tipo de insulina do usuário

```kotlin
// Adicionar log temporário em setData() após linha 1544:
aapsLogger.debug(LTag.APS, "Insulin type: ${insulin.friendlyName}, peak: ${insulin.peak} min")
```

**Se o usuário usa Humalog/Novolog (peak > 45):**
- Bug está ativo
- Correção é **crítica**

**Se o usuário usa Fiasp (peak 35-45) ou Lyumjev (peak < 35):**
- Bug não afeta
- Correção é **preventiva** (para futuras mudanças de insulina)

---

## Impacto em outros cálculos

### 1. Cálculo de `variableSensitivity` (linha 1644)

```kotlin
variableSensitivity = (1800 / (tdd * (ln((glucoseStatus.glucose / insulinDivisor) + 1))))
```

**Com insulinDivisor errado (55 em vez de 65):**
- `ln((bg / 55) + 1)` vs `ln((bg / 65) + 1)`
- Para BG = 130: `ln(3.36)` vs `ln(3.0)` → `1.21` vs `1.10`
- `variableSensitivity` será ~10% maior (mais sensível à insulina)

**Efeito cascata:**
- ISF superestimado → SMB reduzido
- Pode compensar parcialmente o bug de IOB subestimado
- Mas é um **falso equilíbrio** — ambos estão errados

### 2. Ajustes por atividade física (linhas 1566-1576)

```kotlin
val activityAdjustment = when {
    recentSteps5Minutes > 200 && averageBeatsPerMinute > averageBeatsPerMinute180 && bg < 130 -> 0.85
    // ...
}
```

**Com baseDivisor errado:**
- `insulinDivisor = 55 * 0.85 = 46.75` (exercício intenso)
- Corrigido: `insulinDivisor = 65 * 0.85 = 55.25`
- Diferença de ~18% no cálculo de DIA durante exercício

---

## Patch final

```kotlin
// ════════════════════════════════════════════════════════════════════
// Insulina divisor dinâmico (DIA adaptativo) — CORRIGIDO (Bug #3, Jul/2026)
// ════════════════════════════════════════════════════════════════════
// Base: derivado do tempo de pico da insulina (fixo por tipo)
// Ajustes dinâmicos: hora do dia, IOB, atividade física (Bio-Sync)
// ORDEM CORRETA: peak > 45 ANTES de peak >= 35 (evita código morto)
// ════════════════════════════════════════════════════════════════════
val insulin = activePlugin.activeInsulin
val baseDivisor = when {
    insulin.peak > 45  -> 65  // Lento (Humalog, Novolog, Apidra)
    insulin.peak >= 35 -> 55  // Rápido (Fiasp)
    else               -> 75  // Ultra-rápido (Lyumjev)
}
```

---

## Simulação NS recomendada

### Antes de implementar:
1. Verificar tipo de insulina configurado no perfil AAPS
2. Se peak > 45: calcular IOB manualmente para um evento passado e comparar com IOB do sistema

### Após implementar:
1. Monitorar IOB calculado vs esperado
2. Comparar SMBs dados antes vs depois (não deve mudar drasticamente se `variableSensitivity` estava compensando)

---

## Risco de regressão

**BAIXO**
- Mudança cirúrgica (3 linhas)
- Apenas reordena condições existentes
- Não adiciona nova lógica

**Possível efeito colateral:**
- Se sistema estava "funcionando" com o bug, pode ser porque `variableSensitivity` superestimado compensava IOB subestimado
- Correção pode revelar necessidade de ajustar outros parâmetros

**Mitigação:**
- Monitorar primeiras 24h
- Comparar SMBs médios/dia antes vs depois
- Se aumentar agressividade, pode precisar reduzir maxSMB temporariamente

---

## Aprovação necessária

- [ ] Confirmar tipo de insulina (peak > 45?)
- [ ] Revisar impacto em `variableSensitivity`
- [ ] Implementar com Bugs #2 e #4 (Lote 1)?
- [ ] Monitorar 24-48h após implementação

---

**Status:** Aguardando confirmação do tipo de insulina
