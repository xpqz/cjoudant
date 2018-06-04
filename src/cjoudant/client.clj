;; To reload in the repl: (use 'cjoudant.client :reload)
;
; (def client (session "https://USER.cloudant.com" "USER" "PASSWORD"))
; (def db (database client "db-name"))
; (all-docs db)
;
; or
;
; (def db (session "https://USER.cloudant.com" "USER" "PASSWORD" :database "db-name"))
; (all-docs db)


(ns cjoudant.client
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
  (let [shared-params {:url url, :method method} headers (:headers session)]
    (merge-with into shared-params
      (when body {:headers {"content-type" "application/json"}, :body (json/generate-string body)})
      (when query-params {:query-params query-params})
      (when headers {:headers headers}))))

(defn req-future
  [session method url query-params body]
  (let [data (request-params session method url query-params body)]
    (http/request data)))

(defn req
  [session method url & {:keys [query-params body] :or {query-params nil body nil}}]
  (let [{:keys [status headers body error] :as response} @(req-future session method url query-params body)]
    (when error (throw (ex-info {:status status :error error})))
    (if (contains? #{201 200} status)
      (json/parse-string body)
      (throw (ex-info "Bad status" {:status status :info body :url url})))))

(defn new-cookie
  [session]
  (let [url (endpoint session ["_session"])
        response @(req-future session :post url nil {:name (:username session) :password (:password session)})]
    (:set-cookie (:headers response))))

(defn session
  [base-url username password & {:keys [database] :or {database ""}}]
  (let [details {:base-url base-url, :username username, :password password}]
    {:base-url base-url, :database database, :headers {"Cookie" (new-cookie details)}}))

(defn database
  [session db-name]
  (assoc session :database db-name))

(defn create-database
  [session db-name]
  (let [url (endpoint session [db-name])]
    (req session :put url)))

(defn delete-database
  [session db-name]
  (let [url (endpoint session [db-name])]
    (req session :delete url)))

(defn- bulk-docs
  [session docs & opts]
  (let [url (db-endpoint session ["_bulk_docs"])]
    (req session :post url :query-params opts :body {:docs docs})))

(defn read-doc
  ([session doc-id]
    (let [url (db-endpoint session [doc-id])]
      (req session :get url)))

  ([session doc-id opts]
    (let [url (db-endpoint session [doc-id])]
      (req session :get url :query-params opts))))


(defn update-doc
  [session doc-id rev-id body]
  (let [response (bulk-docs session [(merge body {:_id doc-id, :_rev rev-id})])
        item (first response)]
    (if (contains? item "error")
      (throw (ex-info "Update error" {:error (:error item) :reason (:reason item)}))
      item)))

(defn insert-doc
  [session body]
  (let [response (bulk-docs session [body])
        item (first response)]
    (if (contains? item "error")
      (throw (ex-info "Insert error" {:error (:error item) :reason (:reason item)}))
      item)))

(defn insert-docs
  [session docs]
  (bulk-docs session docs))

(defn delete-doc
  [session doc-id rev-id]
  (let [response (bulk-docs session [{:_id doc-id, :_rev rev-id, :_deleted true}])
        item (first response)]
    (if (contains? item "error")
      (throw (ex-info "Delete error" {:error (:error item) :reason (:reason item)}))
      item)))

(defn all-docs
  [session & opts]
  (let [url (db-endpoint session ["_all_docs"])
        {:keys [conflicts descending endkey endkey_docid include_docs
                inclusive_end key keys limit skip stale start_key
                startkey_docid update-seq] :as options} opts]
    ; If given a list of keys, do a POST, otherwise a GET
    (if keys
      (req session :post url :query-params (dissoc options :keys) :body {:keys (:keys options)})
      (req session :get url :query-params options))))

(defn view-query
  [session ddoc view-name opts]
  (let [{:keys [conflicts descending endkey endkey_docid include_docs inclusive_end
                key keys limit group group_level reduce stable skip stale start_key
                startkey_docid update] :as options} opts
        url (db-endpoint session ["_design" ddoc "_view" view-name])]
    (if keys
      (req session :post url :query-params (dissoc options :keys) :body {:keys (:keys options)})
      (req session :get url :query-params options))))

(defn mango-query
  [session query query-params]
  (let [url (db-endpoint session ["_find"])]
    (req session :post url :query-params query-params :body query)))
