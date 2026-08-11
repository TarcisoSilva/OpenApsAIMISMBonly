# Resumo Executivo — Bugs Encontrados no DetermineBasalAdapterAIMI.kt

**Data:** 30/Jul/2026  
**Arquivo analisado:** 2315 linhas  
**Bugs críticos:** 4  
**Bugs menores:** 2  
**Melhorias sugeridas:** 5

---

## 🔴 BUGS CRÍTICOS (Implementar Agora)

### Bug #2 — Override 2 no predictFutureBg sem guarda IOB alta
**Linha:** 1221-1223  
**Risco:** Hipo por SMB quando BG sobe COM IOB alto  
**Correção:** Adicionar guarda `iob > 2.0` para prevenir override com IOB residual alto

```kotlin
// ANTES:
if (LocalTime.now().run { hour in 4..22 } && iob <= 0.8 && delta > 0) {
    futureBg = targetBg + 2
}

// DEPOIS (adicionar APÓS linha 1223):
// Guard IOB alta: previne override quando há IOB residual que pode causar queda
if (iob > 2.0 && delta > 0 && bg <= 155) {
    futureBg = (bg + (delta * 1.5)) - ((iob / 2) * variableSensitivity)
}
```

**Validação:** Simular contra evento NS 21/Jul (BG 103→61 com IOB 0.2→2.5)

---

### Bug #3 — DIA adaptativo com lógica `when` quebrada
**Linha:** 1545-1549  
**Risco:** IOB subestimado → stacking de SMBs  
**Correção:** Inverter ordem das condições

```kotlin
// ANTES:
val baseDivisor = when {
    insulin.peak >= 35 -> 55  // Captura TODOS >= 35 (incluindo > 45)
    insulin.peak > 45  -> 65  // ❌ CÓDIGO MORTO
    else               -> 75
}

// DEPOIS:
val baseDivisor = when {
    insulin.peak > 45  -> 65  // Lento (Humalog, Novolog)
    insulin.peak >= 35 -> 55  // Rápido (Fiasp)
    else               -> 75  // Ultra-rápido (Lyumjev)
}
```

**Impacto:** Insulinas lentas (peak > 45) estão sendo tratadas como rápidas → DIA 15% mais curto → IOB subestimado

---

### Bug #4 — iobArray nunca populado (Fase 1 PK/PD recebe lista vazia)
**Linha:** 1399 (parâmetro) + 190 (variável) + 308 (uso)  
**Risco:** Fase 1 PK/PD não funciona (DIA estimation falha)  
**Correção:** Atribuir array recebido à variável de classe

```kotlin
// ADICIONAR em setData() após linha 1433:
this.iobArray = iobArray.toList()  // Converte Array<IobTotal> → List<IobTotal>
```

**Validação:** Checar logs de `PkPdIntegration` — deve mostrar `iobValues=[...]` em vez de `iobValues=[]`

---

### Bug #1 — Override 1 no predictFutureBg (✅ JÁ CORRIGIDO)
**Linha:** 1217  
**Status:** ✅ Corrigido em 30/Jul/2026 (Correção #6)  
**Guarda IOB adicionada:** `iob < 2.0`

---

## 🟡 BUGS MENORES (Avaliar)

### Bug #5 — Variável `stable2` nunca muda durante ciclo
**Linha:** 1479 (setData) + 2013 (uso em invoke)  
**Severidade:** Baixa — comportamento correto, mas inconsistente com documentação  
**Ação:** Documentar que `stable2` é fixo por ciclo

### Bug #6 — Possível overlap de condições em `nightMultiplier`
**Linha:** 2208-2220  
**Severidade:** Baixa — ordem está correta, mas pode confundir  
**Ação:** Adicionar comentários explicando precedência

---

## 📊 ESTATÍSTICAS DE GUARDS DEFENSIVOS

### ✅ Guards bem implementados (7)
1. SMB Cumulative Brake (Extended Morning Window)
2. Proportional Distribution Brake
3. Rate Limiter Contextual Noturno (Severity Tiers)
4. Hypo Recovery Guard (query direta de BG)
5. P1 Near-Miss + Bounce (soft guards)
6. Falling Trajectory Guard
7. SMB Load Brake

### ⚠️ Guards que precisam validação (2)
1. **Falling Trajectory Guard:** IOB < 1.0 pode ser muito restritivo?
2. **SMB Load Brake:** Threshold 5.0U pode ser baixo para perfil SMB-puro?

---

## 🎯 PLANO DE IMPLEMENTAÇÃO

### Lote 1 — Bugs Críticos (AGORA)
- [ ] Bug #2 — Override 2 guarda IOB (5 linhas)
- [ ] Bug #3 — DIA adaptativo ordem (3 linhas)
- [ ] Bug #4 — iobArray assignment (1 linha)

**Esforço total:** ~15 min  
**Risco de regressão:** Baixo (correções cirúrgicas)

### Lote 2 — Melhorias (PRÓXIMO)
- [ ] Logging de guards ativos (debug)
- [ ] Timestamp no DigestionDetector (prevenir state leak)

### Lote 3 — Opcional (FUTURO)
- [ ] Telemetria de performance
- [ ] `require()`/`check()` em modo DEBUG

---

## 🧪 VALIDAÇÃO NECESSÁRIA

### Antes de implementar
1. **Bug #2:** Buscar eventos NS com `iob > 1.5 && delta > 0 && bg < 155` seguidos de hipo
2. **Bug #3:** Verificar tipo de insulina no perfil (peak > 45?) e comparar IOB calculado
3. **Bug #4:** Checar logs `PkPdIntegration` — deve mostrar `iobValues=[]`

### Após implementar
1. **Rodar em emulador** com dados NS replay
2. **Comparar SMBs decididos** antes vs depois
3. **Monitorar 24-48h** em produção (celular real)

---

## 📝 PERGUNTAS PARA O USUÁRIO

1. **Bug #2:** Quer defesa em profundidade (adicionar guarda) ou substituir override existente?
2. **Bug #3:** Confirmar tipo de insulina usado (peak > 45)?
3. **Validação de guards:** Quer simulação NS antes de implementar ou implementar direto?
4. **Prioridade:** Implementar os 3 bugs críticos juntos (Lote 1) ou um por vez?

---

## 🔗 PRÓXIMOS PASSOS

1. ✅ **Revisar este resumo** — Confirmar análise
2. ⚙️ **Aprovar Lote 1** — Bugs #2, #3, #4
3. 🧪 **Decidir validação** — Simular NS ou implementar direto?
4. 📦 **Implementar correções** — Patch cirúrgico
5. 🧪 **Testar** — Emulador → Produção
6. 📝 **Atualizar documentação** — AGENTS.md + histórico de bugs

---

**Aguardando aprovação para implementar Lote 1 (Bugs #2, #3, #4)**
