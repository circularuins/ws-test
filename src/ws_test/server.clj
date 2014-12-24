(ns ws-test.server
  (:require  [org.httpkit.server :refer 
              [with-channel websocket? on-receive send! on-close close run-server]]
             [org.httpkit.timer :refer [schedule-task]]
             [compojure.route :refer [not-found resources]]
             [compojure.handler :refer [site]] ; form, query params decode; cookie; session, etc
             [compojure.core :refer [defroutes GET POST DELETE ANY context]]))

(defn app 
  "単純なレスポンスを返します" 
  [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    "hello HTTP!"})

(defn ws-handler 
  "WebSocketチャネルでの通信を行います"
  [req]
  (with-channel req channel ;;チャネルを取得する
    (on-close channel (fn [status]
                        (println "channel closed"))) ;; セッションが終了した場合
    (if (websocket? channel) ;; セッションが開始された場合でそれがWebSocketの場合
      (do (println "WebSocketのチャネルが生成されました")
      (send! channel "WebSocketのチャネルが生成されました"))
      (println "HTTPチャネルが生成されました") ;; WebSocketではない場合
      )
    (on-receive 
     channel ;; データを受信した場合
     (fn [data]  ; dataはクライアントから送られたデータである。
       (println data)
       ;; send!は(send! channel data close-after-send?)で３つめの引数のclose-after-send?
       ;; はsend!の後にクローズするかしないかのオプションである。
       ;; もし、３つめのオプションを指定しなければHTTPのチャネルではtrueであり、WebSocketで
       ;; はfalseがデフォルトとなる。
       (send! channel (str "一発目のデータを非同期で送ります " data))
       (send! channel (str "二発目のデータを非同期で送ります " data)))))) 

(defn streaming-handler [request]
  "WebSocketのストリーム通信を行います。あるインターバルで指定された回数、サーバーからpushします"
  (with-channel request channel
    (let [count 10 interval 2000]
      (on-close channel (fn [status] (println "チャネルがクローズされました, " status)))
    (loop [id 0]
      (when (< id count) ;; 10回クライアントに送ります。
        (schedule-task 
         (* id interval) ;; 200msごとに通信する。
         (send! channel (str "message from server #" id) false)) ; falseはsend!の後にクローズしない
        (recur (inc id))))
    (schedule-task (+ (* count interval) 1000) (close channel))))) ;; 10秒経ったらクローズします。


(def channel-hub (atom {}))

(defn long-poll-handler 
  "long pollのサンプルです"
  [request]
  (with-channel request channel
    ;; チャネルになにかを保持してイベント発生時にそれをクライアントに送ります。
    ;; atomであるchanell-bubを監視してイベント発生時にクライアントにおくります。
    (swap! channel-hub assoc channel request) 
    (on-close channel (fn [status]
                        ;; チャネルをクローズします。
                        (swap! channel-hub dissoc channel)))))

(defn someevent 
  "イベントのサンプルです。チャネルにイベントを書き込みます。この関数を随時実行するとクライアントにその内容が送られます"
  [data]
  (doseq [channel (keys @channel-hub)]
    (send! 
     channel {:status 200                                                  
              :headers {"Content-Type" "application/json; charset=utf-8"}
              :body data})))

(defonce server (atom nil)) ;; 競合を避けるためatomを使う。

(defn stop-server 
  "msの指定時間を待ってサーバーをgracefulに停止させます。タイムアウトのオプションがなければ即時に停止させます。"
  []
  (when-not (nil? @server)
    (@server :timeout 100)
    (reset! server nil)))


(defn show-landing-page "単純なレスポンスを返します"
  [req]   
  "OK!")

(defroutes all-routes
  (GET "/" [] show-landing-page)
  (GET "/ws" [] ws-handler)     ;; WebSocket通信を行います。
  (GET "/stream" [] streaming-handler) ;; WebSocketのストリーム通信を行います。
  (GET "/poll" [] long-poll-handler) ;; WebSocketのlong pollの通信を行います。
  (GET "/user/:id" [id]
       (str "<h1>Hello user " id "</h1>"))  
  (resources "/")  ;; resources/public以下のhtmlファイルが表示されます。
  (not-found "<p>Page not found.</p>"));; さもなければエラーを返します。

(defn -main 
  "lein runで呼ばれるメインプログラムです"
  [& args]
  (println "starting server ...")
  (reset! server (run-server (site #'all-routes) {:port 3003})))

(comment 
  ;; それぞれのサービスを起動、停止します。
  ;; wscat -c urlでクライアントのテストができます。
  ;; (例) wscat -c ws://localhost:8080

  (reset! server (run-server #'app {:port 3003}))
  ;;wscat -c ws://localhost:8080
  (stop-server)

  (reset! server (run-server #'ws-handler {:port 3003}))
  ;;wscat -c ws://localhost:8080
  (stop-server)

  (reset! server (run-server #'streaming-handler {:port 3003}))
  ;;wscat -c ws://localhost:8080
  (stop-server)

  (reset! server (run-server #'long-poll-handler {:port 3003}))
  ;;wscat -c ws://localhost:8080

(someevent "test")
(someevent "おーい、元気か〜")

  (stop-server)

  (reset! run-server (site #'all-routes) {:port 3003})
  ;;wscat -c ws://localhost:8080/ws
  ;;wscat -c ws://localhost:8080/stream
  ;;wscat -c ws://localhost:8080/poll

  (stop-server))
