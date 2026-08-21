# SIMULAÇÃO - MELHORIAS SMOOTHING
Data: 18/08/2026 | Dados: 3607 linhas (02/22 a 07/19/2026)

## PADRÕES OBSERVADOS
- Deltas normais (-4 a +5): 90%
- Deltas altos (>10): 3%
- Oscilações: 8%
- Picos erráticos: <1%

## CASO 1: PICO ERRÁTICO (100→115→100)
- Atual: Trava 6 ativa (tier 2), delta +15 usado no PD
- Proposta 1: Suaviza para 107.5, delta corrigido -11.5
- Ganho: 60-80% redução SMBs errados

## CASO 2: OSCILAÇÕES (100→93→101→106→100)
- Atual: Nenhum delta >10, Trava 8 não aplica
- Proposta 3: Feature oscilação (stdDev > 5)
- Proposta 4: Captura 2+ reversões
- Ganho: Melhor classificação de ruído

## CASO 3: SENSOR 18H (PROBLEMA CRÍTICO!)
- Atual: phaseSensor=0.0, mas FASE 1 é só <6h
- 18h > 6h → SEM PROTEÇÃO!
- Proposta 5: 18h → phaseSensor=0.3, proteção 0.85x
- Ganho: Protege sensores 6-24h

## CASO 4: FINGER STICK
- Atual: direcaoDivergencia=0.0 (nunca calculado!)
- Proposta 6: (dedo-bg)/50 como feature
- Ganho: Rede aprende padrão de deriva
