(ns doplarr.webhook-server
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as a]
   [doplarr.webhook :as webhook]
   [hato.client :as http]
   [org.httpkit.server :as server]
   [taoensso.timbre :refer [error info]]))

(defn webhook-handler [request]
  "Handle incoming Jellyseerr webhooks"
  (try
    (let [body (slurp (:body request))
          data (json/parse-string body true)]
      (info "Received webhook from" (:remote-addr request))
      ; Process webhook asynchronously
      (webhook/handle-jellyseerr-webhook data)
      ; Return 200 OK to Jellyseerr
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:status "ok"})})
    (catch Exception e
      (error e "Error processing webhook")
      {:status 500
       :headers {"Content-Type" "application/json"}
       :body (json/generate-string {:error "Internal server error"})})))

(defn start-webhook-server [port]
  "Start the webhook HTTP server on the specified port"
  (try
    (let [server (server/run-server
                  (fn [request]
                    (if (= (:request-method request) :post)
                      (webhook-handler request)
                      {:status 404
                       :body "Not Found"}))
                  {:port port})]
      (info "Webhook server started on port" port)
      server)
    (catch Exception e
      (error e "Failed to start webhook server"))))

(defn stop-webhook-server [server]
  "Stop the webhook HTTP server"
  (when server
    (server :timeout 100)
    (info "Webhook server stopped")))
