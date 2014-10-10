(ns train.summarize
  "This module specializes in producing a summary of tickets from a list of PDF urls.
   This summary takes the form of a single PDF merging all the tickets in one.
   
   * https://github.com/mattdeboard/clj-itext: Tentative interface for using iText from Clojure                          
   * [PDFSam Command-line documentation](http://www.pdfsam.org/uploads/pdfsam-1.5.0e-tutorial.pdf)                       
   * [Using iText to concat PDF Files](http://viralpatel.net/blogs/itext-tutorial-merge-split-pdf-files-using-itext-jar/)"
  (:require [clojure.java.shell :as sh]
            [clj-http.client :as client] 
            [clojure.tools.logging :as log])
  (:import  [java.io File])
  )

;; # Downloading

(defn download 
  "Download a single PDF given a URL and put the content in a temporary file"
  [url]
  (let [output      (File/createTempFile "download" ".pdf")]
    (do
      (log/info "downloading " url " to " output) 
      (with-open [w (clojure.java.io/output-stream output)]
        (.write w (:body (client/get url {:as :byte-array}))))
      output)
    ))

(defn retrieve-tickets 
  "Retrieve all the tickets from URLs given in the parameter list.

   Duplicates URLs are removed, files are downloaded and saved to a temporary location
   and a list of those files is returned."
  [ & ticket-urls]
  (map  #'download (set ticket-urls)))


;; # Merging PDFs
;; 
;; Merging PDF is surprisingly difficult to do. The main library in JAva to manipulate PDFs is 
;; iText which appears to be a real PITA to work with, its API being intricate and difficult 
;; to understand. To make things simpler and have something working faster I have reused an
;; existing program, called PDFSam, which actually uses iText and a bunch of other libraries
;; under the hood to do a lot of fancy operations on PDF files. 
;;
;; I had a hard time to figure out the command-line utility `console-run.bat` (or `.sh`) that 
;; comes packaged with it has a very strict command-line argumetns parsing logic which requires
;; the command to run to be *at end* of arguments list.

(defn merge-tickets
  "Merge several tickets' PDF into a single PDF

   This function tries to leverage PDFSAM software:
   http://www.pdfsam.org/. Actually this fucking shit requires
   truckload of jars to work, does some magic with classpath to load
   its dependencies and does a System.exit when something goes wrong
   which kills the nrepl server it is running in! I would have loved to use the jar files
   directly but I had to resort to invoking the command-line in a shell.

   The pdfs arguments must be File object representing PDFs. If they are not absolute, they are assumed
   to be relative to the current directory and an abolute path is extracted. 
   All the PDFs are then concatenated into a single big PDF.

   Requires a correct `JAVA_HOME` to be set in the environment.
   
   This function returns the output file."
  [& pdfs]
  (let [output (File/createTempFile "merge" ".pdf")
        args `[ "D:\\Program Files\\PDF Split And Merge Basic\\bin\\run-console.bat" 
                "-o" ~(.getAbsolutePath output) 
                ~@(mapcat #(list "-f" (.getAbsolutePath %)) pdfs) 
                "-overwrite" "concat"]]
    (sh/with-sh-dir "D:\\Program Files\\PDF Split And Merge Basic\\bin" 
      (do (apply sh/sh args)
          output))
    )
  )
