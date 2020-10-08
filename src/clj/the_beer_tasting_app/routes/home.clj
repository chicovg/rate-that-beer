(ns the-beer-tasting-app.routes.home
  (:require
   [buddy.hashers :as h]
   [ring.util.http-response :refer [content-type]]
   [ring.util.response :refer [redirect bad-request]]
   [the-beer-tasting-app.db.core :refer [*db*] :as db]
   [the-beer-tasting-app.layout :as layout]
   [the-beer-tasting-app.middleware :as middleware]
   [the-beer-tasting-app.routes.pages :as pages]
   [the-beer-tasting-app.schema :as sc])
  (:use [ring.util.anti-forgery]
        [hiccup.core]))

(defn home-page [request]
  (layout/render request (pages/home-page)))

(defn get-login-page [request]
  (layout/render request (pages/login-page {:errors nil})))

(defn authenticate-user [request]
  (let [{:keys [email pass]} (:params request)
        user (db/get-user-by-email *db* {:email email})]
    (if (h/check pass (:pass user))
      (-> (redirect "/user/beers")
          (assoc-in [:session] (-> (:session request)
                                   (assoc :identity (:id user))
                                   (assoc :first_name (:first_name user)))))
      (layout/render request (pages/login-page {:errors ["Invalid email or password"]})))))

(defn get-profile-form-page
  ([request] (layout/render request (pages/profile-form-page {:errors []})))
  ([request errors] (layout/render request (pages/profile-form-page {:errors errors}))))

(defn create-profile [request]
  (let [user (:params request)
        session (:session request)
        next-url (get-in request [:params :next] "/user/beers")]
    (let [schema-errors (sc/get-schema-errors user sc/user-schema)
          user-by-email (db/get-user-by-email user)]
      (if (empty? schema-errors)
        (if (not user-by-email)
          (if (= (:pass user) (:confirm-pass user))
            (let [encrypted-pass (h/encrypt (:pass user))
                  identity (-> (db/create-user! *db* (assoc user :pass encrypted-pass))
                               first
                               :id)
                  updated-session (-> session
                                      (assoc :identity identity)
                                      (assoc :first_name (:first_name user)))]
              (-> (redirect next-url)
                  (assoc :session updated-session)))
            (get-profile-form-page request ["Passwords do not match"]))
          (get-profile-form-page request ["A user already exists with that email address"]))
        (get-profile-form-page request schema-errors)))))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]
   ["/login" {:get get-login-page
              :post authenticate-user}]
   ["/profile" {:get get-profile-form-page
                :post create-profile}]])
