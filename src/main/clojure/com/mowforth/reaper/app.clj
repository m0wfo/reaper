(ns com.mowforth.reaper.app
  (import  [io.kubernetes.client Configuration]
           [io.kubernetes.client.util Config]
           [io.kubernetes.client.apis AppsV1Api CoreV1Api]
           [io.kubernetes.client.models V1Deployment V1DeleteOptions V1Service]
           [java.util.concurrent TimeUnit]
           [org.joda.time Hours DateTime])
  (require [clojure.tools.logging :as log])
  (:gen-class))

(defonce ^:private k8s-ns (or (System/getenv "TARGET_NAMESPACE") "default"))
(defonce ^:private client (delay (let [timeout 5
                                       c (Config/defaultClient)]
                                   (doto (.getHttpClient c) (.setReadTimeout timeout TimeUnit/SECONDS))
                                   (Configuration/setDefaultApiClient c))))
(defonce ^:private max-age-hours (Integer/parseInt (or (System/getenv "MAX_AGE_HOURS") "24")))
(defonce ^:private selector (or (System/getenv "LABEL_SELECTOR") "branch, branch notin (develop, master)"))

(defmulti delete class)

(defmethod delete V1Deployment [object]
  (let [api (AppsV1Api.)
        delete-options (doto (V1DeleteOptions.) (.setPropagationPolicy "Background"))]
    (.deleteNamespacedDeployment api (.getName (.getMetadata object)) k8s-ns delete-options nil nil nil nil)))

(defmethod delete V1Service [object]
  (let [api (CoreV1Api.)
        delete-options (doto (V1DeleteOptions.) (.setPropagationPolicy "Background"))]
    (.deleteNamespacedService api (.getName (.getMetadata object)) k8s-ns delete-options nil nil nil nil)))

(defn- list-resources []
  (let [api (AppsV1Api.)
        core-api (CoreV1Api.)
        deployment-list (.getItems (.listNamespacedDeployment api k8s-ns nil nil nil true selector nil nil nil false))
        service-list (.getItems (.listNamespacedService core-api k8s-ns nil nil nil true selector nil nil nil false))]
    (concat deployment-list service-list)))

(defn- attempt-deletion [resource]
  (let [meta (.getMetadata resource)
        now (DateTime.)
        creation-time (.getCreationTimestamp meta)
        delta (.getHours (Hours/hoursBetween creation-time now))]
    (if (> delta max-age-hours)
      (do
        (log/info (class resource) (.getName meta) "was created" delta "hours ago- it's now eligible for collection. Deleting...")
        (delete resource)))))

(defn -main [& args]
  (log/info "Starting reaper...")
  (log/info "Namespace being GC'd:" k8s-ns)
  (log/info "Max resource age:" max-age-hours "hours")
  (log/info "Label selector:" selector)
  @client
  (doseq [resource (list-resources)]
    (attempt-deletion resource))
  (log/info "Collection finished, exiting"))
