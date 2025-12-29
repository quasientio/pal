# Local Etcd & Kafka on Kubernetes

This folder contains all you need to spin up a **single-node Minikube cluster** running:

* **Etcd** – key/value directory for Pal  
* **Apache Kafka** – where Pal's Logs live

Everything is kept intentionally small so it starts fast on a laptop.

---

## Prerequisites

| Tool        | Version (tested) | Notes                                  |
|-------------|------------------|----------------------------------------|
| `minikube`  | ≥ v1.36          | Docker driver is used automatically    |
| `kubectl`   | ≥ v1.29          | Installed by most package managers     |

```bash
# quick check
minikube version
kubectl version --client
```

---

## Cluster lifecycle

Run the helper script from anywhere inside the repo:

```bash
run_k8s.sh <dev | prod | stop>
```

| Command | What happens |
|---------|--------------|
| `run_k8s.sh dev`  | • Creates/starts Minikube<br>• Deploys **dev** overlay (`emptyDir` volumes)<br>• Stops **prod** overlay first if it exists |
| `run_k8s.sh prod` | • Creates/starts Minikube<br>• Deploys **prod** overlay (PVCs)<br>• Stops **dev** overlay first if it exists |
| `run_k8s.sh stop` | Deletes whichever overlay is active |

> **Default IP expectation**  
> Manifests assume Minikube assigns **`192.168.49.2`**.  
> If yours differs, recreate the cluster or adjust the NodePorts/IPs in the overlays.

---

## Host-reachable endpoints

| Service | NodePort | URL                        |
|---------|----------|---------------------------|
| Kafka   | 30092    | `192.168.49.2:30092`      |
| Etcd    | 32379    | `192.168.49.2:32379`      |

*(Prod uses the same ports; they’re unique cluster-wide.)*

---

## ▶️ Run Pal’s **HelloWorld** example

```bash
pal run \
  --dir           192.168.49.2:32379 \
  --kafka-servers 192.168.49.2:30092 \
  --log           auto \
  -cp target/classes/ \
  io.quasient.pal.examples.HelloWorld
```

---

## Overlay profiles

| Profile | Storage        | Networking | Notes                                         |
|---------|----------------|------------|-----------------------------------------------|
| **dev** | `emptyDir`     | NodePort   | Fast, stateless – ideal for day-to-day coding |
| **prod**| PVC            | NodePort   | Simulates stateful disk; real prod would use LoadBalancer/Ingress |

> **No parallel runs**  
> Both overlays share the same NodePort numbers; run one at a time.

---

## Customising

* **Change NodePorts/IP** – edit the Service patches in `overlays/*/patches/`.
* **Use a different driver** – pass `--driver=<name>` to `minikube start` inside `run_k8s.sh`.
* **Full reset** – `minikube delete` removes the VM, images, and all volumes.

---

## License

Source files are governed by the **Business Source License 1.1** (see each file header).  
After the _Change Date_ they switch to Apache 2.0.
