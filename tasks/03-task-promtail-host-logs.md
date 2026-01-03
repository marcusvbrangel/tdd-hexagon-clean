Tarefa: Promtail coletar logs dos micros rodando no host (IntelliJ) e enviar ao Loki

Contexto/diagnostico
- Os micros rodando no host ja escrevem logs JSON em repo-root/logs/*.json.
- O promtail em Docker enxerga o mount em /var/log/retail-store, mas Loki/Grafana seguem vazios.
- O promtail precisa usar a config correta e ter scrape_config para /var/log/retail-store/*.json com labels consistentes.

Objetivo
- Garantir que o Promtail em Docker colete os arquivos /var/log/retail-store/*.json e envie para o Loki.
- Confirmar que a query {job="retail-store"} retorna logs no Loki e aparece no Grafana Explore.
- Sem instalar nada no host.

Restricoes
- Nao alterar containers/docker-compose.yaml (base).
- Somente editar containers/docker-compose.observability.yaml e arquivos em containers/observability/.
- Evitar labels de alta cardinalidade (nao usar order_id como label).

Implementacao
1) Garantir a configuracao do promtail:
   Arquivo: containers/observability/promtail/promtail.yaml
   Conteudo minimo esperado:

   server:
     http_listen_port: 9080
     grpc_listen_port: 0

   positions:
     filename: /tmp/positions.yaml

   clients:
     - url: http://loki:3100/loki/api/v1/push

   scrape_configs:
     - job_name: retail-store-host-logs
       static_configs:
         - targets: [localhost]
           labels:
             job: retail-store
             __path__: /var/log/retail-store/*.json

       pipeline_stages:
         - json:
             expressions:
               level: level
               service: service
               env: env
               trace_id: trace_id
               span_id: span_id
               correlation_id: correlation_id
               message: message
         - labels:
             service:
             env:
             level:
         - output:
             source: message

   Observacao: se service nao estiver no JSON, manter o service como label via relabel no path do arquivo.

2) Garantir o mount correto no compose:
   Arquivo: containers/docker-compose.observability.yaml (service promtail)
   - command: ["-config.file=/etc/promtail/promtail.yaml"]
   - volumes:
       - ./observability/promtail/promtail.yaml:/etc/promtail/promtail.yaml:ro
       - ../logs:/var/log/retail-store:ro
       - promtail_positions:/tmp
   - Manter volumes de docker.sock e /var/lib/docker/containers se houver coleta de logs de containers.

3) Recriar somente o promtail:
   cd containers
   docker compose -f docker-compose.yaml -f docker-compose.observability.yaml up -d --force-recreate promtail

Validacao (obrigatoria)
1) Config carregada no container:
   docker exec -it containers-promtail-1 sh -lc 'sed -n "1,140p" /etc/promtail/promtail.yaml'
   Deve conter __path__: /var/log/retail-store/*.json e job: retail-store.

2) Arquivos visiveis no container:
   docker exec -it containers-promtail-1 sh -lc 'ls -lah /var/log/retail-store | sed -n "1,30p"'

3) Promtail tailing sem erros:
   docker logs --tail 120 containers-promtail-1
   Deve mostrar "Adding target ... /var/log/retail-store/*.json" e nao ter erro de push.

4) Loki recebeu logs:
   curl -s "http://localhost:3100/loki/api/v1/query?query=%7Bjob%3D%22retail-store%22%7D" | head -c 500; echo
   data.result deve estar preenchido apos gerar logs.

Query de teste no Grafana
- Explore -> Loki: {job="retail-store"}
- Filtrar por servico: {job="retail-store", service="ms-order"} | json

Entrega
- Commit com as mudancas.
- Mensagem de commit: "Fix Promtail to ingest host IntelliJ logs into Loki"
- Anexar no PR/descricao os outputs da validacao (1-4).
