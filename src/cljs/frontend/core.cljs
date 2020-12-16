(ns frontend.core
  (:require
   [reagent.core :as reagent :refer [atom]]
   [reagent.dom :as rdom]
   [reagent.session :as session]
   [reitit.frontend :as reitit]
   [reitit.frontend.easy :as rfe]
   [clerk.core :as clerk]
   [accountant.core :as accountant]
   [cljs.core.async :refer [<!]]
   [cljs-http.client :as http]
   [ajax.core :as ajax])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;;  :id 11
;;  :fio "Ivan Petrovich Bobrov"
;;  :adress "Moscow, Tverskaya str. 34-3"
;;  :birthday "11-04-1976"
;;  :police_id 3235235
;;  :sex "male"

(def patients-data (atom nil))
(def response-data (atom nil))

(def pdata (atom {:id 0
                  :fio "",
                  :adress "",
                  :birthday "",
                  :police_id 0,
                  :sex ""}))


;; -------------------------
;; Routes

(def router
  (reitit/router
   [["/" :index]
    ["/create-patient" :create]
    ["/patients"
     ["/:patient-id" :patient]]]))

(defn path-for [route & [params]]
  (if params
    (:path (reitit/match-by-name router route params))
    (:path (reitit/match-by-name router route))))


;; -- AJAX requests


(defn errors-handler []
  (js/setTimeout #(reset! response-data nil) 3000)
  [:div
   [:h3 {:style {:color "red"}} (:message @response-data)]])

(defn err-handler [resp]
  (reset! response-data {:message (str "Ошибка: " (:status-text resp))}))

;; Controllers


(defn get-all-patients []
  (ajax/GET "http://localhost:3000/"
   {:handler (fn [resp]  (reset! patients-data resp))
    :error-handler err-handler
    :format (ajax/json-request-format)
    :response-format :json
    :keywords? true}))

(defn create-patient [data]
  (ajax/POST "http://127.0.0.1:3000/"
    {:params data
    :handler (fn [resp]
               (reset! response-data {:message (str "Успешно!")})
               (accountant/navigate! (path-for :index)))
    :error-handler err-handler
    :format (ajax/json-request-format)
     :response-format :json
     :keywords? true}))

(defn update-patient [data]
  (let [id (:id data)
        url (str "http://localhost:3000/" id)
        data-wo-id (dissoc data :id)]
    (ajax/PUT url
      {:params data-wo-id
       :handler (fn [resp] (reset! response-data {:message (str "Успешно!")}))
       :error-handler err-handler
       :format (ajax/json-request-format)
       :response-format :json
       :keywords? true})))

(defn delete-patient [id]
  (let [url (str "http://localhost:3000/" id)]
    (ajax/DELETE url
     {:handler (fn [resp]
                 (reset! response-data {:message (str "Успешно!")})
                 (accountant/navigate! (path-for :index)))
      :error-handler err-handler
      :format (ajax/json-request-format)
      :response-format :json
      :keywords? true})))


;; Views

(defn update-patient-view []
  (let [{:keys [fio adress sex birthday police_id]} @pdata ;; data is list (data)
        ]
    [:div
     [:div {:style {:color "red"}} "Edit info here"]
     [:div
      [:label "FIO"]
      [:input {:type "text"
               :value fio
               :on-change #(swap! pdata assoc :fio (-> % .-target .-value))}]]
     [:div
      [:label "Adress"]
      [:input {:type "text"
               :value adress
               :on-change #(swap! pdata assoc :adress (-> % .-target .-value))}]]
     [:div
      [:label "Birthday"]
      [:input {:type "text"
               :value birthday
               :on-change #(swap! pdata assoc :birthday (-> % .-target .-value))}]]
     [:div
      [:label "Sex"]
      [:input {:type "text"
               :value sex
               :on-change #(swap! pdata assoc :sex (-> % .-target .-value))}]]
     [:div
      [:label "Police id"]
      [:input {:type "text"
               :value police_id
               :on-change #(swap! pdata assoc :police_id (-> % .-target .-value))}]]
     [:hr]
     [:button {:on-click #(update-patient @pdata)} "Submit"]
     [:button {:on-click #(delete-patient (:id @pdata))} "Delete"]]))

(defn create-patient-view []
  [:div
   [:div
    [:label "FIO"]
    [:input {:type "text"
             :on-change #(swap! pdata assoc :fio (-> % .-target .-value))}]]
   [:div
    [:label "Adress"]
    [:input {:type "text"
             :on-change #(swap! pdata assoc :adress (-> % .-target .-value))}]]
   [:div
    [:label "Birthday"]
    [:input {:type "text"
             :on-change #(swap! pdata assoc :birthday (-> % .-target .-value))}]]
   [:div
    [:label "Sex"]
    [:input {:type "text"
             :on-change #(swap! pdata assoc :sex (-> % .-target .-value))}]]
   [:div
    [:label "Police id"]
    [:input {:type "text"
             :on-change #(swap! pdata assoc :police_id (-> % .-target .-value))}]]

   [:button {:on-click #(create-patient (dissoc @pdata :id))} "Submit"]])

(defn patient-view []
  (let [{:keys [fio adress sex birthday police_id]} @pdata]
    [:div
     [:div [:span "FIO: "] [:span {:style {:font-weight "bold"}} fio]]
     [:div [:span "Adress: "] [:span {:style {:font-weight "bold"}} adress]]
     [:div [:span "Birthday: "] [:span {:style {:font-weight "bold"}} birthday]]
     [:div [:span "Sex: "] [:span {:style {:font-weight "bold"}} sex]]
     [:div [:span "Police id: "] [:span {:style {:font-weight "bold"}} police_id]]]))

(defn all-patients-view []
  [:ul
   (for [patient @patients-data]
     ^{:key patient} [:li
                      [:a {:href (path-for
                                  :patient
                                    {:patient-id (:id patient)})} "Patient FIO: " (:fio patient)]])])



;; Pages


(defn home-page []
  (get-all-patients) ;; load data on the first run
  (fn []
    [:span.main
     [:h2 "Welcome to Johns Hopkins Hospital"]
     [all-patients-view]]))

(defn create-patient-page []
  (fn []
    [:div
     [create-patient-view]])) ;; open on new page

(defn patient-page []
  "Take id from session and get patient from patients-data atom and update pdata atom with it"
  (let [routing-data (session/get :route)
        patient (get-in routing-data [:route-params :patient-id]) ;; patient string "2"
        patient-num (js/parseInt patient 10)
        patient-data (first (filter #(= (:id %) patient-num)  @patients-data))]
    (reset! pdata patient-data)
    (fn []
      [:div
       [:div.main
        [patient-view]
        [:hr]
        [update-patient-view]
        [:p [:a {:href (path-for :index)} "Back"]]]])))


;; -------------------------
;; Translate routes -> page components


(defn page-for [route]
  (case route
    :index #'home-page
    :patient #'patient-page
    :create #'create-patient-page))


;; -------------------------
;; Page mounting component


(defn current-page []
  (fn []
    (let [page (:current-page (session/get :route))]
      [:div
       [:header
        [:p
         [:a {:href (path-for :index)} "Home"] " | "
         [:a {:href (path-for :create)} "Create patient"]]]
       [errors-handler]
       [page]])))

;; -------------------------
;; Initialize app

(defn mount-root []
  (rdom/render [current-page] (.getElementById js/document "app")))

(defn init! []
  (clerk/initialize!)
  (accountant/configure-navigation!
   {:nav-handler
    (fn [path]
      (let [match (reitit/match-by-path router path)
            current-page (:name (:data  match))
            route-params (:path-params match)]
        (reagent/after-render clerk/after-render!)
        (session/put! :route {:current-page (page-for current-page)
                              :route-params route-params})
        (clerk/navigate-page! path)))
    :path-exists?
    (fn [path]
      (boolean (reitit/match-by-path router path)))})
  (accountant/dispatch-current!)
  (mount-root))
