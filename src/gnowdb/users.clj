(ns gnowdb.users
  (:require [cemerick.friend.credentials :refer (hash-bcrypt)]))

(def users (atom {"a" {:username "a"
                            :password (hash-bcrypt "abc")
                            :pin "1234" ;; only used by multi-factor
                            :roles #{::user}}
                  "d" {:username "d"
                                  :password (hash-bcrypt "def")
                                  :pin "1234" ;; only used by multi-factor
                                  :roles #{::admin}}}))

(derive ::admin ::user)