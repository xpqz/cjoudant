# cjoudant - a tiny client library for Cloudant (and CouchDB)

A gossamer-thin veneer over the CouchDB HTTP API.

## Installation

Download from https://clojars.org/cjoudant

## Examples

```clojure
(def host "http://127.0.0.1:5984")
(def user "admin")
(def password "xyzzy")
(def db-name "mydatabase")

;; Create an authenticated session
(def db (session host user password :database dbname))

;; Get all documents
(all-docs db)

;; Insert
(def result (insert-doc db {:name "bob" :occupation "used car salesperson"}))

;; Read
(read-doc (get result "id"))

;; Update
(def updated
  (let [body {:newname "bob" :occupation "manager"}]
    (update-doc db (get result "id") (get result "rev") body)))

;; Delete
(delete-doc (get updated "id") (get updated "rev"))
```

## License

Copyright © 2018 Stefan Kruger

Distributed under the Apache License 2.0 or (at your option) any later version.
