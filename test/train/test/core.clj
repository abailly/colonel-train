(ns train.test.core
  (:require [clojure.data.json :as json :refer [ read-str write-str]])
  (:use midje.sweet
        train.core))

(defmacro dbg 
  "provide debugging trace for a given expression"
  [x] 
  `(let [x# ~x] (print "dbg:" '~x "=" x#) x#))


;.;. Effort only fully releases its reward after a person refuses to
;.;. quit. -- Hill
(fact "selecting tickets to print by date"
  (first  (make-ticket-list (read-str (sample-tickets) :key-fn keyword))) => 
  {:departure "St-Pierre-des-Corps", 
   :departure_date "2013-03-28T10:15:00+01:00", 
   :arrival "Paris-Gare-Montparnasse", 
   :arrival_date "2013-03-28T11:13:00+01:00", 
   :pnr "529090", 
   :pdf "https://app.capitainetrain.com/tickets/K0qg79658/TENEVO-BAILLY.pdf"})
