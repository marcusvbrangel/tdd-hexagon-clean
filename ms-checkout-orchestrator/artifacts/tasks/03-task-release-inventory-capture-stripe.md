
Bora por partes, porque aqui tem duas coisas diferentes acontecendo ao mesmo tempo:

A saga do ms-checkout-orchestrator está SIM continuando depois do payment.authorized.

O ms-inventory está liberando a reserva por expiração (TTL), porque não existe “commit/finalização” da reserva.

1) A saga continua após payment.authorized (você já tem isso)

No seu CheckoutSagaEngine, quando chega payment.authorized, você chama saga.onPaymentAuthorized(...), salva, e manda order.complete para o ms-order (ou seja: a saga anda para WAIT_ORDER_COMPLETION). Isso está explícito no seu código.

pasted

Então: o “andar da saga” está correto.

2) Quem disparou inventory.released foi o próprio ms-inventory (job de expiração)

Pelos headers que você colou, o evento de release veio com:

x-saga-name = "inventory"

x-saga-step = "EXPIRE"

e aconteceu ~2 minutos depois do inventory.reserved

Isso é o padrão clássico de: reserva com TTL → job varre vencidos → libera.

E isso acontece mesmo que o pagamento tenha sido autorizado, porque “pagamento autorizado” não finaliza reserva. Finalizar reserva exige um passo explícito no estoque (ex: inventory.commit, inventory.confirm, inventory.deduct, etc).

3) Tem um risco extra: seu handler inventory.released no checkout aceita “release” em qualquer etapa

Olha o seu handler atual:

ele pega a saga e faz markInventoryReleased() sem checar expected step.

pasted

Isso é perigoso porque:

inventory.released pode chegar por expiração, fora do fluxo de compensação do checkout;

e seu checkout vai “engolir” isso e marcar como se fosse compensação concluída, mesmo que você esteja em WAIT_PAYMENT / WAIT_ORDER_COMPLETION.

Mesmo que isso não “quebre” o fluxo agora, vira uma bomba de consistência.

O que você quer de verdade (e o que precisa existir)

Você quer:

✅ order.placed → reserva estoque
✅ estoque reservado → autoriza pagamento
✅ pagamento autorizado → continua e conclui o pedido
✅ e o estoque NÃO pode expirar no meio disso
✅ e no final deve existir um “fechamento” da reserva

Pra isso, você precisa de um passo de finalização da reserva:

Opção A (mais simples pro seu estudo): aumentar o TTL no inventory

Ajusta o TTL pra algo tipo 10–15 minutos.

Resolve rápido seu cenário e evita expiração durante o checkout.

Mas… ainda fica faltando o correto “fechamento” da reserva (ela continua existindo e pode expirar depois, causando inconsistência).

Opção B (correta e “matadora”): criar comando inventory.commit (ou inventory.confirm)

Fluxo fica assim:

inventory.reserve → inventory.reserved

payment.authorize → payment.authorized

order.complete → order.completed

inventory.commit → inventory.committed (remove reserva e “consolida” estoque)

E no cancelamento:

inventory.release continua existindo como compensação.

👉 Eu recomendo B + aumentar TTL para um valor confortável (dev-friendly).

Tarefa “super” pro Codex (do jeito que você pediu)

Abaixo vai uma tarefa pronta pra você colar no Codex. Ela é passo a passo, com critérios de aceite, e com foco em não quebrar nada (mudanças aditivas, compatíveis).

✅ SUPER TASK: “Checkout Saga + Stripe Authorization + Inventory Commit (anti-expire)”
Objetivo

Garantir que o fluxo:

order.placed → inventory.reserve → payment.authorize (Stripe) → order.complete → inventory.commit

seja 100% automático, idempotente, tolerante a retry/timeout e sem expiração indevida da reserva.

PARTE 1 — Stripe (conta + chaves + config)
1.1 Criar conta e pegar chaves (Test Mode)

Acessar o Stripe Dashboard e criar conta.
Stripe

No dashboard, ativar Test mode.
Uplisting

Ir em Developers → API keys e copiar:

sk_test_... (secret key) → vai para STRIPE_API_KEY

(pk_test_... não é necessário pro seu backend-only, mas pode guardar)
Uplisting

Se for usar webhooks (recomendado), criar endpoint em Developers → Webhooks e copiar whsec_....
Stripe

1.2 Como preencher suas properties

Seu YAML:

stripe:
apiKey: ${STRIPE_API_KEY:}
connectAccount: ${STRIPE_CONNECT_ACCOUNT:}
defaultPaymentMethodId: ${STRIPE_DEFAULT_PAYMENT_METHOD_ID:pm_card_visa}
captureMethod: ${STRIPE_CAPTURE_METHOD:manual}


Regras:

STRIPE_API_KEY: usar sk_test_... do dashboard.
Uplisting

STRIPE_CONNECT_ACCOUNT: deixar vazio por enquanto (a não ser que você esteja usando Stripe Connect). Se usar, esse valor é tipo acct_... e fica na área de Connect/Accounts.
Affonso

pm_card_visa: ok pra testes rápidos. (Em “mundo real”, isso viria do front/Stripe.js, mas como seu projeto é estudo backend-only, tá aceitável.)

PARTE 2 — ms-payment (autorizar pagamento via Stripe, automático)
2.1 Requisitos funcionais

Consumir payment.authorize do tópico payment.commands.v1.

Criar/confirmar um PaymentIntent no Stripe com capture_method = manual (autorização).

Produzir exatamente um resultado:

payment.authorized ou

payment.declined

Publicar em payment.events.v1 com headers saga (x-saga-id, x-correlation-id, etc).

Ser idempotente usando x-command-id (retry não pode duplicar cobrança).

2.2 Requisitos de robustez (não negociar)

Idempotência:

Use commandId como idempotency-key no Stripe e como chave no seu ProcessedCommandRepository.

Timeouts/retry:

Se Stripe falhar por timeout/rede, retorne erro “transiente” e deixe o checkout retry.

Logs:

Nunca logar apiKey, nunca logar payload sensível.

2.3 Entregáveis no código

StripeProperties (@ConfigurationProperties(prefix="stripe"))

StripeClient/StripeGateway:

authorize(orderId, amount, currency, paymentMethodId, idempotencyKey) -> AuthorizationResult

mapear erros do Stripe em reason code.

PaymentCommandConsumer:

ler comando PaymentAuthorizeCommandV1

chamar PaymentApplicationService.authorize(...)

PaymentApplicationService:

idempotência primeiro (já processei esse commandId?)

chamar gateway Stripe

persistir Payment (status AUTHORIZED/DECLINED + stripePaymentIntentId)

publicar evento payment.authorized ou payment.declined

Testes:

Unit test para mapping de status/erros

Teste de idempotência: mesmo commandId 2x → 1 cobrança

PARTE 3 — ms-checkout-orchestrator (blindar saga contra “inventory.expire” + fechar reserva)
3.1 Corrigir handler de inventory.released (não aceitar fora de compensação)

Modificar onInventoryReleased para:

Só aceitar se saga.getStep() == COMPENSATING OU se existir um flag no evento indicando que foi release solicitado pelo checkout.

Se chegar inventory.released enquanto saga está RUNNING e step WAIT_PAYMENT ou WAIT_ORDER_COMPLETION, registrar como erro grave (e opcionalmente iniciar compensação).

Motivo: hoje você marca inventoryReleased sem validar etapa.

pasted

3.2 Implementar comando novo: inventory.commit

Criar comando e evento novos (mudança aditiva, não quebra nada):

Tópico: inventory.commands.v1

commandType/eventType: "inventory.commit"

Payload mínimo:

commandId, occurredAt, orderId

Evento de retorno:

"inventory.committed" (ou "inventory.commit_failed" se quiser evoluir)

3.3 Quando enviar inventory.commit

No ms-checkout-orchestrator, após order.completed, disparar inventory.commit.

Hoje seu onOrderCompleted só marca saga como concluída e salva.

pasted


Adicionar:

commandSender.sendInventoryCommit(saga, env.eventId(), SagaStep.WAIT_ORDER_COMPLETION.name())

(se você quiser deixar “mais limpo”, criar um step novo WAIT_INVENTORY_COMMIT, mas pra estudo dá pra manter simples)

3.4 Idempotência do inventory.commit

Seguir o mesmo padrão que você começou a aplicar:

saga.getOrCreateInventoryCommitCommandId()

retries reusam o mesmo commandId

quando receber inventory.committed, limpar commandId/estado

3.5 Ajuste rápido de config (anti-expire durante dev)

Mesmo com commit, configurar no ms-inventory:

inventory.reservation.ttlSeconds para bem maior que o pior caso do checkout (ex: 900s).

Isso dá margem enquanto você valida commit/flow.

PARTE 4 — ms-inventory (aplicar commit e cancelar expiração)
4.1 Novo handler inventory.commit

Implementar consumo do comando e lógica:

localizar reserva por orderId

marcar como “committed/finalized” e impedir expiração

ajustar estoque (dependendo do seu modelo: se reserva já decrementa disponível, aqui só finaliza; se não decrementa, aqui decrementa)

publicar evento inventory.committed

4.2 Alterar job de expiração

expiração só libera:

reservas não committed

reservas vencidas

(e continua publicando inventory.released com sagaName=inventory, sagaStep=EXPIRE)

CRITÉRIOS DE ACEITE (checklist final)

payment.authorize → Stripe autoriza (manual capture) → payment.authorized

Saga continua e manda order.complete após payment.authorized (já existe).

pasted

Após order.completed, checkout manda inventory.commit (novo)

ms-inventory recebe commit e emite inventory.committed

Reserva não expira durante o fluxo normal

inventory.released por EXPIRE não “bagunça” o estado do checkout (handler blindado)

Reprocessar mensagens (Kafka retry) não duplica cobrança Stripe nem duplica commit

Agora, respondendo direto: “já posso enviar a tarefa pro Codex?”

✅ Sim — pode mandar.
E se você mandar exatamente a tarefa acima, o Codex tem instrução suficiente pra implementar sem “inventar requisito”.

Dica prática (bem valiosa) antes de você rodar tudo

Você alterou “eventId vs commandId” nos headers. Em vários sistemas, o pessoal mantém x-event-id == x-command-id para comandos, porque simplifica rastreio e idempotência cross-service.

Se algum consumer seu assume isso (mesmo que sem perceber), mudar pode dar comportamento estranho.

Se você quiser zerar risco: para mensagens de comando, faça:

eventId = commandId (e pronto)

e só gere eventId novo para eventos de domínio emitidos pelos serviços

Isso reduz chance de quebra silenciosa.

Onde exatamente você acessa pra criar conta e pegar as chaves no Stripe?

Criar conta no Stripe Dashboard:
Stripe

Pegar secret key em Developers → API keys (em test mode):
Uplisting

Criar webhook e pegar whsec_... em Developers → Webhooks:
Stripe

Se usar Connect (opcional), o acct_... vem da área de Connect/Accounts:
Affonso

Se você quiser, eu também te passo um “roteiro de debug” de 10 passos (o que olhar no Kafka, nos headers, nos logs e no Stripe dashboard) pra você provar que ficou 100% consistente — mas com o que você trouxe aqui, o grande vilão já está identificado: falta o “commit” da reserva do estoque e o handler do checkout precisa blindagem.

Fontes
em alguma parte da saga.... eu quero que o ms-payment resgate o pagamento no hub de pagamento do stripe....  pois lá no portal do stripe, esta com um monte de pagamento ainda nao capturado, veja a seguir:  	
Valor
​
​
Forma de pagamento
Descrição
Cliente
Data
Data do reembolso
Motivo da recusa

R$ 334,20
BRL
Não capturado
•••• 4242
pi_3SjBNC86Z8WrEdbu1LGawACe
28 de dez. 04:14
—
—

R$ 334,20
BRL
Não capturado
•••• 4242
pi_3SjBGa86Z8WrEdbu1SmyTY5q
28 de dez. 04:07
—
—

R$ 349,20
BRL
Não capturado
•••• 4242
pi_3SjBDW86Z8WrEdbu0IH85VZs
28 de dez. 04:04
—
—
3 itens
Pensou por 29s

Isso aí é 100% esperado no teu cenário, porque você configurou o Stripe com capture_method=manual. Nessa modalidade, o que você fez até agora foi autorizar (hold) — e por isso o Dashboard mostra vários pagamentos como “Não capturado” (status típico: requires_capture).
Stripe Docs
+2
Stripe Docs
+2

E o Stripe ainda avisa: PaymentIntents não capturados são cancelados automaticamente depois de alguns dias (7 por padrão).
Stripe Docs
+1

Então sim: você precisa de uma etapa “resgatar” = capturar (ou cancelar) o pagamento na saga.

O desenho certo pra tua saga (backend-only, automático)

Hoje teu ms-payment faz AUTHORIZE (hold). Falta o CAPTURE.

Fluxo recomendado (bem “enterprise” e consistente)

Orchestrator → inventory.reserve

inventory.reserved

Orchestrator → payment.authorize ✅ (cria PaymentIntent manual)

payment.authorized ✅ (status requires_capture ou succeeded)
Stripe Docs

Orchestrator → order.complete

order.completed

Orchestrator → payment.capture ✅ (novo comando)

payment.captured (novo evento) → saga DONE

E compensação:

Se checkout falhar antes de capturar, Orchestrator → payment.cancel (novo comando) + inventory.release + order.cancel
(Cancelar PaymentIntent é suportado quando está requires_capture, etc.).
Stripe Docs
+1

Pra estudo, você pode capturar logo após order.completed. Em produção, muita gente captura depois de “entregue/enviado”, mas aí é outra história.

O que o ms-payment precisa fazer no “resgate” (CAPTURE)
1) Guardar o PaymentIntent ID (pi_...)

Você já tem isso aparecendo no Dashboard (ex: pi_3SjBNC...), então seu serviço precisa salvar esse ID no teu Payment local (campo tipo providerPaymentIntentId).

2) Implementar capture via Stripe API (Java)

Você vai:

retrieve o PaymentIntent
Stripe Docs

se status == requires_capture, chamar capture
Stripe Docs
+1

Exemplo (esqueleto):

RequestOptions opts = RequestOptions.builder()
.setApiKey(apiKey)
.setIdempotencyKey(commandId) // IMPORTANTE: idempotência do capture também
.build();

PaymentIntent intent = PaymentIntent.retrieve(paymentIntentId, opts); // retrieve :contentReference[oaicite:6]{index=6}

if ("requires_capture".equalsIgnoreCase(intent.getStatus())) { // status :contentReference[oaicite:7]{index=7}
PaymentIntentCaptureParams params = PaymentIntentCaptureParams.builder().build();
PaymentIntent captured = intent.capture(params, opts); // capture :contentReference[oaicite:8]{index=8}
// status esperado: succeeded :contentReference[oaicite:9]{index=9}
}

Tarefa pro Codex: “Payment Capture automático na saga”

Copia e cola isso no Codex:

TAREFA CODEX — Implementar CAPTURE automático do Stripe (manual capture) no ms-payment + novo step na saga

CONTEXTO
- Hoje o ms-payment autoriza com capture_method=manual. Isso gera PaymentIntents “Não capturados” no Stripe (requires_capture).
- Queremos que a saga capture automaticamente após o pedido ser completado.
- Tudo via Kafka (ms-payment não conversa com ms-order diretamente).

OBJETIVO
Adicionar o comando "payment.capture" e evento "payment.captured" para capturar PaymentIntents pendentes (requires_capture).
Opcional: comando "payment.cancel" para cancelar PaymentIntents quando a saga falhar antes da captura.

REQUISITOS IMPORTANTES
- Idempotência TOTAL: usar commandId como idempotencyKey no Stripe e como chave no ProcessedMessageRepository.
- Persistir o Stripe PaymentIntent ID (pi_...) no Payment local.
- Não capturar duas vezes: se já estiver "succeeded", apenas republicar outcome.
- Se estiver cancelado, publicar falha.
- Sem vazar STRIPE_API_KEY em logs.

PARTE A — ms-checkout-orchestrator
1) Adicionar envio de comando "payment.capture" após receber "order.completed" (happy path).
    - tópico: payment.commands.v1
    - commandType: "payment.capture"
    - payload: commandId, occurredAt, orderId
    - headers saga iguais ao padrão atual (SagaHeaders.forCommand)

2) Consumir evento "payment.captured" em payment.events.v1 para finalizar saga (ou simplesmente finalizar ao receber payment.captured).
    - opcional: criar step WAIT_PAYMENT_CAPTURE.

PARTE B — ms-payment
3) Criar DTOs:
    - PaymentCaptureCommandV1 (commandId, occurredAt, orderId)
    - PaymentCapturedEventV1 (eventId, occurredAt, orderId, paymentId, providerPaymentIntentId)

4) Consumer de "payment.capture":
    - Ler commandId/orderId/headers
    - Idempotência: processedRepository.markProcessedIfFirst(commandId, "payment.capture", orderId, now)
    - Buscar Payment por orderId e obter providerPaymentIntentId (pi_...)
    - Se não existir PaymentIntentId -> publicar evento de falha (ou lançar erro controlado, definido)

5) StripeGateway.capture(paymentIntentId, commandId):
    - PaymentIntent.retrieve(paymentIntentId)
    - Se status == requires_capture -> PaymentIntent.capture(...)
    - Se status == succeeded -> considerar capturado e retornar OK
    - Se status não capturável -> retornar erro com reason

6) Persistência:
    - Atualizar status do Payment para CAPTURED (novo enum) ou manter AUTHORIZED + flag captured=true
    - Salvar timestamps

7) Publicação:
    - Publicar "payment.captured" em payment.events.v1 via outbox (padrão existente)

PARTE C — (Opcional, mas recomendado) Cancelamento em compensação
8) Implementar comando "payment.cancel" para cancelar PaymentIntent quando saga falhar antes de capturar:
    - usar PaymentIntent.cancel(...) quando status permite (ex: requires_capture).
    - publicar "payment.canceled" ou "payment.cancel_failed".

TESTES
- Unit: capture quando requires_capture -> succeeded
- Unit: capture idempotente (2x mesmo commandId não duplica)
- Unit: capture quando já succeeded -> republica outcome
- Integration: fluxo happy path: reserve -> authorize -> order.complete -> capture -> saga done

CRITÉRIO DE ACEITE
- Após rodar um checkout completo, o PaymentIntent do Stripe deixa de aparecer como “Não capturado” e passa a “succeeded/captured”.
- O número de PaymentIntents pendentes no Dashboard não cresce indefinidamente.

Dica rápida pra “limpar” o que já ficou pendente no Dashboard (test mode)

Enquanto você não implementa o step de capture, esses “Não capturados” vão continuar acumulando. E como o Stripe cancela depois de alguns dias, você pode:

capturar manualmente no Dashboard (só pra limpar o ambiente de teste), ou

criar um endpoint admin no ms-payment tipo POST /admin/payments/{paymentIntentId}/capture (apenas DEV) que chama PaymentIntent.capture
Stripe Docs
+1

Se você me disser onde você armazena hoje o providerPaymentId no Payment (se é o próprio paymentId ou outro campo), eu te digo exatamente como mapear o pi_... e onde encaixar o capture sem refactor grande.

com isto, crie uma tarefa completa para o codex....    envolvendo os dois ajustes anteriores...   o release errado e a captura do pagamento no stripe
Pensou por 5s
TAREFA COMPLETA PARA CODEX — (1) Evitar “inventory.released” errado por EXPIRE bagunçando a saga + (2) Capturar pagamento no Stripe (manual capture)

========================================================
0) CONTEXTO (o que já existe e NÃO pode quebrar)
   ========================================================
- Arquitetura: Saga orquestrada no ms-checkout-orchestrator.
- Comunicação: Kafka apenas. ms-payment NÃO conversa com ms-order (tudo via orchestrator).
- Tópicos (fixos):
    - order.commands.v1 / order.events.v1
    - inventory.commands.v1 / inventory.events.v1
    - payment.commands.v1 / payment.events.v1
- Headers de saga já existentes: x-saga-id, x-correlation-id, x-causation-id, x-saga-step, etc.
- ms-payment já autoriza pagamento na Stripe via PaymentIntent com capture_method=manual (resultado: PaymentIntents “Não capturados” / requires_capture).
- ms-inventory tem job interno que expira reserva e publica inventory.released com x-saga-name=inventory e x-saga-step=EXPIRE.

PROBLEMAS A RESOLVER
(1) “Release errado”: inventory.released por EXPIRE (job interno do inventory) chega no checkout e pode ser tratado como se fosse compensação, mesmo quando a saga está saudável.
(2) “Pagamento não capturado”: como capture_method=manual, o Stripe acumula PaymentIntents autorizados e não capturados. Precisamos de uma etapa automática de CAPTURE após order.completed.

OBJETIVO FINAL
- Happy path automático e consistente:
  order.placed -> inventory.reserve -> inventory.reserved -> payment.authorize -> payment.authorized
  -> order.complete -> order.completed -> payment.capture -> payment.captured -> SAGA DONE
- Compensação correta:
    - Se falhar antes de capturar: payment.cancel (opcional, recomendado) + inventory.release + order.cancel
- Segurança/Idempotência:
    - TODOS os comandos devem ser idempotentes (commandId estável por step).
    - CAPTURE também deve ser idempotente (commandId como idempotencyKey no Stripe).
    - checkout deve ignorar/alertar inventory.released por EXPIRE quando não está compensando.

========================================================
1) MUDANÇA #1 — BLINDAR O CHECKOUT CONTRA inventory.released “ERRADO”
   ========================================================
   Serviço: ms-checkout-orchestrator

1.1) Ajustar o handler do evento inventory.released (CRÍTICO)
- Local: CheckoutSagaEngine (método onInventoryReleased / handler equivalente)
- Comportamento atual (problema): marca inventory released sem checar se saga está em COMPENSATING.

Alterar para:
A) Se saga.step == COMPENSATING:
- aceitar inventory.released e marcarInventoryReleased() normalmente
- seguir o fluxo de compensação (se houver)
  B) Se saga.step != COMPENSATING:
- NÃO marcar inventory released como compensação concluída
- Logar WARNING/ERROR com:
  orderId, sagaId, correlationId, currentStep, event headers (saga-name, saga-step)
- Ação recomendada (mínima, sem quebrar):
    - ignorar o evento (não altera estado) e seguir a saga normal
- Ação recomendada (mais “enterprise”, opcional):
    - marcar saga como “INCONSISTENT_STATE” e iniciar compensação se fizer sentido.

IMPORTANTE:
- Diferenciar “release solicitado pelo checkout” vs “release por EXPIRE do inventory”.
- Como o EXPIRE vem com x-saga-name=inventory e x-saga-step=EXPIRE, use isso para log/diagnóstico.
- Não confiar apenas em eventType, usar também step atual da saga.

1.2) Melhorar diagnóstico para evitar confusão no futuro
- Ao receber inventory.released fora de COMPENSATING:
    - incluir no log “POSSÍVEL EXPIRAÇÃO DE RESERVA (inventory TTL)”
    - recomendar “aumentar TTL” e/ou “implementar inventory.commit”.

1.3) Testes obrigatórios (unit)
- Teste: ao receber inventory.released quando step != COMPENSATING:
    - saga NÃO muda step
    - markInventoryReleased NÃO é chamado
- Teste: ao receber inventory.released quando step == COMPENSATING:
    - markInventoryReleased é chamado

========================================================
2) MUDANÇA #2 — CAPTURA AUTOMÁTICA DO PAGAMENTO NO STRIPE
   ========================================================
   Serviços: ms-checkout-orchestrator + ms-payment

2.1) Contratos de mensagens (novos, aditivos, não quebram os existentes)

2.1.1) Novo COMMAND
- Topic: payment.commands.v1
- commandType: "payment.capture"
- Payload DTO: PaymentCaptureCommandV1
  Campos mínimos:
    - commandId (String)
    - occurredAt (String ISO)
    - orderId (String)

2.1.2) Novo EVENT
- Topic: payment.events.v1
- eventType: "payment.captured"
- Payload DTO: PaymentCapturedEventV1
  Campos mínimos:
    - eventId (String)
    - occurredAt (String ISO)
    - orderId (String)
    - paymentId (String)  -> id interno do ms-payment, se existir
    - providerPaymentIntentId (String) -> "pi_..." do Stripe

(2.1.3) Opcional, recomendado para compensação:
- COMMAND: "payment.cancel"
- EVENT: "payment.canceled" (ou "payment.cancel_failed")

Headers:
- Reusar SagaHeaders padrão:
  x-saga-id, x-correlation-id, x-causation-id, x-saga-name, x-saga-step, x-aggregate-id/orderId
- commandId precisa estar em x-command-id e no payload.

2.2) ms-checkout-orchestrator — disparar capture no ponto certo

2.2.1) Enviar payment.capture APÓS order.completed (happy path)
- No handler de order.completed (CheckoutSagaEngine.onOrderCompleted):
    - Em vez de finalizar a saga imediatamente, disparar o comando:
      commandSender.sendPaymentCapture(...)
    - Avançar saga para um step novo (recomendado):
      SagaStep.WAIT_PAYMENT_CAPTURE
    - Setar deadline/timeout para captura (ex: saga.timeouts.paymentCaptureSeconds)

2.2.2) Consumir payment.captured e finalizar saga
- No handler payment.captured:
    - Validar sagaStep atual:
        - deve estar em WAIT_PAYMENT_CAPTURE (ou pelo menos após WAIT_ORDER_COMPLETION)
    - Marcar saga DONE e persistir.

2.2.3) Timeout / retry para capture
- Atualizar CheckoutSagaTimeoutScheduler:
    - novo case WAIT_PAYMENT_CAPTURE:
        - retry: reenviar payment.capture com MESMO commandId (getOrCreatePaymentCaptureCommandId)
        - max retries configurável
    - se exceder retries:
        - entrar em compensação (ver 2.5)

2.2.4) CommandSender: implementar sendPaymentCapture
- Criar método sendPaymentCapture(CheckoutSaga saga, String causationId, String sagaStep)
- Usar commandId estável (getOrCreatePaymentCaptureCommandId())
- Publicar em payment.commands.v1 com commandType "payment.capture"

2.2.5) Testes unit (orchestrator)
- Ao receber order.completed:
    - deve publicar payment.capture
    - deve mover step para WAIT_PAYMENT_CAPTURE
- Ao receber payment.captured:
    - deve finalizar saga

2.3) ms-payment — implementar CAPTURE no Stripe

2.3.1) Pré-requisito: persistir o PaymentIntent ID (“pi_...”)
- O ms-payment precisa ter armazenado o providerPaymentIntentId (id do PaymentIntent criado na autorização).
- Se hoje ele não persiste, implementar:
    - Campo no Payment (domínio + JPA + migration Flyway do ms-payment):
      providerPaymentIntentId VARCHAR(...)
    - Salvar intent.getId() ao autorizar.

2.3.2) Consumer do comando payment.capture
- Criar KafkaListener para payment.commands.v1 filtrando commandType=payment.capture.
- Idempotência:
    - processedRepository.markProcessedIfFirst(commandId, "payment.capture", orderId, now)
    - Se duplicado:
        - buscar Payment por orderId
        - se já CAPTURED, republicar payment.captured (mesmo outcome)
        - se ainda AUTHORIZED, tentar capture (ou tratar como “replay-safe”)
- Buscar Payment por orderId:
    - deve existir
    - deve conter providerPaymentIntentId (pi_...)
    - se não existir, publicar falha (ou lançar erro controlado que acione retry).

2.3.3) StripeGateway.capture(...)
Implementar:
- retrieve PaymentIntent pelo providerPaymentIntentId
- comportamento por status:
    - requires_capture -> capturar (PaymentIntent.capture)
    - succeeded -> já capturado (ok)
    - canceled -> falha (reason=CANCELED)
    - outros -> falha com reason=STATUS_<...>
- Idempotência Stripe:
    - RequestOptions.setIdempotencyKey(commandId)
- Se usar connectAccount:
    - RequestOptions.setStripeAccount(connectAccount) (se configurado)

2.3.4) Atualizar Payment e emitir evento
- Atualizar Payment status para CAPTURED (novo enum) OU manter AUTHORIZED + flag captured=true (preferível enum CAPTURED).
- Persistir timestamps.
- Publicar payment.captured via outbox/publisher existente:
    - topic payment.events.v1
    - eventType "payment.captured"
    - headers saga preservados: correlationId, sagaId, causationId=commandId, etc.

2.3.5) Testes unit / integração (ms-payment)
- Unit: mapping de status (requires_capture -> captured, succeeded -> ok)
- Unit: idempotência do capture (mesmo commandId 2x => 1 capture efetivo + republicação)
- (Opcional) Integração: Testcontainers + WireMock para Stripe (se já tiver infra de testes)

2.4) Ajustes de observabilidade / logs (sem vazar segredo)
- Logs com:
  orderId, commandId, providerPaymentIntentId, sagaId, correlationId, status do intent
- NÃO logar STRIPE_API_KEY

2.5) Compensação (recomendado)
- Se capture falhar definitivamente (timeout excedido / erro não recuperável):
    - Orchestrator entra em COMPENSATING:
        - enviar inventory.release
        - enviar order.cancel
        - (opcional recomendado) enviar payment.cancel se PaymentIntent estiver requires_capture
- Implementar payment.cancel no ms-payment:
    - PaymentIntent.cancel via Stripe (quando permitido)
    - publicar payment.canceled
- Isso mantém o mundo consistente quando order foi completado mas pagamento não capturou.

========================================================
3) CRITÉRIOS DE ACEITE (o que deve acontecer no final)
   ========================================================
   A) Happy path:
- Após um checkout completo:
    - Stripe Dashboard NÃO mostra mais PaymentIntents “Não capturados” para aquela order
    - Deve existir payment.captured no Kafka (payment.events.v1)
    - Saga finaliza somente após payment.captured

B) “Release errado”:
- Se o ms-inventory publicar inventory.released por EXPIRE durante uma saga RUNNING:
    - ms-checkout-orchestrator NÃO deve marcar a compensação como concluída
    - Deve logar aviso claro e manter o estado correto da saga

C) Idempotência:
- Reentrega Kafka do payment.capture não duplica captura no Stripe
- Retries do scheduler reenviam o mesmo commandId (sem duplicação)

========================================================
4) LIMITES / REGRAS DO CODEX (para não “inventar” coisas)
   ========================================================
- Mudanças devem ser aditivas e seguras:
    - adicionar novos DTOs, handlers, steps
    - não renomear tópicos existentes
    - não alterar o contrato dos eventos/commands existentes
- Se for necessário alterar DB:
    - migrations separadas por serviço (ms-payment / ms-checkout-orchestrator)
    - colunas opcionais com NULL e sem quebrar dados existentes
- Manter padrão atual de headers (SagaHeaders.forCommand / HeaderNames).

========================================================
5) ENTREGÁVEIS (o que o Codex deve devolver)
   ========================================================
- Lista de arquivos alterados e novos
- DTOs novos:
    - PaymentCaptureCommandV1
    - PaymentCapturedEventV1
    - (opcional) PaymentCancelCommandV1 / PaymentCanceledEventV1
- Alterações no ms-checkout-orchestrator:
    - handler inventory.released blindado
    - envio do payment.capture após order.completed
    - handler payment.captured
    - (recomendado) novo SagaStep WAIT_PAYMENT_CAPTURE + timeout/retry
- Alterações no ms-payment:
    - persistência do providerPaymentIntentId (pi_...)
    - consumer payment.capture
    - Stripe capture + idempotência
    - publisher payment.captured
- Testes unitários (mínimo) e, se possível, integração.

Dica prática (pra você não ficar refém do Dashboard cheio)

Depois que isso estiver pronto, roda 1 pedido completo e verifica:

no Stripe, o PaymentIntent sai de “Não capturado” e vai pra succeeded (capturado),

no Kafka, você vê payment.captured,

e a saga só termina no final.

Se você quiser, eu também escrevo um “roteiro de debug” de 10 passos (o que olhar em cada serviço, em cada tópico, e em quais headers) pra você diagnosticar qualquer travada em 2 minutos.

--------------------------------------


um “roteiro de debug” de 10 passos (o que olhar em cada serviço, em cada tópico, e em quais headers) pra você diagnosticar qualquer travada em 2 minutos.



