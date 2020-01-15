#docker run -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e "xpack.ml.enabled=false" elastic

# run ES with CORS config
docker run -d --rm --name elasticsearch -p 9200:9200 -p 9300:9300 -e "discovery.type=single-node" -e "xpack.ml.enabled=false" -e "http.cors.enabled=true" -e "http.cors.allow-origin=*" -e "http.cors.allow-headers=X-Requested-With,X-Auth-Token,Content-Type,Content-Length,Authorization" -e "http.cors.allow-credentials=true" elastic
