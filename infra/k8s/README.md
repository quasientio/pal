
1. Install minikube and kubectl
2. Create cluster:
```bash
minikube start --driver=docker
```
3. Verify cluster IP and update in kafka-deployment.yaml if needed:
```bash
minikube ip
```
4. Create etcd and kafka services and pods:
```bash
# etcd
#kubectl apply -f etcd-pvc.yaml
kubectl apply -f etcd-deployment.yaml
kubectl apply -f etcd-service.yaml
 
# kafka
#kubectl apply -f kafka-pvc.yaml
kubectl apply -f kafka-deployment.yaml
kubectl apply -f kafka-service.yaml
```
