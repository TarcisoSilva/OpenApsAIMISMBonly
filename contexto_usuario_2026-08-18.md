# CONTEXTO DO USUÁRIO
Data: 18/08/2026 | Projeto: OpenApsAIMI

## MEDIÇÃO
- Finger stick: 2-4x/dia
- Alarme estabilidade: 70-140 mg/dL (30 min)
- Alarme low BG: quando em queda

## CALIBRAÇÃO
- Tipo: MANUAL
- Fluxo: Alarme → dedo → avalia contexto → decide calibrar ou não

## PROBLEMAS DO SENSOR
1. Mesma curva, valores diferentes (sensor 100, dedo 75)
2. Picos erráticos (100→115→119→100)
3. Descidas falsas (100→85→100)
4. Oscilações (100→93→101→106→100)

## IDADE DO SENSOR
- <24h: instável, cada sensor é diferente
- >12 dias: perde sensibilidade a oscilações

## OBJETIVO REDE NEURAL
"Aprender a detectar leituras erradas, usar finger stick como ground truth, informar confiança ao SMB, corrigir delta e bg erráticos"

## REGRA
Todos arquivos de análise → /Volumes/M2/Users/tarciso/Documents/Mudanças AIMI/ANALISE/
