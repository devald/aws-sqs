(ns main
  (:require
    [clojure.set :as set]
    [cognitect.anomalies :as-alias anom]
    [cognitect.aws.client.api :as aws]
    [devald.aws :as daws]
    [puget.printer :as puget]))

(defn ?
  ([x msg]
   (-> msg (->> (format "==== %s ====")) println)
   (? x))
  ([x] (doto x puget/cprint)))

(defonce sqs (aws/client {:api :sqs}))

(defn req [op-ident & {:as req}]
  {:op op-ident :request req})

(defonce log (atom []))

(comment
  (reset! log [])
  (-> @log count)
  )

(defn sqs!
  ([op-map] (sqs! sqs op-map))
  ([sqs-client op-map]
   (? [(-> op-map :op)
       (some-> op-map :request :Entries (->> (mapv :Id)))])
   (let [res (aws/invoke sqs-client op-map)]
     (swap! log conj {:req op-map :res res})
     res)))

(comment

  (aws/validate-requests sqs true)

  (-> (aws/ops sqs) keys sort)

  (daws/doc :sqs :ListQueues)
  (daws/doc :sqs :GetQueueUrl)
  (daws/doc :sqs :ListDeadLetterSourceQueues)
  (daws/doc :sqs :ReceiveMessage)
  (daws/doc :sqs :SendMessageBatch)
  (daws/doc :sqs :DeleteMessageBatch)

  )

(defn list-queues!
  "List all queues."
  ([] (list-queues! sqs))
  ([sqs-client]
   (-> (req :ListQueues :MaxResults 1000)
       (->> (aws/invoke sqs-client))
       :QueueUrls)))

(defn q-name->q-url
  "Get queue URL of queue name."
  ([q-name] (q-name->q-url sqs q-name))
  ([sqs-client q-name]
   (let [res (-> (req :GetQueueUrl :QueueName q-name)
                 (->> (aws/invoke sqs-client)))]
     (if-let [q-url (:QueueUrl res)]
       q-url
       (throw (ex-info (:message res) res))))))

(comment
  (q-name->q-url "fake-q-name")
  )

(defn dlq-url->source-q-url!
  "Get Source Queue of Dead Letter Queue."
  ([dlq-url] (dlq-url->source-q-url! sqs dlq-url))
  ([sqs-client dlq-url]
   (-> (req :ListDeadLetterSourceQueues :QueueUrl (? dlq-url) :MaxResults 10)
       (->> (aws/invoke sqs-client)) ?
       :queueUrls
       first)))

(defn recv-msgs!
  "Receive messages."
  ([q-url] (recv-msgs! sqs q-url))
  ([sqs-client q-url]
   (-> (req :ReceiveMessage
            :QueueUrl q-url
            :MaxNumberOfMessages 10
            :VisibilityTimeout 20
            :MessageAttributeNames ["All"]
            :MessageSystemAttributeNames ["All"])
       (->> (sqs! sqs-client))
       :Messages)))

(defonce qs (list-queues! sqs))

; Dead Letter Queue and Source queue URL map.
(defonce dlq-url+source-q-url
         (-> qs
             sort
             (->> (mapv (juxt identity
                              (fn [q]
                                (Thread/sleep 500)
                                (dlq-url->source-q-url! q))))
                  (remove (comp nil? second))
                  (into {}))))

(comment

  (-> dlq-name q-name->q-url dlq-url->source-q-url!)
  (get dlq-url+source-q-url (q-name->q-url dlq-name))
  (-> dlq-url+source-q-url keys (->> (filter (partial re-find #"social"))))
  (-> qs (->> (filter (partial re-find #"social"))))

  )

(defn msg->send-msg-entry
  "Transform data structure between receive and send."
  [msg]
  (-> msg
      (set/rename-keys
        {:MessageId :Id
         :Body      :MessageBody})
      (doto (some-> :MessageAttributes :AWSTraceHeader (? ":AWSTraceHeader")))
      (update :MessageAttributes update-keys (comp str symbol))
      #_(assoc :MessageSystemAttributes
          (select-keys (:MessageAttributes msg) [:AWSTraceHeader]))))

(defn msg->delete-msg-entry
  "Transform data structure between receive and delete."
  [msg]
  (-> msg
      (set/rename-keys
        {:MessageId :Id})))

(defn send-msgs [source-q-url msgs]
  (req :SendMessageBatch
       :QueueUrl source-q-url
       :Entries (mapv msg->send-msg-entry msgs)))

(defn resend-messages-from-dlq*
  "Resend messages from the Dead Letter Queue."
  [sqs-client dlq-url source-q-url]
  (loop [msg-count 0]
    (if-let [msgs (recv-msgs! dlq-url)]
      (let [sent-msgs (-> source-q-url
                          (send-msgs msgs)
                          (->> (sqs! sqs-client)))]
        (if (::anom/category sent-msgs)
          [sent-msgs]
          [sent-msgs
           (-> (req :DeleteMessageBatch
                    :QueueUrl dlq-url
                    :Entries (mapv msg->delete-msg-entry msgs))
               (->> (sqs! sqs-client)))])
        (recur (+ msg-count (count msgs))))
      msg-count)))

(defn resend-messages-from-dlq
  "Resend messages from the Dead Letter Queue."
  ([dlq-name] (resend-messages-from-dlq sqs dlq-name))
  ([sqs-client dlq-name]
   (let [dlq-url (q-name->q-url dlq-name)]
     (if-let [source-q-url (get dlq-url+source-q-url dlq-url)]
       (let [msg-count (resend-messages-from-dlq* sqs-client dlq-url source-q-url)]
         (println (format "Totally %d messages were sent from %s to %s."
                          msg-count dlq-name source-q-url)))
       (println (format "Missing source-q-url for DLQ %s." (or dlq-url dlq-name)))))))

(def dlq-name
  (first ["social-api-production-dlq"
          "ad-network-microservice-google-production-dlq"]))

(defn dlq-src-q
  ([] (dlq-src-q dlq-name))
  ([dlq-name]
   (dlq-url+source-q-url (q-name->q-url dlq-name))))

(comment
  (resend-messages-from-dlq dlq-name)

  (-> @log count)
  (-> @log first :req)
  (-> @log first :res :Messages count)
  (-> @log first :res :Messages first)
  (-> @log (nth 5) :res)
  (-> @log first :res :Messages (->> (send-msgs (dlq-src-q))) sqs!)
  (-> dlq-name q-name->q-url recv-msgs!)
  (-> dlq-name q-name->q-url recv-msgs! ? (->> (send-msgs (dlq-src-q))) sqs!)

  (-> :cognitect.aws.sqs/MessageAttributeValue clojure.spec.alpha/form)
  (-> :cognitect.aws.sqs/MessageBodyAttributeMap (clojure.spec.alpha/form))

  (-> {:DataType "String", :StringValue "api"}
      ((some-fn
         :StringListValues
         :StringValue
         :BinaryValue
         :BinaryListValues)))

  @(def qs (list-queues! sqs))

  (-> (str "https://sqs.eu-central-1.amazonaws.com/605853681984/"
           "ad-network-microservice-google-production-dlq")
      (dlq-url->source-q-url!))

  ; Solution 1
  @(def dlq-url+source-q-url
     (-> qs
         sort
         (->> (map (juxt identity dlq-url->source-q-url!))
              (remove (comp nil? second))
              (into {}))))
  ; Solution 2
  @(def dlq-url+source-q-url
     (-> qs
         sort
         (->> (into {} (map
                         (fn [q-url]
                           (when-let [src-q (dlq-url->source-q-url! q-url)]
                             [q-url src-q])))))))
  ; Solution 3
  @(def dlq-url+source-q-url
     (-> qs
         sort
         (->> (reduce
                (fn [acc q-url]
                  (if-let [src-q (dlq-url->source-q-url! q-url)]
                    (assoc acc q-url src-q)
                    acc))
                {}))))

  (def fake-source-q
    {'dlq2 :q2
     'dlq4 :q4
     'dlq5 :q5})

  (-> [:q1 :q2 'dlq2 :q3 :q4 'dlq4]
      (->> (map (juxt (fn [dlq] (fake-source-q dlq)) identity))
           (into {}))
      (dissoc nil)))
