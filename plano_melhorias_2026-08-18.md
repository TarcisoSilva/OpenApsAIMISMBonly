# PLANO DE MELHORIA - SMOOTHING GLICOSE
Data: 18/08/2026 | Projeto: OpenApsAIMI

## 6 PROPOSTAS IDENTIFICADAS

| # | Proposta | Arquivo | Ganho | Risco | Prioridade |
|---|----------|-------|-------|-------|------------|
| 1 | Detecção de picos no Smoothing | ExponentialSmoothingPlugin.kt | 9/10 | 2/10 | ⭐⭐⭐⭐⭐ |
| 2 | Correção de delta baseado em tier | BgConfidenceGuard.kt | 7/10 | 5/10 | ⭐⭐⭐⭐ |
| 3 | Feature oscilação na rede neural | AimiBgConfidenceTrainer.kt | 6/10 | 2/10 | ⭐⭐⭐ |
| 4 | Captura automática de oscilações | AimiBgConfidenceTrainer.kt | 4/10 | 1/10 | ⭐⭐ |
| 5 | Gradiente fino de idade do sensor | DetermineBasalAdapterAIMI.kt | 6/10 | 2/10 | ⭐⭐⭐⭐ |
| 6 | Finger stick como feature | DetermineBasalAdapterAIMI.kt | 8/10 | 5/10 | ⭐⭐⭐ |

## RECOMENDAÇÃO
Fase 1 (imediato): Propostas 1 e 5
Fase 2 (pós-validação): Propostas 2 e 3
Fase 3 (longo prazo): Propostas 4 e 6

## NOTA IMPORTANTE
Trava 8 NÃO substitui Propostas 3 e 4 (são complementares)
