(ns cjoudant.core
  (:require [org.httpkit.client :as http])
  (:require [cheshire.core :as json]))

(defn session
  [base-url username password]
  {:base-url base-url
   :username username
   :password password})

(defn database
  [session db-name]
  (assoc session :database db-name))

(defn- endpoint-vec
  [session path-vec]
  (clojure.string/join "/" (concat [(:base-url session) (:database session)] path-vec)))

(defn- endpoint
  [session path-str]
  (endpoint-vec session [path-str]))

(defn- auth
  [session]
  [(:username session) (:password session)])

(defn req-future
  [session method path opts body]
  (let [shared-params {:url (endpoint session path)
                       :method method
                       :basic-auth (auth session)}
        params (merge shared-params (when body {:headers {"content-type" "application/json"}
                                                :body (json/generate-string body)})
                                    (when opts {:query-params opts}))]
    (http/request params)))
        
(defn req
  [session method path opts body]
  (let [response @(req-future session method path opts body)]
    (json/parse-string (:body response))))

(defn- bulk-docs
  ([session docs]
   (req session :post "_bulk_docs" nil {:docs docs}))

  ([session docs opts]
   (req session :post "_bulk_docs" opts {:docs docs}))) 
    
(defn read-doc
  [session doc-id & opts]
  (req session :get doc-id opts nil))
  
(defn update-doc
  [session doc-id rev-id body]
  (let [r (bulk-docs session [(merge body {:_id doc-id, :_rev rev-id})])]
    (first r)))

(defn insert-doc
  [session body]
  (let [r (bulk-docs session [body])]
    (first r)))

(defn insert-docs
  [session docs]
  (bulk-docs session docs))

(defn delete-doc
  [session doc-id rev-id]
  (let [r (bulk-docs session [{:_id doc-id, :_rev rev-id, :deleted true}])]
    (first r)))

(defn all-docs
  ([session]
   (req session :get "_all_docs" nil nil))

  ([session opts]
   (let [{:keys [conflicts descending endkey endkey_docid include_docs 
                 inclusive_end key keys limit skip stale start_key 
                 startkey_docid update-seq] :as options} opts]
     (if keys
       (req session :post "_all_docs" (dissoc options :keys) {:keys [(:keys options)]})
       (req session :get "_all_docs" options nil)))))

(defn changes
  ([session]
   (req session :get "_changes" nil nil))

  ([session opts]
   (req session :get "_changes" opts nil)))

(defn design-docs
  [session]
  (let [response (all-docs session {:startkey "_design/", :endkey "_design0"})]
    (:rows response)))

(defn view-query
  "https://console.bluemix.net/docs/services/session/api/using_views.html#using-views"
  [session ddoc view-name opts]
  (let [{:keys [conflicts descending endkey endkey_docid include_docs inclusive_end 
                key keys limit group group_level reduce stable skip stale start_key 
                startkey_docid update] :as options} opts
        url (endpoint-vec session ["_design" ddoc "_views" view-name])]
    (if keys
      (req session :post url (dissoc options :keys) {:keys [(:keys options)]})
      (req session :get url options nil))))
