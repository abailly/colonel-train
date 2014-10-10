(defproject train "0.0.1-SNAPSHOT"
  :description "Cool new Project to do things and stuff
  
   Requires JDK8 to run due to this bug: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=7044060. 
   In lein on windows, JDK must be or java executable defined in profiles.
  
   Looks like capitainetrain moved to JDK8 itself and changed its server certificate to
   something stronger (following release of Apple app?)"
  :local-repo "repository"
  :dependencies [[org.clojure/clojure "1.5.1"]

                 [clj-http "0.7.7"]
                 [clj-tagsoup "0.3.0"]

                 [org.slf4j/slf4j-api "1.6.4"]
                 [org.slf4j/slf4j-log4j12 "1.6.4"]
                 [log4j/log4j "1.2.17"]
                 [org.clojure/tools.logging "0.2.3"]
                 [midje "1.5-alpha8"]
                 [org.clojure/data.json "0.2.2"]
                 [hiccup "1.0.3"]
                 [ring "1.1.8"]
                 [com.duelinmarkers/ring-request-logging "0.2.0"]
                 [clj-time "0.5.1"]]
  :plugins [[lein-marginalia "0.7.1"]
            [lein-ring "0.8.6"]
            [lein-midje "3.0.0"]]
  ;; :jvm-opts ~(vec (map (fn [[key val]] ( str "-D" (name key) "=" val))
  ;;                      {:http.proxyHost "proxy" 
  ;;                       :http.proxyPort "3128" 
  ;;                       :https.proxyHost "proxy" 
  ;;                       :https.proxyPort "3128" })) 
  :ring {:handler train.web/app} 
  :main train.core
  :repositories {"local" ~(str (.toURI (java.io.File. "lib")))}
  )
  
