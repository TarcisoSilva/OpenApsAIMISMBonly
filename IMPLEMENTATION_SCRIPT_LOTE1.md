# Script de Implementação — Lote 1: Bugs Críticos #2, #3, #4

**Data:** 30/Jul/2026  
**Arquivo alvo:** `DetermineBasalAdapterAIMI.kt`  
**Total de mudanças:** 3 correções (9 linhas adicionadas/modificadas)

---

## Resumo das correções

| Bug | Localização | Mudanças | Risco |
|-----|-------------|----------|-------|
| #4  | Linha 1434  | +1 linha | Muito Baixo |
| #3  | Linhas 1545-1549 | Reordenar 3 linhas | Baixo |
| #2  | Após linha 1223 | +5 linhas (guard) | Baixo |

**Total de linhas alteradas:** 9  
**Tempo estimado:** 10 min  
**Backup necessário:** Sim

---

## Passo 1 — Backup do arquivo original

```bash
cd /Volumes/M2/Users/tarciso/Downloads/OpenApsAIMI-master_Plugin_OpenApsAIMI_Steps-DEV-2/plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI

# Criar backup com timestamp
cp DetermineBasalAdapterAIMI.kt DetermineBasalAdapterAIMI.kt.bak.$(date +%Y%m%d_%H%M%S)

# Verificar backup
ls -lh DetermineBasalAdapterAIMI.kt.bak.*
```

---

## Passo 2 — Aplicar Bug #4 (iobArray)

**Localização:** Linha 1434 (após `this.iob = ...`)

### Contexto atual (linhas 1433-1437):

```kotlin
val iobCalcs = iobCobCalculator.calculateIobFromBolus()

this.iob = iobCalcs.iob + iobCalcs.basaliob.toFloat()
// this.iob = iobCalcs.iob.toFloat() + iobCalcs.basaliob.toFloat()
this.bg = glucoseStatus.glucose
this.bgTime = glucoseStatus.date
```

### Após correção:

```kotlin
val iobCalcs = iobCobCalculator.calculateIobFromBolus()

this.iob = iobCalcs.iob + iobCalcs.basaliob.toFloat()
this.iobArray = iobArray.toList()  // Bug #4 fix (Jul/2026) — popula array para Fase 1 PK/PD
// this.iob = iobCalcs.iob.toFloat() + iobCalcs.basaliob.toFloat()
this.bg = glucoseStatus.glucose
this.bgTime = glucoseStatus.date
```

---

## Passo 3 — Aplicar Bug #3 (DIA adaptativo)

**Localização:** Linhas 1545-1549

### Contexto atual:

```kotlin
// Camada 1 — Ajuste por hora do dia (ritmo circadiano)
val hour = LocalTime.now().hour
val timeAdjustment = when (hour) {
    in 6..10  -> 0.90  // Manhã: maior sensibilidade, DIA reduzido
    in 22..23, in 0..5 -> 1.10  // Noite: resistência natural, DIA estendido
    else      -> 1.0
}
```

### Antes da correção (linhas 1544-1549):

```kotlin
val insulin = activePlugin.activeInsulin
val baseDivisor = when {
    insulin.peak >= 35 -> 55
    insulin.peak > 45  -> 65
    else               -> 75
}
```

### Após correção:

```kotlin
// ════════════════════════════════════════════════════════════════════
// DIA adaptativo — Bug #3 fix (Jul/2026)
// ORDEM CORRIGIDA: peak > 45 ANTES de peak >= 35 (evita código morto)
// ════════════════════════════════════════════════════════════════════
val insulin = activePlugin.activeInsulin
val baseDivisor = when {
    insulin.peak > 45  -> 65  // Lento (Humalog, Novolog, Apidra)
    insulin.peak >= 35 -> 55  // Rápido (Fiasp)
    else               -> 75  // Ultra-rápido (Lyumjev)
}
```

---

## Passo 4 — Aplicar Bug #2 (Override 2 IOB guard)

**Localização:** Após linha 1223 (dentro de `predictFutureBg()`)

### Contexto atual (linhas 1217-1240):

```kotlin
// Version 209 — v2: Adicionado guarda iob < 2.0 (Jul/2026)
if (bg <= 155 && delta > 0 && iob < 2.0) {
    futureBg = targetBg + 2
}

if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
}

/*if (LocalTime.now().run { hour in 1..3 } && bg >= 135 && bg <= 160 && delta > 2 && iob >= 2) {
    futureBg = targetBg + 2

}*/

//if (LocalTime.now().run { hour in 6..22 } && bg > 100 && bg <= 140 && delta > 0) {
//    futureBg = (bg+(delta*1.5))-((iob/(bg/75))*variableSensitivity)
//}


// S'assurer que la glycémie future n'est pas inférieure à une valeur minimale, par exemple 39

if (futureBg < 59.0) {
    futureBg = 59.0
}

return futureBg.toDouble()
```

### Após correção (adicionar após linha 1223):

```kotlin
// Version 209 — v2: Adicionado guarda iob < 2.0 (Jul/2026)
if (bg <= 155 && delta > 0 && iob < 2.0) {
    futureBg = targetBg + 2
}

if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
}

// ═══════════════════════════════════════════════════════════════════
// Guard IOB Alta — Bug #2 fix (Jul/2026)
// ═══════════════════════════════════════════════════════════════════
// Previne que fórmula padrão subestime risco quando há IOB residual
// alto (> 2.0) com BG subindo. Força cálculo conservador.
// ═══════════════════════════════════════════════════════════════════
if (iob > 2.0 && delta > 0 && bg <= 155) {
    futureBg = (bg + (delta * 1.5)) - ((iob / 2) * variableSensitivity)
}

/*if (LocalTime.now().run { hour in 1..3 } && bg >= 135 && bg <= 160 && delta > 2 && iob >= 2) {
    futureBg = targetBg + 2

}*/
```

---

## Passo 5 — Verificar compilação

```bash
cd /Volumes/M2/Users/tarciso/Downloads/OpenApsAIMI-master_Plugin_OpenApsAIMI_Steps-DEV-2

# Compilar módulo APS
./gradlew :plugins:aps:compileDebugKotlin

# Verificar erros
echo $?  # Deve retornar 0 (sucesso)
```

---

## Passo 6 — Adicionar logs de debug (opcional)

### 6.1 — Log para Bug #4 (iobArray)

Adicionar em `invoke()` após linha 308:

```kotlin
val iobValues: List<Double> = iobArray?.map { it.iob.toDouble() } ?: emptyList()

// DEBUG: Bug #4 validation (remover após confirmar correção)
aapsLogger.debug(LTag.APS, "🔍 Bug #4 check: iobArray size=${iobArray?.size ?: 0}, values=${iobValues.take(5)}")
```

### 6.2 — Log para Bug #3 (DIA adaptativo)

Adicionar em `setData()` após linha 1549 (após `baseDivisor`):

```kotlin
val baseDivisor = when {
    insulin.peak > 45  -> 65
    insulin.peak >= 35 -> 55
    else               -> 75
}

// DEBUG: Bug #3 validation (remover após confirmar correção)
aapsLogger.debug(LTag.APS, "🔍 Bug #3 check: insulin=${insulin.friendlyName}, peak=${insulin.peak}min, baseDivisor=$baseDivisor")
```

### 6.3 — Log para Bug #2 (Override 2 guard)

Já existe log em `applySafetyPrecautions()` linha 889-892 (P1 Soft Guard).  
Adicionar similar para o novo guard:

```kotlin
if (iob > 2.0 && delta > 0 && bg <= 155) {
    val beforeOverride = futureBg
    futureBg = (bg + (delta * 1.5)) - ((iob / 2) * variableSensitivity)
    // DEBUG: Bug #2 validation (remover após confirmar correção)
    aapsLogger.debug(LTag.APS, "🔍 Bug #2 guard: IOB=$iob, delta=$delta, bg=$bg → futureBg=${beforeOverride.toInt()}→${futureBg.toInt()}")
}
```

---

## Passo 7 — Build completo

```bash
# Build completo (APK debug)
./gradlew assembleDebug

# Localizar APK gerado
find . -name "*debug.apk" -type f -mmin -10
```

---

## Passo 8 — Instalação e teste

### 8.1 — Instalar no emulador (teste)

```bash
# Listar devices
adb devices

# Instalar
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 8.2 — Monitorar logs em tempo real

```bash
# Terminal 1: Filtro geral APS
adb logcat -s APS:D

# Terminal 2: Filtro bugs específicos
adb logcat | grep "Bug #[234] check\|PKPD:\|iobArray size"
```

### 8.3 — Validações esperadas

**Bug #4 (após primeiro ciclo APS):**
```
🔍 Bug #4 check: iobArray size=12, values=[2.3, 2.1, 1.9, 1.7, 1.5]
PKPD: iobValues received: 12 points
PKPD: Estimated DIA: 4.8h (vs profile: 5.0h)
```

**Bug #3 (no setData do primeiro ciclo):**
```
🔍 Bug #3 check: insulin=Humalog, peak=55min, baseDivisor=65
```

**Bug #2 (quando condição ativar):**
```
🔍 Bug #2 guard: IOB=2.5, delta=2.0, bg=130 → futureBg=95→88
```

---

## Passo 9 — Testes de cenário

### Cenário 1 — Bug #4: Verificar Fase 1 ativa

**Setup:**
- Ter IOB > 1.0
- Esperar ciclo APS (~5 min)

**Validação:**
- Log mostra `iobArray size > 0`
- Log mostra `PKPD: Estimated DIA`

### Cenário 2 — Bug #3: Verificar baseDivisor correto

**Setup:**
- Confirmar tipo de insulina no perfil AAPS

**Validação:**
- Se peak > 45 → `baseDivisor=65` (não 55)
- Se peak 35-45 → `baseDivisor=55`
- Se peak < 35 → `baseDivisor=75`

### Cenário 3 — Bug #2: Verificar guard IOB alta

**Setup:**
- Simular: BG subindo (delta > 0), IOB > 2.0, BG < 155

**Validação:**
- Log mostra guard ativou
- `futureBg` recalculado (não usa `targetBg + 2`)

---

## Passo 10 — Rollback (se necessário)

```bash
cd /Volumes/M2/Users/tarciso/Downloads/OpenApsAIMI-master_Plugin_OpenApsAIMI_Steps-DEV-2/plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI

# Restaurar backup
cp DetermineBasalAdapterAIMI.kt.bak.20260730_232304 DetermineBasalAdapterAIMI.kt

# Recompilar
cd /Volumes/M2/Users/tarciso/Downloads/OpenApsAIMI-master_Plugin_OpenApsAIMI_Steps-DEV-2
./gradlew assembleDebug
```

---

## Checklist de aprovação

Antes de implementar:
- [ ] Backup criado
- [ ] Revisão do código das 3 correções
- [ ] Confirmar tipo de insulina (Bug #3)
- [ ] Decidir: logs de debug incluídos ou não?
- [ ] Decidir: testar em emulador primeiro ou direto no celular?

Após implementar:
- [ ] Compilação sem erros
- [ ] Logs de validação aparecem
- [ ] Bug #4: `iobArray` populado
- [ ] Bug #3: `baseDivisor` correto
- [ ] Bug #2: guard ativa quando esperado
- [ ] Monitorar 24-48h em produção
- [ ] Remover logs de debug após validação

---

## Notas importantes

1. **Não interromper ciclo APS durante testes**
   - Esperar pelo menos 3 ciclos (~15 min) antes de avaliar

2. **Comparar SMBs antes vs depois**
   - Anotar SMBs médios/dia pré-correção
   - Comparar pós-correção
   - Diferença esperada: < 10%

3. **Monitorar IOB calculado**
   - Se Bug #3 estava ativo e foi corrigido, IOB pode ser ligeiramente maior
   - Sistema pode ficar ligeiramente menos agressivo

4. **Se algo der errado**
   - Rollback imediato via backup
   - Reportar logs do problema
   - Analisar antes de tentar novamente

---

## Arquivo de registro de mudanças

Criar arquivo de changelog:

```bash
cat > /Volumes/M2/Users/tarciso/Downloads/OpenApsAIMI-master_Plugin_OpenApsAIMI_Steps-DEV-2/CHANGELOG_BUGS_20260730.md << 'EOF'
# Changelog — Correção de Bugs Críticos

**Data:** 30/Jul/2026  
**Versão:** 228 → 229  
**Autor:** Hermes Mobile Dev + Tarciso

---

## Bugs Corrigidos

### Bug #4 — iobArray nunca populado
**Severidade:** Alta  
**Localização:** Linha 1434  
**Correção:** Adicionar `this.iobArray = iobArray.toList()`  
**Impacto:** Fase 1 PK/PD agora funciona corretamente

### Bug #3 — DIA adaptativo com lógica when quebrada
**Severidade:** Alta  
**Localização:** Linhas 1545-1549  
**Correção:** Reordenar condições (`peak > 45` antes de `peak >= 35`)  
**Impacto:** Insulinas lentas (peak > 45) agora têm baseDivisor=65 (correto)

### Bug #2 — Override 2 sem guarda IOB alta
**Severidade:** Alta  
**Localização:** Após linha 1223  
**Correção:** Adicionar guard para IOB > 2.0  
**Impacto:** Previne SMB quando BG sobe COM IOB residual alto

---

## Validação

- [ ] Compilação: OK
- [ ] Logs de debug: OK
- [ ] Bug #4 iobArray populado: OK
- [ ] Bug #3 baseDivisor correto: OK
- [ ] Bug #2 guard ativa: OK
- [ ] Teste emulador 2h: OK
- [ ] Teste produção 24h: Pendente
- [ ] Teste produção 48h: Pendente

---

## Observações

(Adicionar notas após testes)

EOF
```

---

**Pronto para implementação. Aguardando aprovação.**
