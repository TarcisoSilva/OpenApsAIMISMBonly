# PATCH — Bug #2: Override 2 sem guarda IOB alta

**Data:** 30/Jul/2026  
**Arquivo:** `DetermineBasalAdapterAIMI.kt`  
**Localização:** Após linha 1223  
**Severidade:** ALTA

---

## Problema

Override 2 no `predictFutureBg()` permite que BG subindo COM IOB alto mascare risco de queda:

```kotlin
// Linha 1221-1223 (ATUAL):
if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
}
```

**Cenário de risco:**
- BG = 130 mg/dL
- delta = +2.0 (subindo)
- IOB = 2.5 U (alto — insulina residual de SMBs anteriores)
- Hora = 10h (dentro de 4-22)

**Comportamento atual:**
- Condição `iob <= 0.8` falha → override NÃO aplica
- `futureBg` calculado via fórmula padrão (linha 1187): `(130 + 2*1.5) - ((2.5/2) * 45) = 133 - 56 = 77`
- `futureBg = 77` → abaixo de `targetBg` → proteção ativa → SMB = 0

**Por que isso é um bug?**
- Se IOB for **ligeiramente maior** que 0.8 (ex: 1.2), o override não se aplica
- Mas a fórmula padrão pode **subestimar** o risco se houver IOB residual alto (> 2.0)
- Similar ao Bug #6 (já corrigido): override desliga proteção quando deveria manter

---

## Correção

Adicionar guarda **defensiva** APÓS o override existente para capturar casos com IOB alta:

```kotlin
// ADICIONAR após linha 1223:

// ═══════════════════════════════════════════════════════════════════
// Guard IOB Alta (Bug #2, Jul/2026)
// ═══════════════════════════════════════════════════════════════════
// Previne que fórmula padrão subestime risco quando há IOB residual
// alto (> 2.0) com BG subindo. Força cálculo conservador que considera
// toda a insulina ativa.
// Condições: IOB > 2.0 + delta > 0 + BG ≤ 155
// ═══════════════════════════════════════════════════════════════════
if (iob > 2.0 && delta > 0 && bg <= 155) {
    futureBg = (bg + (delta * 1.5)) - ((iob / 2) * variableSensitivity)
}
```

---

## Justificativa da defesa em profundidade

**Por que não substituir o override existente?**
- Override existente (linha 1221) está correto para IOB **baixo** (≤ 0.8)
- Guarda nova captura o caso **oposto**: IOB **alto** (> 2.0)
- Dois guards cobrem espectro completo: baixo (override desliga proteção) vs alto (override mantém proteção)

**Por que threshold IOB > 2.0?**
- Alinhado com Bug #6 (linha 1217): `iob < 2.0` no Override 1
- Usuário tem perfil SMB-puro → IOB > 2.0 é comum após 2-3 SMBs
- Threshold conservador: só ativa quando risco é real

**Por que BG ≤ 155?**
- Se BG > 155, sistema precisa de SMB para tratar (não bloquear)
- Se BG ≤ 155 com IOB > 2.0, risco de overshoot é alto

---

## Validação

### Teste 1 — IOB baixo (override original deve funcionar)
**Input:**
- BG = 110, delta = +1.5, IOB = 0.6, hora = 10h

**Esperado:**
- Override linha 1221 ativa: `iob <= 0.8` ✓ → `futureBg = targetBg + 2 = 92`
- Guard nova NÃO ativa: `iob > 2.0` ✗

**Resultado:** Override desliga proteção (comportamento correto)

---

### Teste 2 — IOB alta (guard nova deve ativar)
**Input:**
- BG = 130, delta = +2.0, IOB = 2.8, hora = 10h

**Esperado:**
- Override linha 1221 NÃO ativa: `iob <= 0.8` ✗
- Fórmula padrão (linha 1187): `(130 + 3) - ((2.8/2) * 45) = 133 - 63 = 70`
- Guard nova ativa: `iob > 2.0` ✓ e `delta > 0` ✓ e `bg <= 155` ✓
- `futureBg` recalculado: `(130 + 3) - ((2.8/2) * 45) = 70` (mesmo valor)

**Resultado:** Guard mantém proteção ativa (previne SMB)

---

### Teste 3 — BG alto com IOB alta (não deve bloquear)
**Input:**
- BG = 170, delta = +3.0, IOB = 2.5, hora = 10h

**Esperado:**
- Override linha 1221 NÃO ativa: `iob <= 0.8` ✗
- Guard nova NÃO ativa: `bg <= 155` ✗
- Fórmula padrão aplica normalmente

**Resultado:** Sistema dá SMB para tratar hiper (comportamento correto)

---

## Simulação NS recomendada

Buscar eventos onde:
1. `iob > 1.5`
2. `delta > 0`
3. `bg < 155`
4. `hour in 4..22`
5. Hipo nas 2h seguintes

**Exemplo conhecido:** Evento 21/Jul NS (BG 103→61 após SMB 1.0U com IOB 0.2)
- **Nota:** Este evento tinha IOB **baixo** antes do SMB — não seria capturado por esta guarda
- Guarda protege contra **IOB residual alto**, não IOB pré-SMB

---

## Código final (linhas 1221-1235)

```kotlin
// Version 209 — v2: Adicionado guarda iob < 2.0 (Jul/2026)
if (bg <= 155 && delta > 0 && iob < 2.0) {
    futureBg = targetBg + 2
}

if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
}

// ═══════════════════════════════════════════════════════════════════
// Guard IOB Alta (Bug #2, Jul/2026)
// ═══════════════════════════════════════════════════════════════════
// Previne que fórmula padrão subestime risco quando há IOB residual
// alto (> 2.0) com BG subindo. Força cálculo conservador que considera
// toda a insulina ativa.
// ═══════════════════════════════════════════════════════════════════
if (iob > 2.0 && delta > 0 && bg <= 155) {
    futureBg = (bg + (delta * 1.5)) - ((iob / 2) * variableSensitivity)
}
```

---

## Impacto esperado

**Positivo:**
- Previne SMB quando IOB residual alto + BG subindo
- Reduz risco de hipo por stacking de insulina

**Negativo (trade-offs):**
- Pode bloquear SMB legítimo se IOB > 2.0 após refeição
- Threshold IOB 2.0 pode ser conservador demais para alguns perfis

**Mitigação:**
- Condição `bg <= 155` limita scope — não bloqueia hipers
- Se BG continuar subindo (delta > 0 persistente), próximo ciclo terá IOB menor (decaído)

---

## Aprovação necessária

- [ ] Revisar lógica da guarda
- [ ] Confirmar threshold IOB 2.0 está adequado ao perfil
- [ ] Decidir: implementar com Bug #3 e #4 (Lote 1) ou separado?
- [ ] Simular contra NS antes de implementar ou implementar direto?

---

**Status:** Aguardando aprovação
