(defproject chatter-box "0.1.0-SNAPSHOT"
  :description "Example chat program to illustrate core.async and enfocus."
  :url "http://ckirkendall.github.io/enfocus-site"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[enfocus "2.0.0-SNAPSHOT"]
                 [org.clojure/clojure "1.5.1"]
                 [compojure "1.1.5"]
                 [org.clojure/core.async "0.1.0-SNAPSHOT"]
                 [ring "1.2.0"]
                 [com.cemerick/clojurescript.test "0.0.5-SNAPSHOT"]
                 [com.cemerick/piggieback "0.1.0"]
                 [http-kit "2.1.7"]
                 [org.clojure/core.match "0.2.0-rc5"]
                 [org.clojure/clojurescript "0.0-1859"]]
  :resource-paths ["resources"]
  :source-paths ["src/client" "src/server"
               ;add source paths for cross compiled code
               "target/generated/clj" "target/generated/cljs"]
  :test-paths ["test/client" "test/server"
               ;add source paths for cross compiled code
               "target/generated/test-clj" "target/generated/test-cljs"]
  :plugins [[lein-cljsbuild "0.3.3-SNAPSHOT"]
            [com.keminglabs/cljx "0.3.0"]]
  :repl-options {:nrepl-middleware [cemerick.piggieback/wrap-cljs-repl
                                    cljx.repl-middleware/wrap-cljx]}
  ; Enable the lein hooks for: clean, compile, test, and jar.
  ;:hooks [leiningen.cljsbuild cljx.hooks]
  :main chatter-box.core
  :cljsbuild {
    ; Configure the REPL support; see the README.md file for more details.
    :repl-listen-port 9000
    :repl-launch-commands {
        "phantom" ["phantomjs"
                   "phantom/repl.js"
                   :stdout ".repl-phantom-out"
                   :stderr ".repl-phantom-err"]}
    :test-commands
      ; Test command for running the unit tests in "test-cljs" (see below).
      ;     $ lein cljsbuild test
      {"unit" ["phantom/runner.js"
               "resources/private/js/unit-test.js"]}
    :builds 
      [{:id "dev"
        :source-paths ["src/client" "target/generated/cljs"]
        :compiler {:output-to "resources/public/js/main-debug.js"
                   :optimizations :whitespace
                   :pretty-print true}}
       ; This build has the highest level of optimizations, so it is
       ; efficient when running the app in production.
       {:id "prod"
        :source-paths ["src/client" "target/generated/cljs"]
        :compiler {:output-to "resources/public/js/main.js"
                   :optimizations :advanced
                   :pretty-print false}}
       ; This build is for the ClojureScript unit tests that will
       ; be run via PhantomJS.  See the phantom/unit-test.js file
       ; for details on how it's run.
       {:id "test"
        :source-paths ["src/client" "test/client" "target/generated/test-cljs" "target/generated/cljs"]
        :compiler {:output-to "resources/private/js/unit-test.js"
                   :optimizations :whitespace
                   :pretty-print true}}]}
  
  ;cross compiling code for both clojurescript and clojure
  :cljx {:builds [{:source-paths ["src/common"]
                   :output-path "target/generated/clj"
                   :rules :clj}
                  {:source-paths ["test/common"]
                   :output-path "target/generated/test-clj"
                   :rules :clj}
                  {:source-paths ["src/common"]
                   :output-path "target/generated/cljs"
                   :rules :cljs}
                  {:source-paths ["test/common"]
                   :output-path "target/generated/test-cljs"
                   :rules :cljs}]}
  :ring {:handler chatter-box.core})
