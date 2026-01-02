(ns frontend.ui-resize
  "Native ResizeObserver-based resize context for React"
  (:require [rum.core :as rum]))

(defonce ^:private resize-context (js/React.createContext #js {}))

(def ^:private ResizeContext (gobj/get resize-context "Provider"))

(defprotocol IResize
  (subscribe! [this callback])
  (unsubscribe! [this callback])
  (get-size [this]))

(defn- use-element-size [ref]
  (let [size (atom nil)
        observer (atom nil)]
    (rum/useEffect!
      (fn []
        (when-let [el (and ref (aget ref "current"))]
          (let [obs (js/ResizeObserver.
                      (fn [entries]
                        (when-let [entry (first entries)]
                          (let [rect (.getBoundingClientRect entry)
                                new-size #js {:width (.-width rect) :height (.-height rect)}]
                            (reset! size new-size)))))]
            (reset! observer obs)
            (.observe obs el)
            #(.disconnect obs))))
      #js [ref])
    size))

(rum/defc ResizeProvider
  "A native ResizeProvider that uses ResizeObserver instead of react-resize-context"
  [children]
  (let [children-array (if (sequential? children) children [children])]
    [:> ResizeContext
     #js {:children children-array}]))

(rum/defc ResizeConsumer
  "A native ResizeConsumer that works with the native ResizeProvider"
  [{:keys [render]}]
  (let [context (rum/useContext resize-context)]
    (when render
      (render))))
