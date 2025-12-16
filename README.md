# tdd-hexagon-clean

Projeto de estudo com arquitetura hexagonal, TDD e CQRS (Command/Query separados, leitura via SQL). Domínio usa Value Objects para identidade (`OrderId`, `CustomerId`) e dinheiro (`Money`).

## Endpoints

### Commands
- `POST /orders` — cria pedido (`CreateOrderRequest`)
- `POST /orders/place` — cria e orquestra estoque/pagamento
- `POST /orders/{orderId}/confirm` — confirma pedido
- `POST /orders/{orderId}/cancel` — cancela pedido

### Queries
- `GET /orders` — lista resumida (sem itens)  
  Parâmetros: `status`, `customerId`, `page` (default 0), `size` (default 20)
- `GET /orders/details` — lista detalhada (com itens)  
  Parâmetros: `status`, `customerId`, `page` (default 0), `size` (default 20)
- `GET /orders/{orderId}` — detalhe com itens
- `GET /orders/{orderId}/items/{itemId}` — item específico do pedido
