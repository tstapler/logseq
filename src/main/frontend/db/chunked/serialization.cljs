(ns frontend.db.chunked.serialization
  "Serialization utilities for chunked database storage.

   Uses Transit MessagePack format with write caching enabled for optimal
   compression of repeated strings (page names, block UUIDs, property keys).

   Benefits vs Transit JSON:
   - 15-30% smaller payloads
   - 17-100% faster encoding/decoding
   - Built-in dictionary encoding via Transit caching"
  (:require [cognitect.transit :as transit]
            [lambdaisland.glogi :as log]))

;; =============================================================================
;; Transit Writer/Reader Creation
;; =============================================================================

(defn create-writer
  "Create a Transit MessagePack writer with caching enabled.

   Args:
   - options: {:caching? true} (optional, defaults to true)

   Returns: Transit writer instance"
  [& [{:keys [caching?] :or {caching? true}}]]
  (let [opts (if caching?
               {}  ; Default Transit opts enable caching
               {:cache false})]
    (transit/writer :msgpack opts)))

(defn create-reader
  "Create a Transit MessagePack reader.

   Args:
   - options: {} (optional)

   Returns: Transit reader instance"
  [& [_options]]
  (transit/reader :msgpack))

;; =============================================================================
;; Core Serialization Functions
;; =============================================================================

(defn serialize
  "Serialize data to Transit MessagePack format.

   Args:
   - data: Any Clojure data structure
   - options: {:caching? true} (optional, defaults to true)

   Returns: Uint8Array (Transit MessagePack bytes)

   Note: Enables write caching by default for dictionary compression"
  [data & [options]]
  (try
    (let [writer (create-writer options)
          transit-bytes (transit/write writer data)]
      ;; Convert to Uint8Array for consistency
      (if (instance? js/Uint8Array transit-bytes)
        transit-bytes
        (js/Uint8Array. transit-bytes)))
    (catch :default e
      (log/error :serialization/serialize "Serialization failed:" e)
      (throw (js/Error. (str "Transit MessagePack serialization failed: " (.-message e)))))))

(defn deserialize
  "Deserialize Transit MessagePack data.

   Args:
   - bytes: Uint8Array | ArrayBuffer | Buffer

   Returns: Clojure data structure

   Throws:
   - If data is not valid Transit MessagePack
   - If data is corrupted"
  [bytes]
  (try
    (let [reader (create-reader)
          ;; Ensure we have Uint8Array
          data (if (instance? js/Uint8Array bytes)
                 bytes
                 (js/Uint8Array. bytes))]
      (transit/read reader data))
    (catch :default e
      (log/error :serialization/deserialize "Deserialization failed:" e)
      (throw (js/Error. (str "Transit MessagePack deserialization failed: " (.-message e)))))))

;; =============================================================================
;; Chunk-Specific Serialization
;; =============================================================================

(defn serialize-chunk
  "Serialize a chunk record to Transit MessagePack.

   Args:
   - chunk: PageChunk | JournalChunk | MetadataChunk | ConfigChunk record
   - options: {:caching? true} (optional)

   Returns: Uint8Array

   Note: Chunk records are converted to plain maps for serialization"
  [chunk & [options]]
  (try
    ;; Convert record to map for Transit serialization
    (let [chunk-map (into {} chunk)]
      (serialize chunk-map options))
    (catch :default e
      (log/error :serialization/serialize-chunk "Chunk serialization failed:" e)
      (throw (js/Error. (str "Chunk serialization failed: " (.-message e)))))))

(defn deserialize-chunk
  "Deserialize a chunk from Transit MessagePack.

   Args:
   - bytes: Uint8Array or ArrayBuffer
   - chunk-type: :page | :journal | :metadata | :config

   Returns: Chunk record of appropriate type

   Note: Returns a plain map (not a record) since we don't enforce record types"
  [bytes chunk-type]
  (try
    (let [chunk-map (deserialize bytes)]
      ;; Validate expected keys based on chunk type
      (case chunk-type
        :page
        (do
          (when-not (contains? chunk-map :page-name)
            (throw (js/Error. "Invalid page chunk: missing :page-name")))
          chunk-map)

        :journal
        (do
          (when-not (contains? chunk-map :year-month)
            (throw (js/Error. "Invalid journal chunk: missing :year-month")))
          chunk-map)

        :metadata
        (do
          (when-not (contains? chunk-map :schema-version)
            (throw (js/Error. "Invalid metadata chunk: missing :schema-version")))
          chunk-map)

        :config
        (do
          (when-not (contains? chunk-map :built-in-pages)
            (throw (js/Error. "Invalid config chunk: missing :built-in-pages")))
          chunk-map)

        ;; Unknown type - just return the map
        chunk-map))
    (catch :default e
      (log/error :serialization/deserialize-chunk "Chunk deserialization failed:" e)
      (throw (js/Error. (str "Chunk deserialization failed: " (.-message e)))))))

;; =============================================================================
;; JSON Fallback (for backwards compatibility)
;; =============================================================================

(defn serialize-json
  "Fallback: serialize to Transit JSON format.

   Args:
   - data: Any Clojure data structure
   - options: {:caching? true} (optional)

   Returns: String (Transit JSON)"
  [data & [{:keys [caching?] :or {caching? true}}]]
  (try
    (let [opts (if caching? {} {:cache false})
          writer (transit/writer :json opts)]
      (transit/write writer data))
    (catch :default e
      (log/error :serialization/serialize-json "JSON serialization failed:" e)
      (throw (js/Error. (str "Transit JSON serialization failed: " (.-message e)))))))

(defn deserialize-json
  "Fallback: deserialize Transit JSON data.

   Args:
   - json-str: String

   Returns: Clojure data structure"
  [json-str]
  (try
    (let [reader (transit/reader :json)]
      (transit/read reader json-str))
    (catch :default e
      (log/error :serialization/deserialize-json "JSON deserialization failed:" e)
      (throw (js/Error. (str "Transit JSON deserialization failed: " (.-message e)))))))

;; =============================================================================
;; Format Detection
;; =============================================================================

(defn detect-format
  "Detect whether data is Transit MessagePack or JSON.

   Args:
   - bytes: Uint8Array | String

   Returns: :msgpack | :json | :unknown

   Strategy:
   - If string, assume JSON
   - If Uint8Array, check first byte (MessagePack starts with specific markers)
   - Transit JSON strings typically start with '[' or '{'
   - MessagePack binary starts with various markers (0x80-0x8f for fixmap, etc.)"
  [bytes]
  (cond
    ;; String → JSON
    (string? bytes)
    :json

    ;; Uint8Array/Buffer → Check MessagePack markers
    (or (instance? js/Uint8Array bytes)
        (instance? js/ArrayBuffer bytes)
        (and (exists? js/Buffer) (js/Buffer.isBuffer bytes)))
    (let [first-byte (if (instance? js/Uint8Array bytes)
                       (aget bytes 0)
                       (aget (js/Uint8Array. bytes) 0))]
      ;; MessagePack format markers (common first bytes)
      ;; fixmap: 0x80-0x8f, fixarray: 0x90-0x9f, fixstr: 0xa0-0xbf
      ;; nil: 0xc0, false: 0xc2, true: 0xc3
      ;; map16: 0xde, map32: 0xdf, array16: 0xdc, array32: 0xdd
      (if (or (and (>= first-byte 0x80) (<= first-byte 0xbf))
              (= first-byte 0xc0) (= first-byte 0xc2) (= first-byte 0xc3)
              (= first-byte 0xdc) (= first-byte 0xdd)
              (= first-byte 0xde) (= first-byte 0xdf))
        :msgpack
        :unknown))

    :else
    :unknown))

;; =============================================================================
;; Size Estimation
;; =============================================================================

(defn estimate-serialized-size
  "Estimate the serialized size of data.

   Args:
   - data: Any Clojure data structure
   - format: :msgpack | :json

   Returns: Long (approximate bytes)

   Note: This is an approximation for planning purposes"
  [data format]
  (try
    (case format
      :msgpack
      (let [serialized (serialize data {:caching? true})]
        (.-length serialized))

      :json
      (let [serialized (serialize-json data {:caching? true})]
        (.-length serialized))

      0)
    (catch :default e
      (log/error :serialization/estimate-size "Size estimation failed:" e)
      0)))
