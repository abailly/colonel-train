(ns train.scrap
  "Scrap operations for http://capitainetrain.com
   
   * [clj-http](https://github.com/dakrone/clj-http) HTTP client library"

  (:require [pl.danieljanus.tagsoup :as t])
  (:require [clj-http.client :as client])
  (:require [clj-http.cookies :as cookies])
  (:require [clojure.data.json :as json :refer [ read-str write-str]]))


(declare signin)
(declare create-session)
(declare do-login)

(defn login 
  "login into the system given some credentials.

  Login actually entails a sequence of requests to the application.
  First we need to retrieve a token through the signin request


    -> GET https://www.capitainetrain.com/signin HTTP/1.1

    <- 304 Not Modified <meta content=\"authenticity_token\"
    name=\"csrf-param\" /> <meta
    content=\"vUuLfW2pU6Joa+wYZ2+CQkm+ahtF9NDLcwUl6wNU9co=\"
    name=\"csrf-token\" />


  Then authenticates to the server and create a session:

     -> POST https://www.capitainetrain.com/api/v2/sessions
     X-CSRF-Token:vUuLfW2pU6Joa+wYZ2+CQkm+ahtF9NDLcwUl6wNU9co=

     {\"session\":{\"email\":\"email@email.com\",\"password\":\"pass\",\"facebook_token\":null,\"facebook_id\":null,\"invitation_token\":null,\"user_id\":null,\"base\":null,\"user\":null}}

      <- 201 Created
      cookie=remember_user_token:BAhbB1sGaQIPiEkiGUYyQ1JTa241c3pHdVJCZHhYS2JCBjoGRUY=--ce842a68a3b2d81824ac7a5cf25e7ea1dff893d7

  This returns a JSON description of the users and its preferences.

  Finally login:

    -> POST https://www.capitainetrain.com/login
    cookie=remember_user_token:BAhbB1sGaQIPiEkiGUYyQ1JTa241c3pHdVJCZHhYS2JCBjoGRUY=--ce842a68a3b2d81824ac7a5cf25e7ea1dff893d7

    Sent Form Data redirect:true password:
    email:email@email.com
    authenticity_token:vUuLfW2pU6Joa+wYZ2+CQkm+ahtF9NDLcwUl6wNU9co=

    <- 302 Found

  Note the authenticty token which is the same value than the
  X-CSRF-Token sent in the create session request. Response body is 
  empty"

  [useremail password]
  (let [auth-token  (signin)
        session   (create-session auth-token useremail password)
        session   (do-login session)] 
    session))


(defn do-login
  [session]
  (let [login (client/post "https://www.capitainetrain.com/login"
                           { :cookie-store (:cookie-store session)
                            :insecure? true 
                            :form-params {:redirect true
                                          :password ""
                                          :email "arnaud.oqube@gmail.com"
                                          :authenticity_token (:auth-token session)}})]
    session))

(defn create-session 
  "authenticates user and initializes a session using a token"
  [auth-token useremail password]
  (let [cs (cookies/cookie-store) 
        session-data (client/post "https://www.capitainetrain.com/api/v2/sessions"
                                  {:throw-entire-message? true
                                   :insecure? true 
                                   :headers {"X-CSRF-Token" auth-token}
                                   :cookie-store cs
                                   :content-type :json
                                   :body ( json/write-str {:session {:email            useremail
                                                                     :password         password 
                                                                     :facebook_token   nil
                                                                     :facebook_id      nil
                                                                     :invitation_token nil
                                                                     :user_id          nil
                                                                     :base             nil
                                                                     :user             nil}})})]
    
    {:cookie-store cs
     :auth-token auth-token
     :user (json/read-str (:body session-data) :key-fn keyword)})
  )

(defn signin 
  "extracts an authentication token from /signin request.  The
  authentication token is the content of meta header 'csrf-param'."
  []
  (let [page       (client/get "https://www.capitainetrain.com/signin" {:insecure? true :decode-body-headers true})
        auth-token (:content  (t/attributes (first (filter #(= "csrf-token" (second (find  (t/attributes %) :name))) 
                                                           (filter #(= (first %) :meta) 
                                                                   (t/children (first (t/children 
                                                                                             (t/parse-string (:body page))))))))))]
    auth-token
    ))

(defn make-ticket-list
  "generates a list of all printed instructions for all folders in the
  tickets-info.

   Takes as input a complex structure as produced by API pnrs
   resource: A map with among many things details of the trips and
   printing instructions of the tickets. Returns a structure
   containing the following fields:

   * departure, arrival: name of departure and arrival stations
   * departure_date,arrival_date: dates for departure/arrival 
   * pnr: id of printed instructions 
   * pdf: link to PDF for the ticket"

  [tickets-info]
  (letfn [( selectid 
            [id coll] 
            (filter #(= (:id %) id) coll))

          (name-of 
            [singleton] 
            (:name (first singleton)))
          ]

    (for  [ {:keys [folder_ids id formatted_print_instructions]}    (:pnrs tickets-info)
            folder                                                  folder_ids
            {:keys [departure_station_id departure_date arrival_station_id arrival_date cents]} (selectid folder (:folders tickets-info))]

      {:departure      (name-of (selectid departure_station_id (:stations tickets-info))) 
       :departure_date departure_date 
       :arrival        (name-of (selectid arrival_station_id (:stations tickets-info))) 
       :arrival_date   arrival_date 
       :price          (/ cents 100.0)
       :pnr            id 
       :pdf            (second (re-find #".*href='([^']+)" formatted_print_instructions))})))

(defn list-tickets 
  "List past tickets.

   Uses URL `https://www.capitainetrain.com/api/v2/pnrs` to retrieve
  the list of past tickets for the given session. If called with a
  date parameter then this date is passed as `date` to the URL and
  retrieves the tickets up-to this date.  All the tickets for the
  month starting with given date are returned (full month? sliding
  window?). Without date, all tickets for the last month are returned.

  The returned structure contains the following fields:

  * folders: a list of travels, each travel being part of a *pnr* (whatever that means)
  * passengers: identifies all passengers taking part in the travels
  * addresses: empty, don't know
  * segments: individual travels? contains seats and train identification
  * fares: types of fares?
  * conditions: details of fares, descriptions with html fragments
  * tickets: point at segments and pnr
  * pnrs: a list of pnr, the actual ticket to be printed. `formatted_print_instructions` contains
    html fragment with an anchor pointing to PDF for ticket

  This function produces a simpler list of e-ticket, along with details of 
  stations and dates for each ticket."

  [session date]
  (let [cs (:cookie-store session)
        url (if (nil? date) "" (str  "?date=" date))
        pnrs (client/get (str "https://www.capitainetrain.com/api/v2/pnrs" url) 
                         {:insecure? true 
                          :cookie-store cs})]
    (print pnrs) 
    (make-ticket-list (json/read-str (:body pnrs) :key-fn keyword)))
)


(defn sample-tickets "a sample tickets"  [] (slurp "tickets"))

(defn sample-users "a sample users"  [] (slurp "users"))
