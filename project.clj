(defproject my-http-kit "0.1.0-SNAPSHOT"
  :description "FIXME: write this!"
  :url "http://github.com/tokomakoma123/my-http-kit"
  :dependencies [
                 [org.clojure/clojure "1.6.0"]
                 [http-kit "2.1.16"] ; Add to your project.clj.
                 [compojure "1.1.6"]                 
                 [ring/ring "1.2.1"]]
  :cljsbuild {:builds [{}]}
  :main ^{:skip-aot true} ws-test.server)
