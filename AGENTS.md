# Project Knowledge Base

## DetermineBasalAdapterAIMI.kt

**Localização:** `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAdapterAIMI.kt`

**Execução:** Este arquivo é acionado de modo assíncrono a cada ~5 minutos pelo ciclo APS (Artificial Pancreas System). A cada execução, **todas as variáveis são recalculadas do zero**, incluindo: BG, delta, IOB, COB, TDD, ISF, heart rate, steps, TIR, alarmes de hipoglicemia, etc.

**Fluxo de execução:**
1. `setData()` é chamada primeiro — calcula todas as variáveis de entrada (bg, iob, cob, tdd, variableSensitivity, maxIob, maxSMB, targetBg, predictedBg, etc.)
2. `invoke()` é chamada depois — usa as variáveis calculadas em `setData()` para determinar o SMB a ser enviado à bomba

**Variáveis de classe:** Todas as variáveis de classe (ex: `bg`, `iob`, `maxIob`, `lowFactor`, etc.) são recalculadas a cada ciclo. Não há persistência de estado entre ciclos (a menos que a instância seja reutilizada pelo DI).

**Pontos-chave do algoritmo:**
- Modelo ML (TensorFlow Lite) para predição de SMB
- Controle PD (Proporcional + Derivado) para correção
- Ajuste dinâmico de ISF baseado em TDD e BG
- Proteção contra hipoglicemia via TIR e alarmes
- Detecção de atividade física via passos e heart rate
- Fatores horários (morning/afternoon/evening) para ajuste temporal
- CSV logging para análise externa
