(ns doplarr.webhook
  (:require
   [clojure.core.async :as a]
   [com.rpl.specter :as s]
   [discljord.messaging :as m]
   [doplarr.discord :as discord]
   [doplarr.state :as state]
   [doplarr.utils :as utils]
   [taoensso.timbre :refer [debug error info]]))

; Jellyseerr webhook notification types
(def notification-types
  {"REQUEST_PENDING_APPROVAL" :request-pending
   "REQUEST_APPROVED" :request-approved
   "REQUEST_AVAILABLE" :request-available
   "REQUEST_DECLINED" :request-declined
   "REQUEST_PROCESSING_FAILED" :request-failed
   "ISSUE_REPORTED" :issue-reported
   "ISSUE_COMMENT" :issue-comment
   "ISSUE_RESOLVED" :issue-resolved
   "ISSUE_REOPENED" :issue-reopened})

(defn extract-user-id [webhook-data]
  "Extract Discord user ID from Jellyseerr webhook payload"
  (s/select-one [:notification :user :discordId] webhook-data))

(defn extract-media-info [webhook-data]
  "Extract media information from webhook payload"
  (let [media (s/select-one [:media] webhook-data)
        media-type (keyword (s/select-one [:media :mediaType] webhook-data))]
    {:title (or (s/select-one [:media :title] webhook-data)
                (s/select-one [:media :name] webhook-data))
     :type media-type
     :year (s/select-one [:media :releaseDate] webhook-data)
     :overview (s/select-one [:media :overview] webhook-data)
     :poster (s/select-one [:media :posterPath] webhook-data)}))

(defn build-dm-message [notification-type media-info]
  "Build a DM message based on notification type"
  (case notification-type
    :request-pending
    {:content (str "📋 **Request Pending**: " (:title media-info) " needs approval")
     :embeds [{:title (:title media-info)
               :description (:overview media-info)
               :color 3447003}]}
    
    :request-approved
    {:content (str "✅ **Request Approved**: " (:title media-info))
     :embeds [{:title (:title media-info)
               :description (:overview media-info)
               :color 65280}]}
    
    :request-available
    {:content (str "🎉 **Available**: " (:title media-info) " is now ready!")
     :embeds [{:title (:title media-info)
               :description (:overview media-info)
               :color 255}]}
    
    :request-declined
    {:content (str "❌ **Request Declined**: " (:title media-info))
     :embeds [{:title (:title media-info)
               :description (:overview media-info)
               :color 16711680}]}
    
    :request-failed
    {:content (str "⚠️ **Request Failed**: " (:title media-info) " failed to process")
     :embeds [{:title (:title media-info)
               :description (:overview media-info)
               :color 16776960}]}
    
    ; Default message
    {:content (str "📢 Notification: " (name notification-type))}))

(defn handle-jellyseerr-webhook [webhook-data]
  "Process Jellyseerr webhook and send DM if DM notifications enabled"
  (a/go
    (try
      (let [notification-type-str (s/select-one [:notification :type] webhook-data)
            notification-type (get notification-types notification-type-str)
            user-id (extract-user-id webhook-data)
            media-info (extract-media-info webhook-data)
            {:keys [messaging]} @state/discord]
        
        (if (and user-id notification-type (:discord/dm-notifications @state/config))
          (do
            (info "Processing Jellyseerr webhook:" notification-type-str "for user:" user-id)
            (let [dm-channel (->> @(m/create-dm! messaging user-id)
                                 :id)]
              (->> @(m/create-message! messaging dm-channel (build-dm-message notification-type media-info))
                   (debug "Sent DM notification" user-id))))
          (do
            (when-not user-id
              (debug "No Discord user ID in webhook"))
            (when-not notification-type
              (debug "Unknown notification type:" notification-type-str))
            (when-not (:discord/dm-notifications @state/config)
              (debug "DM notifications disabled")))))
      
      (catch Exception e
        (error e "Error processing Jellyseerr webhook")))))
