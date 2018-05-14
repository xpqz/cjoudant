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
    (let [result (insert-doc @test-client {:name "Bubba", :location "Idaho"})]
      (is (contains? result "id") true))))
