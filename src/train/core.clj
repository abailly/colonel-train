(ns train.core
  "main entry point of coloneltrain application


   * ()[http://mmcgrana.github.io/2010/07/develop-deploy-clojure-web-applications.html]: Provided 
     useful information on how to deploy a clojure web app
   * ()[http://www.agilogy.com/blog/create-a-basic-web-application-in-clojure.html]: Another
     blog entry on clojure web development"
  (:use train.web)
  (:require  [ring.adapter.jetty :as j])
  )


(defn -main []
  "Main application, executable with `lein run`

   This main is duplicate with what ring handler provides"
  (let [port-env (get (System/getenv) "PORT")
        port     (if (nil? port-env) 3000 (Integer/parseInt port-env))]
    (j/run-jetty app {:port port})))

