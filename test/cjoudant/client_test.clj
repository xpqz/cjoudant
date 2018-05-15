;; To run tests against a local CouchDB we need to ensure
;; that we disable CouchDB's admin party mode by creating
;; an admin user, as the cjoudant library does not allow
;; unauthenticated connections.
;;
;; docker run -d -p 5984:5984 --rm --name couchdb couchdb:1.6
;; export HOST="http://127.0.0.1:5984"
;; curl -XPUT $HOST/_config/admins/admin -d '"xyzzy"'
;; curl -XPUT $HOST/testdb -u admin
;;
;; To run these tests in the repl:
;;
;; (require '[clojure.test :refer [run-tests]])
;; (require 'cjoudant.client-test)
;; (run-tests 'cjoudant.client-test)

(ns cjoudant.client-test
  (:require [clojure.test :refer :all]
            [cjoudant.client :refer :all]))

(def test-client (atom nil))
(def host "http://127.0.0.1:5984")
(def user "admin")
(def password "xyzzy")

(defn uuid [] (str (java.util.UUID/randomUUID)))
(defn gen-doc [] {:name (uuid) :location (uuid)})

(defn random-db-name []
  (clojure.string/join "-" ["cjoudant" (uuid)]))

(defn create-client
  [test-fn]
  (compare-and-set! test-client nil (session host user password))
  (test-fn))

(defn new-test-database
  [test-fn]
  (let [db-name (random-db-name)]
    (if-let [result (create-database @test-client db-name)]
      (do
        (swap! test-client assoc :database db-name)
        (test-fn))
      (throw (Exception. "Failed to create database")))))

(defn remove-test-database
  [test-fn]
  (test-fn)
  (delete-database @test-client (:database @test-client))
  (swap! test-client dissoc :database))

(use-fixtures :once create-client)
(use-fixtures :each new-test-database remove-test-database)

(deftest create-document
  (testing "Document creation"
    (let [result (insert-doc @test-client (gen-doc))]
      (is (contains? result "rev") true))))

(deftest create-documents
  (testing "Multiple documents creation"
    (let [doc-count 1000
          data (vec (repeatedly doc-count gen-doc))
          result (insert-docs @test-client data)]
      (is (count result) doc-count))))

(deftest create-documents-read-all-docs
  (testing "Retrieve all_docs"
    (let [doc-count 1000
          data (vec (repeatedly doc-count gen-doc))
          result (insert-docs @test-client data)
          all-docs-result (all-docs @test-client)]
      (is (count result) (count all-docs-result)))))

(deftest read-document         ;; Normal caveats apply on reading writes
  (testing "Read document"
    (let [result (insert-doc @test-client (gen-doc))
          result-read (read-doc @test-client (get result "id") {:rev (get result "rev")})]
      (is (get result "id") (get result-read "_id"))
      (is (get result "rev") (get result-read "_rev")))))

(deftest delete-document
  (testing "Successful document deletion"
    (let [result (insert-doc @test-client (gen-doc))
          delete-result (delete-doc @test-client (get result "id") (get result "rev"))]
      (is (contains? delete-result "rev") true))))

(deftest delete-document-should-fail
  (testing "Failed document deletion"
    (let [result (insert-doc @test-client (gen-doc))]
      (is (thrown? Throwable (delete-doc @test-client (get result "id") "1-notthere"))))))

(deftest update-document
  (testing "Successful document update"
    (let [body {:newname "bob" :widget "sproket"}
          result (insert-doc @test-client (gen-doc))
          update-result (update-doc @test-client (get result "id") (get result "rev") body)]
      (is (contains? update-result "rev") true))))

(deftest update-document-should-fail
  (testing "Failed document update"
    (let [body {:newname "bob" :widget "sproket"}
          result (insert-doc @test-client (gen-doc))]
      (is (thrown? Throwable (update-doc @test-client (get result "id") "1-notthere") body)))))


