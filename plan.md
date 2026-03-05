# Plan

1. **Understand the Problem**
   The issue is an N+1 query in `src/main/frontend/components/block.cljs:3975` during the `group-by-page` rendering process. Calling `(db/entity (:db/id page))` inside a `for` loop that iterates over grouped blocks causes individual database entity queries for each page.

2. **Establish Baseline**
   As I do not have a robust benchmarking suite or `bb` easily accessible in this environment to run the exact code segment, I will rely on logic and testing to confirm the N+1 avoidance. The performance improvement fundamentally replaces `O(N)` individual database fetches with `O(1)` batched fetch (or chunked fetch internally in the DB abstraction).

3. **Optimization Steps**
   - Locate the `->hiccup` function in `src/main/frontend/components/block.cljs`.
   - In the `group-by-page` blocks (lines ~3919 and ~3967), before iterating over `blocks`, extract `page-ids` and pre-fetch the pages using `db/pull-many`.
   - Build a `pages-map` mapping `:db/id` to the pulled entity.
   - Inside the loop, replace `(db/entity (:db/id page))` with `(get pages-map (:db/id page))`.

   *Example structure:*
   ```clojure
   (let [blocks (sort-by (comp :block/journal-day first) > blocks)
         page-ids (map (comp :db/id first) blocks)
         pages-map (into {} (map (juxt :db/id identity) (db/pull-many '[*] page-ids)))]
     (for [[page blocks] blocks]
       (let [page (get pages-map (:db/id page))
             alias? (:block/alias? page)]
         ;; Rest of code...
   ```

4. **Ensure Verification and pre-commit checks**
   - Run linter/tests as requested by `pre_commit_instructions` or check documentation to ensure `cljs` compilation works and tests pass.

5. **Commit the changes**
   - Push to a branch and finalize via submit tool.
