# run ES with CORS config
docker run --rm --name elasticsearch -p 9200:9200 -p 9300:9300 \
  -e "ES_JAVA_OPTS=-Xms750m -Xmx758m" \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "xpack.ml.enabled=false" \
  -e "http.cors.enabled=true" \
  -e "http.cors.allow-headers=X-Requested-With,X-Auth-Token,Content-Type,Content-Length,Authorization" \
  -e "http.cors.allow-origin='*'" \
  -e "http.cors.allow-methods:OPTIONS,HEAD,GET,POST,PUT,DELETE" \
  -e "http.cors.allow-credentials=true" \
  elasticsearch:8.5.3
