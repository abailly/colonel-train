(ns train.web
  "Web server for managing stuff in capitainetrain.com

   Some references used:
   
   * http://www.vijaykiran.com/2012/02/12/web-application-development-with-clojure-part-4/
   * [Ring wiki](https://github.com/ring-clojure/ring/wiki)
   * [General Lisp editing stuff](http://ergoemacs.org/emacs/emacs_editing_lisp.html)
   * [Bootstrap references](http://twitter.github.io/bootstrap/base-css.html)
   * [clj-time Library](https://github.com/seancorfield/clj-time)"
  (:use     ring.util.response)
  (:use     ring.middleware.params)
  (:use     ring.middleware.resource)
  (:use     ring.middleware.reload)
  (:use     ring.middleware.file)
  (:use     com.duelinmarkers.ring-request-logging)
  (:use     ring.middleware.file-info)
  (:use     ring.middleware.stacktrace)
  (:require [clojure.data.json :as json :refer [ read-str write-str]]
            [clojure.tools.logging :as log]
            [hiccup.form :as f]
            [hiccup.core :as h]
            [clj-time.core :as time]
            [clj-time.format :as format])
  (:use train.scrap)
  (:use train.summarize))


(defn enumerate-past-months
  "enumerate backward the last 12 months from month given by `start-date`
   Each date is given as the first day of the month"
  [start-date]
  (let [ref-point (if (nil? start-date) (time/now) start-date)]
    (loop [d ref-point
           l []]
         (cond 
          (and (= (time/month d) (time/month ref-point)) (< (time/year  d) (time/year  ref-point))) l
          :else (recur (time/minus d (time/months 1)) (conj l (time/date-time (time/year d) (time/month d) 1)))))))

(def yyyy-MM-dd (format/formatter "yyyy-MM-dd"))
(def MMMM (format/formatter "MMMM"))

(defn list-months
  "Returns a list of ':option' tags for each of the twelve months before current date
   or a given reference point"
  [date]
  (reverse  
    (map (fn [first-day-of-month]
          [:option {:value (format/unparse yyyy-MM-dd first-day-of-month)} (format/unparse MMMM first-day-of-month)]) (enumerate-past-months date))))

(defn tickets-selection
  "HTML Fragment for selecting for input login/password and selecting a date for tickets list"
  []
  (f/form-to {:id "selection" :class "form-inline"} 
             [:post "/tickets/list"]
             
             [:input#login.input-medium    {:type "text" :name "login"  :placeholder "Login"}]
             [:input#password.input-medium {:type "password" :name "password"  :placeholder "Password"}]
             [:select {:name "month"} 
              (list-months (time/now))
              ]
             [:input#list-tickets.btn.btn-primary {:type "submit" :name "list-tickets" :value  "Lister"}]
             [:span#loader.pull-right {:style "display: none;"} [:img {:src "/images/loader.gif"}]]
             
    ))

(defn list-tickets-view
  "Format a list of tickets into an HTML table"
  [tickets-list]
  (f/form-to { :target "_blank"} 
             [:post "/tickets/select"]
             [:table#tickets.table-striped.table.table-condensed
              [:thead [:tr 
                       [:th "D&eacute;part"] 
                       [:th "Date"] 
                       [:th "Arriv&eacute;e"] 
                       [:th "Date"] 
                       [:th "Prix"] 
                       [:th "Billet"] 
                       [:th [:input#select-all {:type "checkbox" :name "select-all" :value "select-all"}]]]]
              [:tr.info
               [:td]
               [:td]
               [:td]
               [:td]
               [:td [:b (reduce + (map #(:price %) tickets-list))]]
               [:td]
               [:td]]
              (concat (map (fn [ticket] 
                             (let [ticket-name (if (nil? (:pdf ticket)) 
                                                 nil
                                                 (second  (re-find #".*/(.*).pdf"  (:pdf ticket))))]
                               [:tr 
                                [:td (:departure ticket)]
                                [:td (:departure_date ticket)]
                                [:td (:arrival ticket)]
                                [:td (:arrival_date ticket)]
                                [:td (:price ticket)]
                                [:td 
                                 [:a {:href  (:pdf ticket)} ticket-name]]
                                [:td 
                                 (if (nil? ticket-name)
                                   "" 
                                   [:input {:type "checkbox" :name "checkbox-ticket" :value (:pdf ticket)}])
                                 ]
                                ])) tickets-list))
              ]
             (f/submit-button {:class "btn btn-primary"} "Imprimer")))

(defn tickets-view []
  (response (str "<!DOCTYPE html>" 
                        (h/html [:html 
                                 [:head 
                                  "<link rel=\"stylesheet\" href=\"/css/bootstrap.min.css\">"
                                  "<link rel=\"stylesheet\" href=\"/css/bootstrap-responsive.min.css\">"
                                  ]
                                 [:body
                                  
                                  [:div.navbar.navbar-inverse
                                   [:div.navbar-inner 
                                    [:div.container
                                     [:a.brand.large {:href "/"} [:h1 "coloneltrain"]]
                                     ]]]

                                  [:div.container
                                   [:div.row
                                    [:div
                                     
                                     [:div.well 
                                      [:h1 "S&eacute;lection"]
                                      (tickets-selection)]

                                     
                                     [:div.well 
                                      [:h1 "Liste des billets"] 
                                      [:div#tickets-view
                                       (list-tickets-view [])
                                       ]]
                                     ]]]
                                  "<script src='/js/jquery-1.7.1.js'></script><script src='/js/train.js'></script>"
                                  ]
                                 ]))))

(defn print-selected-tickets [request]
  (response (let [{tickets "checkbox-ticket"} (:params request)] 
              (apply merge-tickets (apply retrieve-tickets tickets)))))

(defn list-selected-tickets 
  "List all tickets matching selected month.

   This function is the heart of the application: It extracts the needed parameters from the request
   then delegates work to `train.scrap` functions to retrieve the list of tickets from capitainetrain.com"
  [request]
  (let [{user "login" password "password" month "month"} (:params request)
        session                             (login user password)
        tickets                             (list-tickets session month)]
    (do (log/info "received tickets: " tickets)
        (response 
         (h/html 
          (list-tickets-view tickets))))))

(defn route [request]
  "Routing handler. Delegates to concrete functions according to properties of the request.

   This should probably be refactored to use a real web framework like compojure which 
   provides decent language for routing requests."
  (do (cond (= "/"               (:uri request)) (tickets-view)
            (= "/tickets/list"   (:uri request)) (list-selected-tickets request)
            (= "/tickets/select" (:uri request)) (print-selected-tickets request))))

(defn simple-logging-middleware [app]
  (fn [req]
    (log/info "request: " req)
    (let  [resp  (app req)]
      (do (log/info "response: " resp)
          resp))))

(defn wrap-failsafe [handler]
  "This middleware is used to ensure production-mode is failsafe and
   does not leak too much information to the client.

   When something gets wrong, we simply return a `500 Internal Server Error`
   with a generic message and log the error"
  (fn [req]
    (try
      (handler req)
      (catch Exception e
        (do (log/error e) 
            {:status 500
             :headers {"Content-Type" "text/plain"}
             :body "We're sorry, something went wrong."})))))

(defn wrap-if [handler pred wrapper & args]
  "Applies a `wrapper` to a `handler`, possibly with some `args`, only if some 
   predicate `pred` is true."
  (if pred
    (apply wrapper handler args)
    handler))

(def production?
  "Is the system in production environment?"
  (= "production" (get (System/getenv) "APP_ENV")))

(def development?
  "Is the system in development environment?"
  (not production?))

(def app
  (->  #'route
       (wrap-if development? simple-logging-middleware) 
       (wrap-if production?  wrap-failsafe)
       (wrap-if development? wrap-reload '[train.web train.scrap train.summarize])
       (wrap-params)                     ;; extract parameters from the query
       (wrap-file  "resources/public")   ;; serve static resources from this directory
       (wrap-file-info)                  ;; ???
       (wrap-request-logging)))          ;; log requests

