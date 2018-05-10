;; To reload in the repl: (use 'cjoudant.core :reload)
;
; (def client (session "https://USER.cloudant.com" "USER" "PASSWORD"))
; (def db (database client "db-name"))
; (all-docs db)
;
; or
;
; (def db (session "https://USER.cloudant.com" "USER" "PASSWORD" :database "db-name"))
; (all-docs db)


(ns cjoudant.core
  (:require [org.httpkit.client :as http])
  (:require [cheshire.core :as json])
  (:require [clojure.java.io :as io]))

(defn- endpoint
  [session path-vec]
  (clojure.string/join "/" (cons (:base-url session) path-vec)))

(defn- db-endpoint
  [session path-vec]
  (endpoint session (cons (:database session) path-vec)))

(defn- request-params
  [session method url query-params body]
  (let [shared-params {:url url, :method method}]
    (merge-with into shared-params (when body {:headers {"content-type" "application/json"},
                                               :body (json/generate-string body)})
                                    (when query-params {:query-params query-params}))))

(defn req-future
  [session method url query-params body]
  (http/request (request-params session method url query-params body)))

(defn req
  [session method url query-params body]
  (let [response @(req-future session method url query-params body)]
    (json/parse-string (:body response))))

(defn new-cookie
  [session]
  (let [url (endpoint session ["_session"])
        response @(req-future session :post url nil {:name (:username session) :password (:password session)})]
    (:set-cookie (:headers response))))

(defn session
  [base-url username password & {:keys [database] :or {database ""}}]
  (let [details {:base-url base-url, :username username, :password password}]
    {:base-url base-url, :database database, :headers {:cookie (new-cookie details)}}))

(defn database
  [session db-name]
  (assoc session :database db-name))

(defn create-database
  [session db-name]
  (req session :put db-name nil nil))

(defn- bulk-docs
  [session docs & opts]
  (let [url (db-endpoint session ["_bulk_docs"])]
    (req session :post url opts {:docs docs})))

(defn read-doc
  [session doc-id & opts]
  (let [url (db-endpoint session [doc-id])]
    (req session :get url opts nil)))

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
  [session & opts]
  (let [url (db-endpoint session ["_all_docs"])
        {:keys [conflicts descending endkey endkey_docid include_docs
                inclusive_end key keys limit skip stale start_key
                startkey_docid update-seq] :as options} opts]
    ; If given a list of keys, do a POST, otherwise a GET
    (if keys
      (req session :post url (dissoc options :keys) {:keys [(:keys options)]} nil)
      (req session :get url options nil))))

(defn view-query
  "https://console.bluemix.net/docs/services/session/api/using_views.html#using-views"
  [session ddoc view-name opts]
  (let [{:keys [conflicts descending endkey endkey_docid include_docs inclusive_end
                key keys limit group group_level reduce stable skip stale start_key
                startkey_docid update] :as options} opts
        url (db-endpoint session ["_design" ddoc "_views" view-name])]
    (if keys
      (req session :post url (dissoc options :keys) {:keys [(:keys options)]} nil)
      (req session :get url options nil))))
