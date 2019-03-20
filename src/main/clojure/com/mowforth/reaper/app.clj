(ns com.mowforth.reaper.app
  (import  [io.kubernetes.client Configuration]
           [io.kubernetes.client.util Config]
           [io.kubernetes.client.apis AppsV1Api]
           [java.util.concurrent TimeUnit])
  (require [clojure.tools.logging :as log])
  (:gen-class))

(defonce ^:private k8s-ns "default")
(defonce ^:private client (delay (let [timeout 5
                                       c (Config/defaultClient)]
                                   (.setDebugging c true)
                                   (doto (.getHttpClient c) (.setReadTimeout timeout TimeUnit/SECONDS))
                                   (Configuration/setDefaultApiClient c))))

(defn- list-resources []
  (let [api (AppsV1Api.)
        selector (System/getenv "LABEL_SELECTOR")
        list (.getItems (.listNamespacedDeployment api k8s-ns nil nil nil true selector nil nil nil false))]
    list))

(defn- attempt-deletion [resource]
  (let [meta (.getMetadata resource)]
    (log/info "Resource" (.getName meta) "was created at" (.getCreationTimestamp meta))))

(defn -main [& args]
  (log/info "Starting reaper...")
  @client
  (doseq [resource (list-resources)]
    (attempt-deletion resource)))
