(ns devald.aws
  (:require
    [clojure.data.zip :as zf]
    [clojure.data.zip.xml :as zip.xml]
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [clojure.string :as str]
    [clojure.zip :as zip]
    [cognitect.aws.client.api :as aws.api]
    [puget.printer :as puget]))

(set! *print-namespace-maps* false)

(defn- clojure-core-based-puget-options
  "NOTE: clojure.core/*print-level* doesn't have an equivalent in Puget 1.3.4."
  []
  {:seq-limit      *print-length*
   :coll-limit     *print-length*
   :namespace-maps *print-namespace-maps*})

(defn puget-cprint [value & [opts]]
  ((requiring-resolve 'puget.printer/cprint)
   value
   ;; Puget doesn't consider some *print-** settings,
   ;; so we translate their values to the corresponding
   ;; puget settings at the time of the print call.
   (merge (clojure-core-based-puget-options) opts)))

;; Colored pretty-printing of the request/response schema by `ginoco.aws/doc`
(alter-var-root #'cognitect.aws.client.api/pprint-ref
                (constantly #'puget-cprint))

(def kw->sym->str (comp str symbol keyword))

;;; Docs
(defn api-docs
  "Read the AWS API docs EDN file from the classpath.
   It's useful in a Babashka context, where the `aws.api/ops` function was missing."
  [api]
  (-> api kw->sym->str
      (->> (format "cognitect/aws/%s/docs.edn"))
      io/resource slurp edn/read-string
      (->> (into {} (map (fn [[k v]] [k (assoc v :name (symbol k))]))))))

(defn- parse-doc
  "Parse a HTML `:documentation` string into an XML tree."
  [html-doc]
  (-> html-doc
      ; wrap with <div>, so all paragraphs are parsed, not just the 1st one
      (->> (str "<div>")) (str "</div>")
      ((requiring-resolve 'clojure.data.xml/parse-str))))

(defn- html-node->text
  "Simplifies `clojure.data.xml.node.Element`s as vectors of text."
  [e]
  (cond
    (-> e :tag #{:div :p :ul})
    (into (vec (:content e)) ["\n\n"])

    (-> e :tag (= :note))
    (vec (concat ["=== Note ===\n"] (:content e) ["============\n\n"]))

    (-> e :tag (= :li))
    (vec (concat ["* "] (:content e)))

    (-> e :tag (= :a))
    (vec (concat ["["] (:content e) ["][]"]))               ; Omit [:attrs :href]

    (-> e :tag #{:i :code})
    (vec (concat ["`"] (:content e) ["`"]))

    :else e))

(defn- links
  "Collect link (aka. anchor) XML nodes."
  [xml-doc]
  (-> xml-doc zip/xml-zip zf/descendants
      (->> (filter (comp #{:a} :tag zip/node))
           (map zip/node))))

(defn- render-link
  "Render a link (aka. anchor) XML node as a markdown reference-style link.
   https://www.markdownguide.org/basic-syntax/#reference-style-links"
  [anchor-node]
  (str "[" (-> anchor-node zip/xml-zip zip.xml/text) "]:"
       "\n"
       (-> anchor-node :attrs :href)
       "\n"))

(defn- compact-text
  "Clean up trailing whitespace and deduplicate multiple whitespaces."
  [txt]
  (-> txt str/trim (str/replace #" \." ".") (str/replace #"  +" " ")))

(defn- wrap-line
  "Source: https://rosettacode.org/wiki/Word_wrap#Clojure"
  ([text*] (wrap-line (or pp/*print-right-margin* 80) text*))
  ([size text]
   (pp/cl-format nil (str "~{~<~%~1," size ":;~A~> ~}") (str/split text #" "))))

(defn doc->txt
  "Renders a HTML `:documentation` returned by `aws.api/ops` as text."
  [html-doc]
  (let [xml-doc (-> html-doc parse-doc)]
    (-> xml-doc
        (->> (clojure.walk/prewalk html-node->text) flatten (apply str)
             compact-text wrap-line)
        (str "\n\n"
             (-> xml-doc links (->> (map render-link) (str/join "\n")))))))

(defn doc
  "Prints the documentation for the specified aws api op."
  [api-ident|api-client op]
  (println (or (-> api-ident|api-client :api keyword
                   (or api-ident|api-client)
                   (some-> api-docs op
                           (update :documentation doc->txt)
                           aws.api/doc-str))
               (str "No docs for " (name op)))))

(defn doc*
  "Prints the documentation for the specified ginoco-aws-request."
  ([req|api]
   (if (keyword? req|api)
     (-> req|api api-docs
         (update-vals :documentationUrl))
     (doc (-> req|api :aws.client/opts :api) (-> req|api :aws/op))))
  ([api op]
   (println (or (some-> api api-docs op
                        (update :documentation doc->txt)
                        aws.api/doc-str)
                (str "No docs for " (name op))))))
